/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.spellchecker;

import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiMethod;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 *
 * @author shkate@jetbrains.com
 */
public class MethodNameTokenizerJava extends NamedElementTokenizer<PsiMethod> {

  @Override
  public void tokenize(@NotNull PsiMethod element, TokenConsumer consumer) {
    final PsiMethod[] methods = (element).findDeepestSuperMethods();
    boolean isInSource = true;
    for (PsiMethod psiMethod : methods) {
      isInSource &= isMethodDeclarationInSource(psiMethod);
    }
    if (isInSource) {
      super.tokenize(element, consumer);
    }
  }

  private static boolean isMethodDeclarationInSource(@NotNull PsiMethod psiMethod) {
    if (psiMethod.getContainingFile() == null) return false;
    final VirtualFile virtualFile = psiMethod.getContainingFile().getVirtualFile();
    if (virtualFile == null) return false;
    return ProjectRootManager.getInstance(psiMethod.getProject()).getFileIndex().isInSource(virtualFile);
  }
}
