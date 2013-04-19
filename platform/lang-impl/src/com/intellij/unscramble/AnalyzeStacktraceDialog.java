/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.unscramble;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

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

  @Override
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
    AnalyzeStacktraceUtil.addConsole(myProject, null, "<Stacktrace>",  myEditorPanel.getText());
    super.doOKAction();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myEditorPanel.getEditorComponent();
  }
}
