// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util.side;

import com.intellij.diff.tools.holders.EditorHolder;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.diff.tools.util.breadcrumbs.DiffBreadcrumbsPanel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

@ApiStatus.Internal
public class OnesideContentPanel extends JPanel {
  private final DiffContentPanel myPanel;

  public OnesideContentPanel(@NotNull JComponent content) {
    super(new BorderLayout());

    myPanel = new DiffContentPanel(content);
    add(myPanel, BorderLayout.CENTER);
  }

  public void setTitle(@Nullable JComponent titles) {
    myPanel.setTitle(titles);
  }

  public void setBreadcrumbs(@Nullable DiffBreadcrumbsPanel breadcrumbs, @NotNull TextDiffSettings settings) {
    if (breadcrumbs != null) {
      myPanel.setBreadcrumbs(breadcrumbs);
      myPanel.updateBreadcrumbsPlacement(settings.getBreadcrumbsPlacement());
      settings.addListener(new TextDiffSettings.Listener.Adapter() {
        @Override
        public void breadcrumbsPlacementChanged() {
          myPanel.updateBreadcrumbsPlacement(settings.getBreadcrumbsPlacement());
        }
      }, breadcrumbs);
    }
  }

  public static @NotNull OnesideContentPanel createFromHolder(@NotNull EditorHolder holder) {
    return new OnesideContentPanel(holder.getComponent());
  }
}
