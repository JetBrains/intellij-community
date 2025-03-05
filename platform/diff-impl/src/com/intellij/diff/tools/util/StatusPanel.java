// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util;

import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NotNull;
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
    myBusySpinner.setToolTipText(DiffBundle.message("diff.progress.spinner.tooltip.text"));

    GridBag bag = new GridBag().setDefaultInsets(JBInsets.create(0, 2)).setDefaultFill(GridBagConstraints.BOTH)
      .setDefaultWeightY(1.0);
    add(myBusySpinner, bag.next());
    add(myTextLabel, bag.next().weightx(1.0));
    setBorder(JBUI.Borders.empty(0, 2));
  }

  public void update() {
    String message = getMessage();
    myTextLabel.setVisible(message != null);
    myTextLabel.setText(message);
    myTextLabel.setIcon(getStatusIcon());
    myTextLabel.setForeground(getStatusForeground());
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

  protected @NlsContexts.Label @Nullable String getMessage() {
    return null;
  }

  protected @Nullable Icon getStatusIcon() { return null; }

  protected @NotNull Color getStatusForeground() {
    return UIUtil.getLabelForeground();
  }
}
