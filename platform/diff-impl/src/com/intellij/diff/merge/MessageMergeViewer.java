// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge;

import com.intellij.diff.util.DiffUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

@ApiStatus.Internal
class MessageMergeViewer implements MergeTool.MergeViewer {
  private final @NotNull MergeContext myMergeContext;

  private final @NotNull JPanel myPanel;

  MessageMergeViewer(@NotNull MergeContext context, @NotNull @Nls String message) {
    myMergeContext = context;

    myPanel = new JPanel(new BorderLayout());
    myPanel.add(DiffUtil.createMessagePanel(message), BorderLayout.CENTER);
  }

  @Override
  public @NotNull JComponent getComponent() {
    return myPanel;
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return null;
  }

  @Override
  public @NotNull MergeTool.ToolbarComponents init() {
    return new MergeTool.ToolbarComponents();
  }

  @Override
  public @Nullable Action getResolveAction(final @NotNull MergeResult result) {
    if (result != MergeResult.CANCEL) return null;

    String caption = MergeUtil.getResolveActionTitle(result, null, myMergeContext);
    return new AbstractAction(caption) {
      @Override
      public void actionPerformed(ActionEvent e) {
        myMergeContext.finishMerge(result);
      }
    };
  }

  @Override
  public void dispose() {
  }
}
