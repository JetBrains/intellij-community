/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class CustomizeDesktopEntryStep extends AbstractCustomizeWizardStep {
  private final JCheckBox myCreateEntryCheckBox = new JCheckBox(ActionsBundle.message("action.CreateDesktopEntry.description"));
  private final JCheckBox myGlobalEntryCheckBox = new JCheckBox("For all users");

  public CustomizeDesktopEntryStep(@Nullable String iconPath) {
    setLayout(new GridBagLayout());

    GridBag gbc =
      new GridBag().setDefaultAnchor(GridBagConstraints.WEST).setDefaultFill(GridBagConstraints.HORIZONTAL).setDefaultWeightX(1);

    add(myCreateEntryCheckBox, gbc.nextLine());

    gbc.nextLine().insets.left = UIUtil.PANEL_REGULAR_INSETS.left;
    add(myGlobalEntryCheckBox, gbc);

    if (iconPath != null) {
      gbc.nextLine().insets.top = UIUtil.LARGE_VGAP;
      add(new JLabel(IconLoader.getIcon(iconPath)), gbc.anchor(GridBagConstraints.CENTER));
    }

    add(Box.createVerticalGlue(), gbc.nextLine().weighty(1));

    myCreateEntryCheckBox.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        myGlobalEntryCheckBox.setEnabled(myCreateEntryCheckBox.isSelected());
        myGlobalEntryCheckBox.setSelected(myCreateEntryCheckBox.isSelected() && !PathManager.getHomePath().startsWith("/home"));
      }
    });

    myCreateEntryCheckBox.setSelected(true);
  }

  public static boolean isAvailable() {
    return CreateDesktopEntryAction.isAvailable();
  }

  @Override
  public boolean beforeOkAction() {
    if (myCreateEntryCheckBox.isSelected()) {
      try {
        CreateDesktopEntryAction.createDesktopEntry(null, new EmptyProgressIndicator(), myGlobalEntryCheckBox.isSelected());
      }
      catch (Throwable e) {
        // ignored
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