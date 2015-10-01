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
package com.intellij.diff.merge;

import com.intellij.diff.util.DiffUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class ErrorMergeTool implements MergeTool {
  public static final ErrorMergeTool INSTANCE = new ErrorMergeTool();

  @NotNull
  @Override
  public MergeViewer createComponent(@NotNull MergeContext context, @NotNull MergeRequest request) {
    return new MyViewer(context, request);
  }

  @Override
  public boolean canShow(@NotNull MergeContext context, @NotNull MergeRequest request) {
    return true;
  }

  private static class MyViewer implements MergeViewer {
    @NotNull private final MergeContext myMergeContext;
    @NotNull private final MergeRequest myMergeRequest;

    @NotNull private final JPanel myPanel;

    public MyViewer(@NotNull MergeContext context, @NotNull MergeRequest request) {
      myMergeContext = context;
      myMergeRequest = request;

      myPanel = new JPanel(new BorderLayout());
      myPanel.add(createComponent(), BorderLayout.CENTER);
    }

    @NotNull
    private JComponent createComponent() {
      return DiffUtil.createMessagePanel("Can't show diff");
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
    public ToolbarComponents init() {
      return new ToolbarComponents();
    }

    @Nullable
    @Override
    public Action getResolveAction(@NotNull final MergeResult result) {
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
