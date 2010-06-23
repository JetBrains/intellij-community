/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeEditor;

import com.intellij.openapi.fileEditor.impl.EditorFileSwapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.Nullable;

public class JavaEditorFileSwapper implements EditorFileSwapper {
  public VirtualFile getFileToSwapTo(Project project, VirtualFile original) {
    return findSourceFile(project, original);
  }

  @Nullable
  public static VirtualFile findSourceFile(Project project, VirtualFile eachFile) {
    PsiFile psiFile = PsiManager.getInstance(project).findFile(eachFile);
    if (!(psiFile instanceof ClsFileImpl)) return null;

    String fqn = getFQN(psiFile);
    if (fqn == null) return null;

    PsiClass clsClass = JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project));
    if (!(clsClass instanceof ClsClassImpl)) return null;

    PsiClass sourceClass = ((ClsClassImpl)clsClass).getSourceMirrorClass();
    if (sourceClass == null || sourceClass == clsClass) return null;

    VirtualFile result = sourceClass.getContainingFile().getVirtualFile();
    assert result != null;
    return result;
  }

  @Nullable
  public static String getFQN(PsiFile psiFile) {
    if (!(psiFile instanceof PsiJavaFile)) return null;
    PsiClass[] classes = ((PsiJavaFile)psiFile).getClasses();
    if (classes.length == 0) return null;
    final String fqn = classes[0].getQualifiedName();
    if (fqn == null) return null;
    return fqn;
  }
}
