// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.hierarchy.method;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.Presentation;

final class OverrideMethodAction extends OverrideImplementMethodAction {
  @Override
  protected void update(Presentation presentation, int toImplement, int toOverride) {
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
