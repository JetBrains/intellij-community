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
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.lang.ElementsHandler;

public abstract class BaseExtractModuleAction extends BaseRefactoringAction {
  public boolean isAvailableInEditorOnly() {
    return false;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    if (elements.length > 0) {
      final Language language = elements[0].getLanguage();
      final RefactoringActionHandler handler = getRefactoringSupport(language).getExtractModuleHandler();
      return isEnabledOnLanguage(language) && handler instanceof ElementsHandler && ((ElementsHandler)handler).isEnabledOnElements(elements);
    }
    return false;
  }

  protected RefactoringSupportProvider getRefactoringSupport(Language language) {
    return LanguageRefactoringSupport.INSTANCE.forLanguage(language);
  }


  public RefactoringActionHandler getHandler(DataContext dataContext) {
    PsiFile file = LangDataKeys.PSI_FILE.getData(dataContext);
    if (file == null) return null;
    final RefactoringSupportProvider supportProvider = getRefactoringSupport(file.getViewProvider().getBaseLanguage());
    return supportProvider != null ? supportProvider.getExtractModuleHandler() : null;
  }

  protected boolean isAvailableForLanguage(final Language language) {
    return isEnabledOnLanguage(language) && getRefactoringSupport(language).getExtractModuleHandler() != null;
  }

  protected abstract boolean isEnabledOnLanguage(final Language language);
}