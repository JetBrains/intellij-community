// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.ide.IdeBundle;
import com.intellij.ui.components.JBPanelWithEmptyText;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public final class DumbUnawareHider extends JBPanelWithEmptyText {
  private final JComponent myDumbUnawareContent;

  public DumbUnawareHider(@NotNull JComponent dumbUnawareContent) {
    super(new BorderLayout());
    this.myDumbUnawareContent = dumbUnawareContent;
    getEmptyText().setText(IdeBundle.message("empty.text.this.view.is.not.available.until.indices.are.built"));
    add(dumbUnawareContent, BorderLayout.CENTER);
  }

  public @NotNull JComponent getContent() {
    return myDumbUnawareContent;
  }

  public void setContentVisible(boolean show) {
    for (int i = 0, count = getComponentCount(); i < count; i++) {
      getComponent(i).setVisible(show);
    }
  }
}
