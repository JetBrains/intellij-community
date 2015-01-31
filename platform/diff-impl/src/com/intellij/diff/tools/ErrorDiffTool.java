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
package com.intellij.diff.tools;

import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.ErrorDiffRequest;
import com.intellij.diff.requests.MessageDiffRequest;
import com.intellij.diff.util.DiffUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ErrorDiffTool implements FrameDiffTool {
  public static final ErrorDiffTool INSTANCE = new ErrorDiffTool();

  @NotNull
  @Override
  public DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return new MyViewer(context, request);
  }

  @Override
  public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return true;
  }

  @NotNull
  @Override
  public String getName() {
    return "Error Viewer";
  }

  private static class MyViewer implements DiffViewer {
    @NotNull private final JPanel myPanel;

    public MyViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
      myPanel = new JPanel(new BorderLayout());

      JPanel centerPanel = DiffUtil.createMessagePanel(getMessage(request));

      myPanel.add(centerPanel, BorderLayout.CENTER);
    }

    @NotNull
    private static String getMessage(@NotNull DiffRequest request) {
      if (request instanceof ErrorDiffRequest) {
        // TODO: explain some of exceptions ?
        return ((ErrorDiffRequest)request).getMessage();
      }
      if (request instanceof MessageDiffRequest) {
        return ((MessageDiffRequest)request).getMessage();
      }

      return "Can't show diff";
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

    @Override
    public void dispose() {
    }
  }
}
