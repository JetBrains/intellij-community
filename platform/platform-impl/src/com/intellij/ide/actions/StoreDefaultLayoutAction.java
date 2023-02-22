// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.toolWindow.ToolWindowDefaultLayoutManager;

public final class StoreDefaultLayoutAction extends StoreNamedLayoutAction {

  public StoreDefaultLayoutAction() {
    super(() -> ToolWindowDefaultLayoutManager.getInstance().getActiveLayoutName());
  }

}
