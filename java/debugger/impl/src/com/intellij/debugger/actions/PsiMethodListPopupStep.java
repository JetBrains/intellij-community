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
class PsiMethodListPopupStep implements ListPopupStep<JvmSmartStepIntoHandler.StepTarget> {
  private final List<JvmSmartStepIntoHandler.StepTarget> myTargets;
  private final OnChooseRunnable myStepRunnable;


  public interface OnChooseRunnable {
    void execute(JvmSmartStepIntoHandler.StepTarget stepTarget);
  }

  public PsiMethodListPopupStep(final List<JvmSmartStepIntoHandler.StepTarget> targets, final OnChooseRunnable stepRunnable) {
    myTargets = targets;
    myStepRunnable = stepRunnable;
  }

  @NotNull
  public List<JvmSmartStepIntoHandler.StepTarget> getValues() {
    return myTargets;
  }

  public boolean isSelectable(JvmSmartStepIntoHandler.StepTarget value) {
    return true;
  }

  public Icon getIconFor(JvmSmartStepIntoHandler.StepTarget aValue) {
    return aValue.getMethod().getIcon(0);
  }

  @NotNull
    public String getTextFor(JvmSmartStepIntoHandler.StepTarget value) {
    final PsiMethod method = value.getMethod();
    return PsiFormatUtil.formatMethod(
      method,
      PsiSubstitutor.EMPTY,
      PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
      PsiFormatUtil.SHOW_TYPE,
      999
    );
  }

  public ListSeparator getSeparatorAbove(JvmSmartStepIntoHandler.StepTarget value) {
    return null;
  }

  public int getDefaultOptionIndex() {
    return 0;
  }

  public String getTitle() {
    return DebuggerBundle.message("title.smart.step.popup");
  }

  public PopupStep onChosen(JvmSmartStepIntoHandler.StepTarget selectedValue, final boolean finalChoice) {
    if (finalChoice) {
      myStepRunnable.execute(selectedValue);
    }
    return FINAL_CHOICE;
  }

  public Runnable getFinalRunnable() {
    return null;
  }

  public boolean hasSubstep(JvmSmartStepIntoHandler.StepTarget selectedValue) {
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
