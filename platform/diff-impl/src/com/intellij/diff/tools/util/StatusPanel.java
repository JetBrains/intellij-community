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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.AnimatedIcon;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
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

    GridBag bag = new GridBag().setDefaultInsets(JBUI.insets(0, 2)).setDefaultFill(GridBagConstraints.BOTH);
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
