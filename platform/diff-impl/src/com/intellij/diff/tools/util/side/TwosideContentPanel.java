// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util.side;

import com.intellij.diff.tools.holders.EditorHolder;
import com.intellij.diff.tools.util.DiffSplitter;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.diff.tools.util.breadcrumbs.DiffBreadcrumbsPanel;
import com.intellij.diff.util.Side;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class TwosideContentPanel extends JPanel {
  private final @NotNull DiffSplitter mySplitter;
  private final @NotNull List<DiffContentPanel> myPanels;

  public TwosideContentPanel(@NotNull List<? extends JComponent> contents) {
    super(new BorderLayout());
    assert contents.size() == 2;

    myPanels = ContainerUtil.map(contents, it -> new DiffContentPanel(it));
    DiffContentLayoutPanel.syncTitleHeights(myPanels);

    mySplitter = new DiffSplitter();
    mySplitter.setFirstComponent(Side.LEFT.select(myPanels));
    mySplitter.setSecondComponent(Side.RIGHT.select(myPanels));
    mySplitter.setHonorComponentsMinimumSize(false);
    add(mySplitter, BorderLayout.CENTER);
  }

  public void setTitles(@NotNull List<? extends @Nullable JComponent> titleComponents) {
    for (Side side : Side.values()) {
      DiffContentPanel panel = side.select(myPanels);
      JComponent title = side.select(titleComponents);
      panel.setTitle(title);
    }
  }

  @ApiStatus.Internal
  public void setBreadcrumbs(@NotNull Side side, @Nullable DiffBreadcrumbsPanel breadcrumbs, @NotNull TextDiffSettings settings) {
    if (breadcrumbs != null) {
      DiffContentPanel panel = side.select(myPanels);
      panel.setBreadcrumbs(breadcrumbs);
      panel.updateBreadcrumbsPlacement(settings.getBreadcrumbsPlacement());
      settings.addListener(new TextDiffSettings.Listener.Adapter() {
        @Override
        public void breadcrumbsPlacementChanged() {
          panel.updateBreadcrumbsPlacement(settings.getBreadcrumbsPlacement());
          repaintDivider();
        }
      }, breadcrumbs);
    }
  }

  public void setBottomAction(@Nullable AnAction value) {
    mySplitter.setBottomAction(value);
  }

  public void setTopAction(@Nullable AnAction value) {
    mySplitter.setTopAction(value);
  }

  @RequiresEdt
  public void setPainter(@Nullable DiffSplitter.Painter painter) {
    mySplitter.setPainter(painter);
  }

  public void repaintDivider() {
    mySplitter.repaintDivider();
  }

  public @NotNull DiffSplitter getSplitter() {
    return mySplitter;
  }

  public static @NotNull TwosideContentPanel createFromHolders(@NotNull List<? extends EditorHolder> holders) {
    TwosideContentPanel panel = new TwosideContentPanel(ContainerUtil.map(holders, holder -> holder.getComponent()));

    EditorHolder holder = Side.RIGHT.select(holders);
    panel.mySplitter.redispatchWheelEventsTo(holder);

    return panel;
  }
}
