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
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.SyntheticElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.rename.RenameHandlerRegistry;

public class RenameElementAction extends BaseRefactoringAction {

  public RenameElementAction() {
    setInjectedContext(true);
  }

  public boolean isAvailableInEditorOnly() {
    return false;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    if (elements.length != 1) return false;

    PsiElement element = elements[0];
    return element instanceof PsiNamedElement && !(element instanceof SyntheticElement);
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    return RenameHandlerRegistry.getInstance().getRenameHandler(dataContext);
  }

  @Override
  protected boolean hasAvailableHandler(DataContext dataContext) {
    return isEnabledOnDataContext(dataContext);
  }

  protected boolean isEnabledOnDataContext(DataContext dataContext) {
    return RenameHandlerRegistry.getInstance().hasAvailableHandler(dataContext);
  }

  protected boolean isAvailableForLanguage(Language language) {
    return true;
  }
}
