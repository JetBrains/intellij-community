package com.intellij.ide.hierarchy.method;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.ide.IdeBundle;

public final class ImplementMethodAction extends OverrideImplementMethodAction {
  protected final void update(final Presentation presentation, final int toImplement, final int toOverride) {
    if (toImplement > 0) {
      presentation.setEnabled(true);
      presentation.setVisible(true);
      presentation.setText(toImplement == 1 ? IdeBundle.message("action.implement.method") : IdeBundle.message("action.implement.methods"));
    }
    else {
      presentation.setEnabled(false);
      presentation.setVisible(false);
    }
  }

}
