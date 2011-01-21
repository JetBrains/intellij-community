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

/**
 * created at Dec 14, 2001
 * @author Jeka
 */
package com.intellij.execution;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public class TerminateRemoteProcessDialog extends DialogWrapper {
  private static final int ICON_TEXT_GAP = 7;
  private JCheckBox myTerminateCheckBox;
  private final String mySessionName;
  private final boolean myDetachIsDefault;
  private final boolean myAlwaysUseDefault;

  public TerminateRemoteProcessDialog(final Project project,
                                      final String configurationName,
                                      final boolean detachIsDefault,
                                      boolean alwaysUseDefault) {
    super(project, true);
    mySessionName = configurationName;
    myDetachIsDefault = detachIsDefault;
    myAlwaysUseDefault = alwaysUseDefault;
    setTitle(ExecutionBundle.message("process.is.running.dialog.title", mySessionName));
    setOKButtonText(alwaysUseDefault && !detachIsDefault ? ExecutionBundle.message("button.terminate")
                                                         : ExecutionBundle.message("button.disconnect"));
    setButtonsAlignment(SwingUtilities.CENTER);
    this.init();
  }

  protected Action[] createActions(){
    return new Action[]{getOKAction(), getCancelAction()};
  }

  protected JComponent createNorthPanel() {
    final String message = myAlwaysUseDefault && !myDetachIsDefault ?
                           ExecutionBundle.message("terminate.process.confirmation.text", mySessionName) :
                           ExecutionBundle.message("disconnect.process.confirmation.text", mySessionName);
    final JLabel label = new JLabel(message);
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(label, BorderLayout.CENTER);
    final Icon icon = UIUtil.getOptionPanelWarningIcon();
    if (icon != null) {
      label.setIcon(icon);
      label.setIconTextGap(ICON_TEXT_GAP);
    }
    return panel;
  }

  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    if (!myAlwaysUseDefault) {
      myTerminateCheckBox = new JCheckBox(ExecutionBundle.message("terminate.after.disconnect.checkbox"));
      myTerminateCheckBox.setSelected(!myDetachIsDefault);
      final Icon icon = UIUtil.getOptionPanelWarningIcon();
      if (icon != null) {
        final Border border = myTerminateCheckBox.getBorder();
        if (border != null) {
          final Insets insets = border.getBorderInsets(myTerminateCheckBox);
          final Border emptyBorder = BorderFactory.createEmptyBorder(0, icon.getIconWidth()+ICON_TEXT_GAP-insets.left, 0, 0);
          myTerminateCheckBox.setBorder(BorderFactory.createCompoundBorder(emptyBorder, border));
        }
        else {
          myTerminateCheckBox.setBorder(BorderFactory.createEmptyBorder(0, icon.getIconWidth()+ICON_TEXT_GAP, 0, 0));
        }
      }
      panel.add(myTerminateCheckBox, BorderLayout.WEST);
    }
    return panel;
  }

  public boolean forceTermination() {
    return myAlwaysUseDefault ? !myDetachIsDefault : myTerminateCheckBox.isSelected();
  }
}
