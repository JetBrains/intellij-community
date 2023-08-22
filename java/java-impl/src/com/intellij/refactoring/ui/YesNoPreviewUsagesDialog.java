// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.ui;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.refactoring.RefactoringBundle;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class YesNoPreviewUsagesDialog extends DialogWrapper {
  private JCheckBox myCbPreviewResults;
  private final boolean myToPreviewUsages;
  private final @NlsContexts.DialogMessage String myMessage;
  private final String myHelpID;

  public YesNoPreviewUsagesDialog(@NlsContexts.DialogTitle String title, @NlsContexts.DialogMessage String message, boolean previewUsages, String helpID, Project project) {
    super(project, false);
    myHelpID = helpID;
    setTitle(title);
    myMessage = message;
    myToPreviewUsages = previewUsages;
    setOKButtonText(RefactoringBundle.message("yes.button"));
    setCancelButtonText(RefactoringBundle.message("no.button"));
    init();
  }

  @Override
  protected JComponent createNorthPanel() {
    JLabel label = new JLabel(myMessage);
    label.setUI(new MultiLineLabelUI());
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(label, BorderLayout.CENTER);
    Icon icon = Messages.getQuestionIcon();
    label.setIcon(icon);
    label.setIconTextGap(7);
    return panel;
  }

  @Override
  protected JComponent createCenterPanel() {
    return null;
  }

  public boolean isPreviewUsages() {
    return myCbPreviewResults.isSelected();
  }

  @Override
  protected JComponent createSouthPanel() {
    myCbPreviewResults = new JCheckBox();
    myCbPreviewResults.setSelected(myToPreviewUsages);
    myCbPreviewResults.setText(JavaRefactoringBundle.message("preview.usages.to.be.changed"));
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(super.createSouthPanel(), BorderLayout.CENTER);
    panel.add(myCbPreviewResults, BorderLayout.WEST);
    return panel;
  }

  @Nullable
  @Override
  protected String getHelpId() {
    return myHelpID;
  }
}