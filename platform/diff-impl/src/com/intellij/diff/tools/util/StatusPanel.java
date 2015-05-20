/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.tools.util;

import com.intellij.openapi.diff.DiffBundle;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.AnimatedIcon;
import com.intellij.util.ui.AsyncProcessIcon;

import javax.swing.*;
import java.awt.*;

public abstract class StatusPanel extends JPanel {
  private final JLabel myTextLabel;
  private final AnimatedIcon myBusySpinner;

  public StatusPanel() {
    super(new BorderLayout());
    myTextLabel = new JLabel("");
    myTextLabel.setVisible(false);
    myBusySpinner = new AsyncProcessIcon("StatusPanelSpinner");
    myBusySpinner.setVisible(false);

    add(myTextLabel, BorderLayout.CENTER);
    add(myBusySpinner, BorderLayout.WEST);
    setBorder(IdeBorderFactory.createEmptyBorder(0, 4, 0, 4));
  }

  public void update() {
    int count = getChangesCount();
    myTextLabel.setVisible(count != -1);
    myTextLabel.setText(DiffBundle.message("diff.count.differences.status.text", count));
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

  protected abstract int getChangesCount();
}
