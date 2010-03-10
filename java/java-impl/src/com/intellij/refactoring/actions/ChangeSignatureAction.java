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
package com.intellij.refactoring.actions;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.changeSignature.ChangeSignatureHandler;
import org.jetbrains.annotations.Nullable;

public class ChangeSignatureAction extends BaseRefactoringAction {
  public boolean isAvailableInEditorOnly() {
    return false;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    return elements.length == 1 && (elements[0] instanceof PsiMethod || elements[0] instanceof PsiClass);
  }

  protected boolean isAvailableOnElementInEditor(final PsiElement element, final Editor editor) {
    final Document document = editor.getDocument();
    final PsiFile file = PsiDocumentManager.getInstance(element.getProject()).getPsiFile(document);
    final ChangeSignatureHandler handler = getChangeSignatureHandler(element.getLanguage());
    if (handler == null) return false;
    if (file != null && handler.findTargetMember(file, editor) != null) {
      return true;
    }
    return element instanceof PsiMethod || element instanceof PsiClass;
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    final Language language = LangDataKeys.LANGUAGE.getData(dataContext);
    if (language != null) {
      return getChangeSignatureHandler(language);
    }
    return null;
  }

  @Override
  protected boolean isAvailableForLanguage(Language language) {
    return getChangeSignatureHandler(language) != null;
  }

  @Nullable
  private static ChangeSignatureHandler getChangeSignatureHandler(Language language) {
    return LanguageRefactoringSupport.INSTANCE.forLanguage(language).getChangeSignatureHandler();
  }
}
