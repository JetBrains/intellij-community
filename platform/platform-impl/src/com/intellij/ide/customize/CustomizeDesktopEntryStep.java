/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.customize;

import com.intellij.ide.actions.CreateDesktopEntryAction;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class CustomizeDesktopEntryStep extends AbstractCustomizeWizardStep {
  public static boolean isAvailable() {
    return System.getProperty("idea.skip.desktop.entry.step") == null && CreateDesktopEntryAction.isAvailable();
  }

  private final JCheckBox myCreateEntryCheckBox = new JCheckBox(ActionsBundle.message("action.CreateDesktopEntry.description"));
  private final JCheckBox myGlobalEntryCheckBox = new JCheckBox("For all users (requires superuser privileges)");

  public CustomizeDesktopEntryStep(String iconPath) {
    setLayout(new BorderLayout());

    JPanel panel = createBigButtonPanel(createSmallBorderLayout(), myCreateEntryCheckBox, EmptyRunnable.INSTANCE);
    panel.setBorder(createSmallEmptyBorder());

    JPanel buttonPanel = new JPanel(new GridBagLayout());
    buttonPanel.setOpaque(false);

    GridBag gbc = new GridBag().setDefaultAnchor(GridBagConstraints.WEST).setDefaultFill(GridBagConstraints.HORIZONTAL).setDefaultWeightX(1);

    myCreateEntryCheckBox.setOpaque(false);
    buttonPanel.add(myCreateEntryCheckBox, gbc.nextLine());

    myGlobalEntryCheckBox.setOpaque(false);
    gbc.nextLine().insets.left = UIUtil.PANEL_REGULAR_INSETS.left;
    buttonPanel.add(myGlobalEntryCheckBox, gbc);

    panel.add(buttonPanel, BorderLayout.NORTH);

    JLabel label = new JLabel(IconLoader.getIcon(iconPath));
    label.setVerticalAlignment(SwingConstants.TOP);
    panel.add(label, BorderLayout.CENTER);

    add(panel, BorderLayout.CENTER);

    myCreateEntryCheckBox.addChangeListener(e -> myGlobalEntryCheckBox.setEnabled(myCreateEntryCheckBox.isSelected()));
    myCreateEntryCheckBox.setSelected(!"true".equals(System.getProperty("idea.debug.mode")));

    myGlobalEntryCheckBox.setSelected(false);
    myGlobalEntryCheckBox.setEnabled(myCreateEntryCheckBox.isSelected());
  }

  @Override
  public boolean beforeOkAction() {
    if (myCreateEntryCheckBox.isSelected()) {
      try {
        CreateDesktopEntryAction.createDesktopEntry(myGlobalEntryCheckBox.isSelected());
      }
      catch (Exception e) {
        Messages.showErrorDialog(ExceptionUtil.getNonEmptyMessage(e, "Internal error"), "Desktop Entry Creation Failed");
        return false;
      }
    }

    return true;
  }

  @Override
  protected String getTitle() {
    return "Desktop Entry";
  }

  @Override
  protected String getHTMLHeader() {
    return "<html><body><h2>Create Desktop Entry</h2>&nbsp;</body></html>";
  }

  @Override
  protected String getHTMLFooter() {
    return "Desktop entry can be created later in Tools | Create Desktop Entry...";
  }
}