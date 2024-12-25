// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge;

import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.diff.DiffBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

@ApiStatus.Internal
public class ErrorMergeTool implements MergeTool {
  public static final ErrorMergeTool INSTANCE = new ErrorMergeTool();

  @Override
  public @NotNull MergeViewer createComponent(@NotNull MergeContext context, @NotNull MergeRequest request) {
    return new MyViewer(context, request);
  }

  @Override
  public boolean canShow(@NotNull MergeContext context, @NotNull MergeRequest request) {
    return true;
  }

  private static class MyViewer implements MergeViewer {
    private final @NotNull MergeContext myMergeContext;
    private final @NotNull MergeRequest myMergeRequest;

    private final @NotNull JPanel myPanel;

    MyViewer(@NotNull MergeContext context, @NotNull MergeRequest request) {
      myMergeContext = context;
      myMergeRequest = request;

      myPanel = new JPanel(new BorderLayout());
      myPanel.add(DiffUtil.createMessagePanel(DiffBundle.message("error.message.cannot.show.merge")), BorderLayout.CENTER);
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
    public @NotNull ToolbarComponents init() {
      return new ToolbarComponents();
    }

    @Override
    public @Nullable Action getResolveAction(final @NotNull MergeResult result) {
      if (result == MergeResult.RESOLVED) return null;

      String caption = MergeUtil.getResolveActionTitle(result, myMergeRequest, myMergeContext);
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
}
