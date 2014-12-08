package com.intellij.openapi.util.diff.tools.util.twoside;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.util.diff.api.FrameDiffTool.DiffContext;
import com.intellij.openapi.util.diff.tools.util.EditorsDiffPanelBase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class TwosideTextDiffPanel extends EditorsDiffPanelBase {
  @NotNull private final TwosideTextDiffViewer myViewer;

  public TwosideTextDiffPanel(@NotNull TwosideTextDiffViewer viewer,
                              @NotNull TwosideTextContentPanel content,
                              @NotNull DataProvider dataProvider,
                              @NotNull DiffContext context) {
    super(content, dataProvider, context);
    myViewer = viewer;
  }

  @NotNull
  @Override
  protected JComponent getCurrentEditor() {
    return myViewer.getCurrentEditor().getContentComponent();
  }
}
