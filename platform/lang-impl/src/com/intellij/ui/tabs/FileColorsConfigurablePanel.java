/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.ui.tabs;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.scopeChooser.EditScopesDialog;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.StripeTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * @author spleaner
 */
public class FileColorsConfigurablePanel extends JPanel implements Disposable {
  private FileColorManagerImpl myManager;
  private final JCheckBox myEnabledCheckBox;
  private final JCheckBox myTabsEnabledCheckBox;
  private final JCheckBox myHighlightNonProjectFilesCheckBox;
  private final FileColorSettingsTable myLocalTable;
  private final FileColorSettingsTable mySharedTable;

  public FileColorsConfigurablePanel(@NotNull final FileColorManagerImpl manager) {
    setLayout(new BorderLayout());

    myManager = manager;

    final JPanel topPanel = new JPanel();
    //topPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
    topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.X_AXIS));

    myEnabledCheckBox = new JCheckBox("Enable File Colors");
    myEnabledCheckBox.setMnemonic('F');
    topPanel.add(myEnabledCheckBox);

    myTabsEnabledCheckBox = new JCheckBox("Use colors in Editor Tabs");
    myTabsEnabledCheckBox.setMnemonic('T');
    topPanel.add(myTabsEnabledCheckBox);

    myHighlightNonProjectFilesCheckBox = new JCheckBox("Highlight Non-Project Files");
    myHighlightNonProjectFilesCheckBox.setMnemonic('N');
    topPanel.add(myHighlightNonProjectFilesCheckBox);
    topPanel.add(Box.createHorizontalGlue());

    add(topPanel, BorderLayout.NORTH);

    final JPanel mainPanel = new JPanel(new GridLayout(2, 1));
    mainPanel.setPreferredSize(new Dimension(300, 500));
    mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    myLocalTable = new FileColorSettingsTable(manager, manager.getLocalConfigurations()) {
      protected void apply(@NotNull List<FileColorConfiguration> configurations) {
        final List<FileColorConfiguration> copied = new ArrayList<FileColorConfiguration>();
        for (final FileColorConfiguration configuration : configurations) {
          try {
            copied.add(configuration.clone());
          }
          catch (CloneNotSupportedException e) {
            assert false : "Should not happen!";
          }
        }
        manager.getModel().setConfigurations(copied, false);
      }
    };

    final JPanel localPanel = new JPanel(new BorderLayout());
    localPanel.setBorder(BorderFactory.createTitledBorder("Local colors:"));
    //localPanel.add(new JLabel("Local colors:"), BorderLayout.NORTH);
    localPanel.add(StripeTable.createScrollPane(myLocalTable), BorderLayout.CENTER);
    localPanel.add(Box.createVerticalStrut(10), BorderLayout.SOUTH);
    localPanel.add(buildButtons(manager, myLocalTable, "Share", new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        share();
      }
    }), BorderLayout.EAST);
    mainPanel.add(localPanel);

    mySharedTable = new FileColorSettingsTable(manager, manager.getSharedConfigurations()) {
      protected void apply(@NotNull List<FileColorConfiguration> configurations) {
        final List<FileColorConfiguration> copied = new ArrayList<FileColorConfiguration>();
        for (final FileColorConfiguration configuration : configurations) {
          try {
            copied.add(configuration.clone());
          }
          catch (CloneNotSupportedException e) {
            assert false : "Should not happen!";
          }
        }
        manager.getModel().setConfigurations(copied, true);
      }
    };

    final JPanel sharedPanel = new JPanel(new BorderLayout());
    sharedPanel.setBorder(BorderFactory.createTitledBorder("Shared colors:"));
    //sharedPanel.add(new JLabel("Shared colors:"), BorderLayout.NORTH);
    sharedPanel.add(StripeTable.createScrollPane(mySharedTable), BorderLayout.CENTER);
    sharedPanel.add(buildButtons(manager, mySharedTable, "Unshare", new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        unshare();
      }
    }), BorderLayout.EAST);
    mainPanel.add(sharedPanel);

    add(mainPanel, BorderLayout.CENTER);

    final JPanel warningPanel = new JPanel(new BorderLayout());
    warningPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    warningPanel.add(new JLabel("Scopes are processed from top to bottom with Local colors first.",
                             MessageType.WARNING.getDefaultIcon(), SwingConstants.LEFT));
    final JButton editScopes = new JButton("Manage Scopes...");
    editScopes.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        EditScopesDialog.editConfigurable(myManager.getProject(), null, true);
      }
    });
    warningPanel.add(editScopes, BorderLayout.EAST);
    add(warningPanel, BorderLayout.SOUTH);
  }

  private static JButton createAddButton(final FileColorSettingsTable table, final FileColorManagerImpl manager) {
    final JButton addButton = new JButton("Add...");
    addButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, addButton.getMaximumSize().height));
    addButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final FileColorConfigurationEditDialog dialog = new FileColorConfigurationEditDialog(manager, null);
        dialog.show();

        if (dialog.getExitCode() == 0) {
          table.addConfiguration(dialog.getConfiguration());
        }
      }
    });

    return addButton;
  }

  private void unshare() {
    final int rowCount = mySharedTable.getSelectedRowCount();
    if (rowCount > 0) {
      final int[] rows = mySharedTable.getSelectedRows();
      for (int i = rows.length - 1; i >= 0; i--) {
        FileColorConfiguration removed = mySharedTable.removeConfiguration(rows[i]);
        if (removed != null) {
          myLocalTable.addConfiguration(removed);
        }
      }
    }
  }

  private void share() {
    final int rowCount = myLocalTable.getSelectedRowCount();
    if (rowCount > 0) {
      final int[] rows = myLocalTable.getSelectedRows();
      for (int i = rows.length - 1; i >= 0; i--) {
        FileColorConfiguration removed = myLocalTable.removeConfiguration(rows[i]);
        if (removed != null) {
          mySharedTable.addConfiguration(removed);
        }
      }
    }
  }

  private static Component buildButtons(final FileColorManagerImpl manager,
                                        final FileColorSettingsTable table,
                                        final String shareButtonText,
                                        final ActionListener shareButtonListener) {
    final JPanel result = new JPanel();
    result.setLayout(new BoxLayout(result, BoxLayout.Y_AXIS));

    result.add(createAddButton(table, manager));

    final JButton removeButton = new JButton("Remove");
    result.add(removeButton);
    removeButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, removeButton.getMaximumSize().height));
    removeButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        table.performRemove();
      }
    });

    final JButton shareButton = new JButton(shareButtonText);
    result.add(shareButton);
    shareButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, shareButton.getMaximumSize().height));
    shareButton.addActionListener(shareButtonListener);

    final JButton upButton = new JButton("Move up");
    result.add(upButton);
    upButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, upButton.getMaximumSize().height));
    upButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        table.moveUp();
      }
    });

    final JButton downButton = new JButton("Move down");
    result.add(downButton);
    downButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, downButton.getMaximumSize().height));
    downButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        table.moveDown();
      }
    });

    return result;
  }

  public void dispose() {
    myManager = null;
  }

  public boolean isModified() {
    boolean modified;

    modified = myEnabledCheckBox.isSelected() != myManager.isEnabled();
    modified |= myTabsEnabledCheckBox.isSelected() != myManager.isEnabledForTabs();
    modified |= myHighlightNonProjectFilesCheckBox.isSelected() != myManager.isHighlightNonProjectFiles();
    modified |= myLocalTable.isModified() || mySharedTable.isModified();

    return modified;
  }

  public void apply() {
    myManager.setEnabled(myEnabledCheckBox.isSelected());
    myManager.setEnabledForTabs(myTabsEnabledCheckBox.isSelected());
    myManager.setHighlightNonProjectFiles(myHighlightNonProjectFilesCheckBox.isSelected());

    myLocalTable.apply();
    mySharedTable.apply();

    UISettings.getInstance().fireUISettingsChanged();
  }

  public void reset() {
    myEnabledCheckBox.setSelected(myManager.isEnabled());
    myTabsEnabledCheckBox.setSelected(myManager.isEnabledForTabs());
    myHighlightNonProjectFilesCheckBox.setSelected(myManager.isHighlightNonProjectFiles());

    if(myLocalTable.isModified()) myLocalTable.reset();
    if(mySharedTable.isModified()) mySharedTable.reset();
  }
}
