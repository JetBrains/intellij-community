// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.merge;

import com.intellij.diff.util.DiffUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

class MessageMergeViewer implements MergeTool.MergeViewer {
  @NotNull private final MergeContext myMergeContext;

  @NotNull private final JPanel myPanel;

  MessageMergeViewer(@NotNull MergeContext context, @NotNull @Nls String message) {
    myMergeContext = context;

    myPanel = new JPanel(new BorderLayout());
    myPanel.add(DiffUtil.createMessagePanel(message), BorderLayout.CENTER);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @NotNull
  @Override
  public MergeTool.ToolbarComponents init() {
    return new MergeTool.ToolbarComponents();
  }

  @Nullable
  @Override
  public Action getResolveAction(@NotNull final MergeResult result) {
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
