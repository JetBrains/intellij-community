// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.customize;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.CreateDesktopEntryAction;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.application.ApplicationBundle;
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
  private final JCheckBox myGlobalEntryCheckBox = new JCheckBox(ApplicationBundle.message("desktop.entry.system.wide"));

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
    myCreateEntryCheckBox.setSelected(!PluginManagerCore.isRunningFromSources());

    myGlobalEntryCheckBox.setSelected(false);
    myGlobalEntryCheckBox.setEnabled(myCreateEntryCheckBox.isSelected());
  }

  @Override
  public boolean beforeOkAction() {
    if (myCreateEntryCheckBox.isSelected()) {
      CustomizeIDEWizardInteractions.INSTANCE.record(CustomizeIDEWizardInteractionType.DesktopEntryCreated);

      try {
        CreateDesktopEntryAction.createDesktopEntry(myGlobalEntryCheckBox.isSelected());
      }
      catch (Exception e) {
        Messages.showErrorDialog(ExceptionUtil.getNonEmptyMessage(e, "Internal error"),
                                 IdeBundle.message("dialog.title.desktop.entry.creation.failed"));
        return false;
      }
    }

    return true;
  }

  @Override
  protected String getTitle() {
    return IdeBundle.message("step.title.desktop.entry");
  }

  @Override
  protected String getHTMLHeader() {
    return IdeBundle.message("label.create.desktop.entry");
  }

  @Override
  protected String getHTMLFooter() {
    return IdeBundle.message("label.text.desktop.entry.can.be.created.later.in.tools.create.desktop.entry");
  }
}