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
 * created at Oct 8, 2001
 * @author Jeka
 */
package com.intellij.refactoring.ui;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.ui.Messages;
import com.intellij.refactoring.RefactoringBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class YesNoPreviewUsagesDialog extends DialogWrapper {
  private JCheckBox myCbPreviewResults;
  private final boolean myToPreviewUsages;
  private final String myMessage;
  private final String myHelpID;

  public YesNoPreviewUsagesDialog(String title, String message, boolean previewUsages,
                                  String helpID, Project project) {
    super(project, false);
    myHelpID = helpID;
    setTitle(title);
    myMessage = message;
    myToPreviewUsages = previewUsages;
    setOKButtonText(RefactoringBundle.message("yes.button"));
    setCancelButtonText(RefactoringBundle.message("no.button"));
    setButtonsAlignment(SwingUtilities.CENTER);
    init();
  }

  protected JComponent createNorthPanel() {
    JLabel label = new JLabel(myMessage);
    label.setUI(new MultiLineLabelUI());
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(label, BorderLayout.CENTER);
    Icon icon = Messages.getQuestionIcon();
    if (icon != null) {
      label.setIcon(icon);
      label.setIconTextGap(7);
    }
    return panel;
  }

  protected JComponent createCenterPanel() {
    return null;
  }

  public boolean isPreviewUsages() {
    return myCbPreviewResults.isSelected();
  }

  protected JComponent createSouthPanel() {
    myCbPreviewResults = new JCheckBox();
    myCbPreviewResults.setSelected(myToPreviewUsages);
    myCbPreviewResults.setText(RefactoringBundle.message("preview.usages.to.be.changed"));
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(super.createSouthPanel(), BorderLayout.CENTER);
    panel.add(myCbPreviewResults, BorderLayout.WEST);
    return panel;
  }

  @NotNull
  protected Action[] createActions() {
    if(myHelpID != null){
      return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
    }
    else {
      return new Action[]{getOKAction(), getCancelAction()};
    }
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(myHelpID);
  }
}
