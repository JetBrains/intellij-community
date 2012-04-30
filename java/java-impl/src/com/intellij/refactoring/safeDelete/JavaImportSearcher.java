/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.refactoring.safeDelete;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author Max Medvedev
 */
public class JavaImportSearcher extends ImportSearcher {
  @Override
  public PsiElement findImport(PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile instanceof PsiJavaFile) {
      return PsiTreeUtil.getParentOfType(element, PsiImportList.class, true);
    }
    return null;
  }
}
