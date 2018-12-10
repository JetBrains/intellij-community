// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DefaultAlwaysVisibleActionGroup extends DefaultActionGroup implements DumbAware, AlwaysVisibleActionGroup {
  public DefaultAlwaysVisibleActionGroup() {
  }

  public DefaultAlwaysVisibleActionGroup(String shortName, boolean popup) {
    super(shortName, popup);
  }

  public DefaultAlwaysVisibleActionGroup(@NotNull List<? extends AnAction> actions) {
    super(actions);
  }
}
