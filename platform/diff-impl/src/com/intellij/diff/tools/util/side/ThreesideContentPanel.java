/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diff.tools.util.side;

import com.intellij.diff.tools.holders.EditorHolder;
import com.intellij.diff.tools.holders.TextEditorHolder;
import com.intellij.diff.tools.util.DiffSplitter;
import com.intellij.diff.tools.util.ThreeDiffSplitter;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.diff.tools.util.breadcrumbs.DiffBreadcrumbsPanel;
import com.intellij.diff.util.Side;
import com.intellij.diff.util.ThreeSide;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ThreesideContentPanel extends JPanel {
  @NotNull protected final ThreeDiffSplitter mySplitter;
  @NotNull private final List<DiffContentPanel> myPanels;

  public ThreesideContentPanel(@NotNull List<? extends JComponent> contents) {
    super(new BorderLayout());
    assert contents.size() == 3;

    myPanels = ContainerUtil.map(contents, it -> new DiffContentPanel(it));
    DiffContentLayoutPanel.syncTitleHeights(myPanels);

    mySplitter = new ThreeDiffSplitter(myPanels);
    add(mySplitter, BorderLayout.CENTER);
  }

  public void setTitles(@NotNull List<? extends @Nullable JComponent> titleComponents) {
    for (ThreeSide side : ThreeSide.values()) {
      DiffContentPanel panel = side.select(myPanels);
      JComponent title = side.select(titleComponents);
      panel.setTitle(title);
    }
  }

  @ApiStatus.Internal
  public void setBreadcrumbs(@NotNull ThreeSide side, @Nullable DiffBreadcrumbsPanel breadcrumbs, @NotNull TextDiffSettings settings) {
    if (breadcrumbs != null) {
      DiffContentPanel panel = side.select(myPanels);
      panel.setBreadcrumbs(breadcrumbs);
      panel.updateBreadcrumbsPlacement(settings.getBreadcrumbsPlacement());
      settings.addListener(new TextDiffSettings.Listener.Adapter() {
        @Override
        public void breadcrumbsPlacementChanged() {
          panel.updateBreadcrumbsPlacement(settings.getBreadcrumbsPlacement());
          repaintDividers();
        }
      }, breadcrumbs);
    }
  }

  @RequiresEdt
  public void setPainter(@Nullable DiffSplitter.Painter painter, @NotNull Side side) {
    mySplitter.setPainter(painter, side);
  }

  public void repaintDividers() {
    repaintDivider(Side.LEFT);
    repaintDivider(Side.RIGHT);
  }

  public void repaintDivider(@NotNull Side side) {
    mySplitter.repaintDivider(side);
  }

  public static class Holders extends ThreesideContentPanel {
    @Nullable private final EditorEx myBaseEditor;

    public Holders(@NotNull List<? extends EditorHolder> holders) {
      super(ContainerUtil.map(holders, holder -> holder.getComponent()));

      EditorHolder baseHolder = ThreeSide.BASE.select(holders);
      myBaseEditor = baseHolder instanceof TextEditorHolder ? ((TextEditorHolder)baseHolder).getEditor() : null;

      mySplitter.redispatchWheelEventsTo(baseHolder);
    }

    @Override
    public void repaintDivider(@NotNull Side side) {
      if (side == Side.RIGHT && myBaseEditor != null) {
        myBaseEditor.getScrollPane().getVerticalScrollBar().repaint();
      }
      super.repaintDivider(side);
    }
  }
}
