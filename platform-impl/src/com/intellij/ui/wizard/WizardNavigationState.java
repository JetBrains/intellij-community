/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.wizard;

public final class WizardNavigationState {

  public final WizardAction PREVIOUS;
  public final WizardAction NEXT;
  public final WizardAction CANCEL;
  public final WizardAction FINISH;

  public WizardNavigationState(WizardModel model) {
    this.PREVIOUS = new WizardAction.Previous(model);
    this.NEXT = new WizardAction.Next(model);
    this.CANCEL = new WizardAction.Cancel(model);
    this.FINISH = new WizardAction.Finish(model);
  }

  public void setEnabledToAll(boolean enabled) {
    PREVIOUS.setEnabled(enabled);
    NEXT.setEnabled(enabled);
    CANCEL.setEnabled(enabled);
    FINISH.setEnabled(enabled);
  }

}

