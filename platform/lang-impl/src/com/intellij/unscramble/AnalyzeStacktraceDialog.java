// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.unscramble;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.openapi.application.ex.ClipboardUtil.getTextInClipboard;

@ApiStatus.Internal
public final class AnalyzeStacktraceDialog extends DialogWrapper {
  private final Project project;
  private AnalyzeStacktraceUtil.StacktraceEditorPanel editorPanel;

  AnalyzeStacktraceDialog(@Nullable Project project) {
    super(project, true);
    this.project = project;
    setTitle(IdeBundle.message("unscramble.dialog.title"));
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(new JLabel(IdeBundle.message("label.text.put.stacktrace.here")), BorderLayout.NORTH);
    editorPanel = AnalyzeStacktraceUtil.createEditorPanel(project, myDisposable);
    editorPanel.pasteTextFromClipboard();
    panel.add(editorPanel, BorderLayout.CENTER);
    return panel;
  }

  @Override
  protected void doOKAction() {
    AnalyzeStacktraceUtil.addConsole(project, null, IdeBundle.message("tab.title.stacktrace"), editorPanel.getText());
    super.doOKAction();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    String text = getTextInClipboard();
    if (text == null || text.isEmpty()) {
      return editorPanel.getEditorComponent();
    }

    JRootPane pane = getRootPane();
    return pane != null ? pane.getDefaultButton() : super.getPreferredFocusedComponent();
  }
}
