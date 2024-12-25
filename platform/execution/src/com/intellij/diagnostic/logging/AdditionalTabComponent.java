// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.logging;

import com.intellij.openapi.ui.ComponentContainer;
import com.intellij.openapi.ui.ComponentWithActions;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class AdditionalTabComponent extends JPanel implements ComponentContainer, ComponentWithActions {
  protected AdditionalTabComponent(LayoutManager layout) {
    super(layout);
  }

  protected AdditionalTabComponent() {
  }

  public abstract @NotNull @NlsContexts.TabTitle String getTabTitle();

  public @Nullable @NlsContexts.Tooltip String getTooltip() {
    return null;
  }

  @Override
  public @NotNull JComponent getComponent(){
    return this;
  }
}
