// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.actions;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import com.intellij.refactoring.introduceParameterObject.IntroduceParameterObjectClassDescriptor;
import com.intellij.refactoring.introduceParameterObject.IntroduceParameterObjectDelegate;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class IntroduceParameterObjectAction extends BaseRefactoringAction {

  @Override
  protected boolean isAvailableInEditorOnly() {
    return false;
  }

  @Override
  protected boolean isEnabledOnElements(final PsiElement @NotNull [] elements) {
    if (elements.length == 1) {
      final IntroduceParameterObjectDelegate delegate = IntroduceParameterObjectDelegate.findDelegate(elements[0]);
      if (delegate != null && delegate.isEnabledOn(elements[0])) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected RefactoringActionHandler getHandler(@NotNull DataContext context) {
    final PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(context);
    if (element == null) {
      return null;
    }
    final IntroduceParameterObjectDelegate<PsiNamedElement, ParameterInfo, IntroduceParameterObjectClassDescriptor<PsiNamedElement, ParameterInfo>>
      delegate = IntroduceParameterObjectDelegate.findDelegate(element);
    return delegate != null ? delegate.getHandler(element) : null;
  }

  @Override
  protected boolean isAvailableForLanguage(Language language) {
    return IntroduceParameterObjectDelegate.EP_NAME.forLanguage(language) != null;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    ExtractSuperActionBase.removeFirstWordInMainMenu(this, e);
  }
}
