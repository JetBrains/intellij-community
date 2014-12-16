package com.intellij.openapi.util.diff.tools.util.threeside;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.util.diff.api.FrameDiffTool.DiffContext;
import com.intellij.openapi.util.diff.tools.util.EditorsDiffPanelBase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ThreesideTextDiffPanel extends EditorsDiffPanelBase {
  @NotNull private final ThreesideTextDiffViewer myViewer;

  public ThreesideTextDiffPanel(@NotNull ThreesideTextDiffViewer viewer,
                                @NotNull ThreesideTextContentPanel editorPanel,
                                @NotNull DataProvider dataProvider,
                                @NotNull DiffContext context) {
    super(editorPanel, dataProvider, context);
    myViewer = viewer;
  }

  @NotNull
  @Override
  protected JComponent getCurrentEditor() {
    return myViewer.getCurrentEditor().getContentComponent();
  }
}
