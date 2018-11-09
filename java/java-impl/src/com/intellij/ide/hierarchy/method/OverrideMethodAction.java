// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.hierarchy.method;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.Presentation;

public final class OverrideMethodAction extends OverrideImplementMethodAction {
  @Override
  protected final void update(final Presentation presentation, final int toImplement, final int toOverride) {
    if (toOverride > 0) {
      presentation.setEnabled(true);
      presentation.setVisible(true);
      presentation.setText(toOverride == 1 ? IdeBundle.message("action.override.method")
                                           : IdeBundle.message("action.override.methods"));
    }
    else {
      presentation.setEnabled(false);
      presentation.setVisible(false);
    }
  }

}
