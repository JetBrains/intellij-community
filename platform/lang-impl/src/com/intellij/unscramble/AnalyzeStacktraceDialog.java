package com.intellij.unscramble;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.execution.ui.ConsoleView;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class AnalyzeStacktraceDialog extends DialogWrapper {
  private final Project myProject;
  protected AnalyzeStacktraceUtil.StacktraceEditorPanel myEditorPanel;

  protected AnalyzeStacktraceDialog(Project project) {
    super(project, true);
    myProject = project;
    setTitle(IdeBundle.message("unscramble.dialog.title"));
    init();
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(new JLabel("Put a thread dump here:"), BorderLayout.NORTH);
    myEditorPanel = AnalyzeStacktraceUtil.createEditorPanel(myProject, myDisposable);
    myEditorPanel.pasteTextFromClipboard();
    panel.add(myEditorPanel, BorderLayout.CENTER);
    return panel;
  }

  @Override
  protected void doOKAction() {
    final ConsoleView consoleView = AnalyzeStacktraceUtil.addConsole(myProject, null, "<Stacktrace>");
    AnalyzeStacktraceUtil.printStacktrace(consoleView, myEditorPanel.getText());
    super.doOKAction();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myEditorPanel.getEditorComponent();
  }
}
