// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.fileTypes.FileTypesBundle;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.components.JBLabel;

import javax.swing.*;

class FileTypePanel {
  JPanel myWholePanel;
  JPanel myRecognizedFileTypesPanel;
  JPanel myPatternsPanel;
  JPanel myHashBangPanel;
  JButton myAssociateButton;
  JPanel myAssociatePanel;
  JPanel myRightPanel;
  JBLabel myAssociateMessageLabel;
  @SuppressWarnings("unused") private JLabel myAssociateContextHelpLabel;

  private void createUIComponents() {
    myAssociateContextHelpLabel = ContextHelpLabel.create(
      FileTypesBundle.message("filetype.associate.context.help.text", ApplicationInfo.getInstance().getFullApplicationName())
    );
  }
}
