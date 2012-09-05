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
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;
import com.intellij.openapi.fileEditor.impl.text.TextEditorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

public class JavaEditorFileSwapper extends EditorFileSwapper {

  public Pair<VirtualFile, Integer> getFileToSwapTo(Project project, EditorWithProviderComposite editorWithProviderComposite) {
    VirtualFile file = editorWithProviderComposite.getFile();
    VirtualFile sourceFile = findSourceFile(project, file);
    if (sourceFile == null) return null;

    Integer position = null;

    TextEditorImpl oldEditor = findSinglePsiAwareEditor(editorWithProviderComposite.getEditors());
    if (oldEditor != null) {
      PsiCompiledFile clsFile = (PsiCompiledFile)PsiManager.getInstance(project).findFile(file);
      assert clsFile != null;

      int offset = oldEditor.getEditor().getCaretModel().getOffset();

      PsiElement elementAt = clsFile.findElementAt(offset);
      PsiMember member = PsiTreeUtil.getParentOfType(elementAt, PsiMember.class, false);

      if (member instanceof PsiClass) {
        boolean isFirstMember = true;

        for (PsiElement e = member.getFirstChild(); e != null; e = e.getNextSibling()) {
          if (e instanceof PsiMember) {
            if (offset < e.getTextRange().getEndOffset()) {
              if (!isFirstMember) {
                member = (PsiMember)e;
              }

              break;
            }

            isFirstMember = false;
          }
        }
      }

      if (member != null) {
        PsiElement navigationElement = member.getNavigationElement();
        if (Comparing.equal(navigationElement.getContainingFile().getVirtualFile(), sourceFile)) {
          position = navigationElement.getTextOffset();
        }
      }
    }

    return Pair.create(sourceFile, position);
  }

  @Nullable
  public static VirtualFile findSourceFile(Project project, VirtualFile eachFile) {
    PsiFile psiFile = PsiManager.getInstance(project).findFile(eachFile);
    if (!(psiFile instanceof PsiCompiledFile)) return null;

    String fqn = getFQN(psiFile);
    if (fqn == null) return null;

    PsiClass clsClass = JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project));
    if (!(clsClass instanceof ClsClassImpl)) return null;

    PsiClass sourceClass = ((ClsClassImpl)clsClass).getSourceMirrorClass();
    if (sourceClass == null) return null;

    VirtualFile result = sourceClass.getContainingFile().getVirtualFile();
    assert result != null;
    return result;
  }

  @Nullable
  public static String getFQN(PsiFile psiFile) {
    if (!(psiFile instanceof PsiJavaFile)) return null;
    PsiClass[] classes = ((PsiJavaFile)psiFile).getClasses();
    if (classes.length == 0) return null;
    return classes[0].getQualifiedName();
  }
}
