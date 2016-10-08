/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.compiler;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubTree;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class JavaCompilerDirectInheritorSearchAdapter implements CompilerDirectInheritorSearchAdapter<PsiClass> {
  public static final JavaCompilerDirectInheritorSearchAdapter INSTANCE = new JavaCompilerDirectInheritorSearchAdapter();

  @NotNull
  @Override
  public PsiClass[] getCandidatesFromFile(@NotNull Collection<String> classInternalNames,
                                          @NotNull PsiNamedElement superClass,
                                          @NotNull VirtualFile containingFile,
                                          @NotNull Project project) {
    final PsiClass[] result = new PsiClass[classInternalNames.size()];
    int i = 0;
    boolean anonymousClassesAdded = false;
    for (String classInternalName : classInternalNames) {
      String name;

      boolean isAnonymous = isAnonymousClass(classInternalName);
      if (isAnonymous) {
        if (anonymousClassesAdded) {
          continue;
        }
        anonymousClassesAdded = true;
        name = ObjectUtils.notNull(superClass.getName());
      }
      else {
        name = StringUtil.replace(classInternalName, "$", ".");
      }
      for (PsiClass c : findClassByStub(containingFile, name, isAnonymous, project)) {
        result[i++] = c;
      }
    }
    return result;
  }

  private static boolean isAnonymousClass(@NotNull String name) {
    int lastIndex = name.lastIndexOf('$');
    return lastIndex != -1 && lastIndex < name.length() - 1 && Character.isDigit(name.charAt(lastIndex + 1));
  }

  private static Collection<PsiClass> findClassByStub(VirtualFile file, String name, boolean isAnonymous, Project project) {
    final List<PsiClass> result = new SmartList<>();
    PsiFileWithStubSupport psiFile = ObjectUtils.notNull((PsiFileWithStubSupport)PsiManager.getInstance(project).findFile(file));
    StubTree tree = psiFile.getStubTree();
    if (tree != null) {
      for (StubElement<?> element : tree.getPlainListFromAllRoots()) {
        if (element instanceof PsiClassStub) {
          if (isAnonymous) {
            String baseClassRef = ((PsiClassStub)element).getBaseClassReferenceText();
            if (baseClassRef != null && ((PsiClassStub)element).isAnonymous() && name.equals(PsiNameHelper.getShortClassName(baseClassRef))) {
              result.add((PsiClass)element.getPsi());
            }
          } else {
            if (!((PsiClassStub)element).isAnonymous() && name.equals(((PsiClassStub)element).getQualifiedName())) {
              result.add((PsiClass)element.getPsi());
            }
          }
        }
      }
    } else {
      PsiTreeUtil.processElements(psiFile, e -> {
        if (e instanceof PsiAnonymousClass) {
          if (isAnonymous) {
            String baseClassRefText = ((PsiAnonymousClass)e).getBaseClassReference().getText();
            if (name.equals(PsiNameHelper.getShortClassName(baseClassRefText))) {
              result.add((PsiClass)e);
            }
          }
          return true;
        }
        else if (e instanceof PsiClass) {
          if (!isAnonymous) {
            if (name.equals(((PsiClass)e).getQualifiedName())) {
              result.add((PsiClass)e);
            }
          }
        }
        return true;
      });
    }
    return result;
  }
}
