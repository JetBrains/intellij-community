package com.intellij.openapi.util.diff.tools;

import com.intellij.openapi.util.diff.api.DiffTool;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import com.intellij.openapi.util.diff.requests.ErrorDiffRequest;
import com.intellij.openapi.util.diff.requests.NoDiffRequest;
import com.intellij.openapi.util.diff.util.DiffUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ErrorDiffTool implements DiffTool {
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
        return ((ErrorDiffRequest)request).getErrorMessage();
      }
      if (request instanceof NoDiffRequest) {
        return "Nothing to show";
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
