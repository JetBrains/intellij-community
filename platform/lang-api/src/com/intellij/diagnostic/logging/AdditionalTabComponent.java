// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic.logging;

import com.intellij.openapi.ui.ComponentContainer;
import com.intellij.openapi.ui.ComponentWithActions;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public abstract class AdditionalTabComponent extends JPanel implements ComponentContainer, ComponentWithActions {
  protected AdditionalTabComponent(LayoutManager layout) {
    super(layout);
  }

  protected AdditionalTabComponent() {
  }

  @NotNull
  public abstract @NlsContexts.TabTitle String getTabTitle();

  @Nullable
  public @NlsContexts.Tooltip String getTooltip() {
    return null;
  }

  @Override
  @NotNull
  public JComponent getComponent(){
    return this;
  }
}
