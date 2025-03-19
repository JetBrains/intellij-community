// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.excludedFiles;

import com.intellij.ui.components.JBList;
import com.intellij.ui.scale.JBUIScale;

import javax.swing.*;
import java.awt.*;

public final class ExcludedFilesScopeForm {
  private JPanel myTopPanel;
  private JBList<String> myScopesList;

  public ExcludedFilesScopeForm() {
    myTopPanel.setMinimumSize(new Dimension(myTopPanel.getMinimumSize().width, JBUIScale.scale(100)));
  }

  public JPanel getTopPanel() {
    return myTopPanel;
  }


  public JBList<String> getScopesList() {
    return myScopesList;
  }
}
