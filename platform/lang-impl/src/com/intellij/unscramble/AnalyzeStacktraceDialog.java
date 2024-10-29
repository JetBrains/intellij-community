// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.unscramble;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;
import java.awt.*;

import static com.intellij.openapi.application.ex.ClipboardUtil.getTextInClipboard;


@ApiStatus.Internal
public final class AnalyzeStacktraceDialog extends DialogWrapper {
  private final Project myProject;
  private AnalyzeStacktraceUtil.StacktraceEditorPanel myEditorPanel;

  AnalyzeStacktraceDialog(Project project) {
    super(project, true);
    myProject = project;
    setTitle(IdeBundle.message("unscramble.dialog.title"));
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(new JLabel(IdeBundle.message("label.text.put.stacktrace.here")), BorderLayout.NORTH);
    myEditorPanel = AnalyzeStacktraceUtil.createEditorPanel(myProject, myDisposable);
    myEditorPanel.pasteTextFromClipboard();
    panel.add(myEditorPanel, BorderLayout.CENTER);
    return panel;
  }

  @Override
  protected void doOKAction() {
    AnalyzeStacktraceUtil.addConsole(myProject, null, IdeBundle.message("tab.title.stacktrace"), myEditorPanel.getText());
    super.doOKAction();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    String text = getTextInClipboard();
    if (text == null || text.isEmpty()) {
      return myEditorPanel.getEditorComponent();
    }

    JRootPane pane = getRootPane();
    return pane != null ? pane.getDefaultButton() : super.getPreferredFocusedComponent();
  }
}
