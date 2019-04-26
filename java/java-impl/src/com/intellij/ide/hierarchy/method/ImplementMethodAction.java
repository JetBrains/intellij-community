// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.hierarchy.method;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.Presentation;

public final class ImplementMethodAction extends OverrideImplementMethodAction {
  @Override
  protected final void update(final Presentation presentation, final int toImplement, final int toOverride) {
    if (toImplement > 0) {
      presentation.setEnabledAndVisible(true);
      presentation.setText(toImplement == 1 ? IdeBundle.message("action.implement.method") : IdeBundle.message("action.implement.methods"));
    }
    else {
      presentation.setEnabledAndVisible(false);
    }
  }

}
