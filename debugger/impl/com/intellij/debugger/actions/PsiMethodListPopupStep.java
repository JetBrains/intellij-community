/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.openapi.ui.popup.*;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
*         Date: Nov 21, 2006
*/
class PsiMethodListPopupStep implements ListPopupStep {
  private final List<PsiMethod> myMethods;
  private final OnChooseRunnable myStepRunnable;


  public static interface OnChooseRunnable {
    void execute(PsiMethod chosenMethod);
  }

  public PsiMethodListPopupStep(final List<PsiMethod> methods, final OnChooseRunnable stepRunnable) {
    myMethods = methods;
    myStepRunnable = stepRunnable;
  }

  @NotNull
  public List getValues() {
    return myMethods;
  }

  public boolean isSelectable(Object value) {
    return true;
  }

  public Icon getIconFor(Object aValue) {
    if (aValue instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)aValue;
      return method.getIcon(0);
    }
    return null;
  }

  @NotNull
    public String getTextFor(Object value) {
    if (value instanceof PsiMethod) {
      return PsiFormatUtil.formatMethod(
        (PsiMethod)value,
        PsiSubstitutor.EMPTY,
        PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
        PsiFormatUtil.SHOW_TYPE,
        999
      );
    }
    return value.toString();
  }

  public ListSeparator getSeparatorAbove(Object value) {
    return null;
  }

  public int getDefaultOptionIndex() {
    return 0;
  }

  public String getTitle() {
    return DebuggerBundle.message("title.smart.step.popup");
  }

  public PopupStep onChosen(Object selectedValue, final boolean finalChoice) {
    if (finalChoice) {
      myStepRunnable.execute((PsiMethod)selectedValue);
    }
    return FINAL_CHOICE;
  }

  public boolean hasSubstep(Object selectedValue) {
    return false;
  }

  public void canceled() {
  }

  public boolean isMnemonicsNavigationEnabled() {
    return false;
  }

  public MnemonicNavigationFilter getMnemonicNavigationFilter() {
    return null;
  }

  public boolean isSpeedSearchEnabled() {
    return false;
  }

  public SpeedSearchFilter getSpeedSearchFilter() {
    return null;
  }

  public boolean isAutoSelectionEnabled() {
    return false;
  }
}
