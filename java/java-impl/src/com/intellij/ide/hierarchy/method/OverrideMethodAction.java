// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.hierarchy.method;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.Presentation;

public final class OverrideMethodAction extends OverrideImplementMethodAction {
  @Override
  protected final void update(final Presentation presentation, final int toImplement, final int toOverride) {
    if (toOverride > 0) {
      presentation.setEnabledAndVisible(true);
      presentation.setText(toOverride == 1 ? JavaBundle.message("action.override.method")
                                           : JavaBundle.message("action.override.methods"));
    }
    else {
      presentation.setEnabledAndVisible(false);
    }
  }

}
