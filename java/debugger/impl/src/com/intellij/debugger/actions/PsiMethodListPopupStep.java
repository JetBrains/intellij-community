// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.actions;

import com.intellij.codeInsight.unwrap.ScopeHighlighter;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
*/
class PsiMethodListPopupStep implements ListPopupStep<SmartStepTarget> {
  private final List<SmartStepTarget> myTargets;
  private final OnChooseRunnable myStepRunnable;
  private final ScopeHighlighter myScopeHighlighter;

  public interface OnChooseRunnable {
    void execute(SmartStepTarget stepTarget);
  }

  PsiMethodListPopupStep(Editor editor, final List<SmartStepTarget> targets, final OnChooseRunnable stepRunnable) {
    myTargets = targets;
    myScopeHighlighter = new ScopeHighlighter(editor);
    myStepRunnable = stepRunnable;
  }

  @NotNull
  public ScopeHighlighter getScopeHighlighter() {
    return myScopeHighlighter;
  }

  @Override
  @NotNull
  public List<SmartStepTarget> getValues() {
    return myTargets;
  }

  @Override
  public boolean isSelectable(SmartStepTarget value) {
    return true;
  }

  @Override
  public Icon getIconFor(SmartStepTarget avalue) {
    return avalue.getIcon();
  }

  @Override
  @NotNull
  public String getTextFor(SmartStepTarget value) {
    return value.getPresentation();
  }

  @Override
  public ListSeparator getSeparatorAbove(SmartStepTarget value) {
    return null;
  }

  @Override
  public int getDefaultOptionIndex() {
    return 0;
  }

  @Override
  public String getTitle() {
    return DebuggerBundle.message("title.smart.step.popup");
  }

  @Override
  public PopupStep onChosen(SmartStepTarget selectedValue, final boolean finalChoice) {
    if (finalChoice) {
      myScopeHighlighter.dropHighlight();
      myStepRunnable.execute(selectedValue);
    }
    return FINAL_CHOICE;
  }

  @Override
  public Runnable getFinalRunnable() {
    return null;
  }

  @Override
  public boolean hasSubstep(SmartStepTarget selectedValue) {
    return false;
  }

  @Override
  public void canceled() {
    myScopeHighlighter.dropHighlight();
  }

  @Override
  public boolean isMnemonicsNavigationEnabled() {
    return false;
  }

  @Override
  public MnemonicNavigationFilter<SmartStepTarget> getMnemonicNavigationFilter() {
    return null;
  }

  @Override
  public boolean isSpeedSearchEnabled() {
    return true;
  }

  @Override
  public SpeedSearchFilter<SmartStepTarget> getSpeedSearchFilter() {
    return this::getTextFor;
  }

  @Override
  public boolean isAutoSelectionEnabled() {
    return false;
  }
}
