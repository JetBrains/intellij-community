// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class StatusPanel extends JPanel {
  private final JLabel myTextLabel;
  private final AnimatedIcon myBusySpinner;

  public StatusPanel() {
    super(new GridBagLayout());
    myTextLabel = new JLabel("");
    myTextLabel.setVisible(false);
    myBusySpinner = new AsyncProcessIcon("StatusPanelSpinner");
    myBusySpinner.setVisible(false);

    GridBag bag = new GridBag().setDefaultInsets(JBInsets.create(0, 2)).setDefaultFill(GridBagConstraints.BOTH);
    add(myBusySpinner, bag.next());
    add(myTextLabel, bag.next());
    setBorder(JBUI.Borders.empty(0, 2));
  }

  public void update() {
    String message = getMessage();
    myTextLabel.setVisible(message != null);
    myTextLabel.setText(StringUtil.notNullize(message));
  }

  public void setBusy(boolean busy) {
    if (busy) {
      myBusySpinner.setVisible(true);
      myBusySpinner.resume();
    }
    else {
      myBusySpinner.setVisible(false);
      myBusySpinner.suspend();
    }
  }

  @Nullable
  protected String getMessage() {
    return null;
  }
}
