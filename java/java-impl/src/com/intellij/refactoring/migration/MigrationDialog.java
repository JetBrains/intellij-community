// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.migration;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.refactoring.HelpID;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.IOException;

public class MigrationDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(MigrationDialog.class);

  private JPanel myPanel;
  private JComboBox<MigrationMap> myMapComboBox;
  private JTextArea myDescriptionTextArea;
  private JButton myEditMapButton;
  private JButton myNewMapButton;
  private JButton myRemoveMapButton;
  private final Project myProject;
  private final MigrationMapSet myMigrationMapSet;
  private JLabel promptLabel;
  private JSeparator mySeparator;
  private JScrollPane myDescriptionScroll;

  public MigrationDialog(Project project, MigrationMapSet migrationMapSet) {
    super(project, true);
    myProject = project;
    myMigrationMapSet = migrationMapSet;
    setTitle(JavaRefactoringBundle.message("migration.dialog.title"));
    setHorizontalStretch(1.2f);
    setOKButtonText(JavaRefactoringBundle.message("migration.dialog.ok.button.text"));
    init();
  }

  @Override
  protected String getHelpId() {
    return HelpID.MIGRATION;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myMapComboBox;
  }

  @Override
  protected JComponent createCenterPanel() {
    class MyTextArea extends JTextArea {
      MyTextArea(@Nls String s, int a, int b) {
        super(s, a, b);
        setFocusable(false);
      }
    }

    initMapCombobox();
    myDescriptionTextArea = new MyTextArea("", 10, 40);
    myDescriptionScroll.getViewport().add(myDescriptionTextArea);
    myDescriptionScroll.setBorder(null);
    myDescriptionScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    myDescriptionScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
    myDescriptionTextArea.setEditable(false);
    myDescriptionTextArea.setFont(promptLabel.getFont());
    myDescriptionTextArea.setBackground(myPanel.getBackground());
    myDescriptionTextArea.setLineWrap(true);
    myDescriptionTextArea.setWrapStyleWord(true);
    updateDescription();

    myMapComboBox.addActionListener(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent event) {
          updateDescription();
        }
      }
    );

    myEditMapButton.addActionListener(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent event) {
          editMap();
        }
      }
    );

    myRemoveMapButton.addActionListener(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent event) {
          removeMap();
        }
      }
    );

    myNewMapButton.addActionListener(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent event) {
          addNewMap();
        }
      }
    );

    myMapComboBox.registerKeyboardAction(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          if (myMapComboBox.isPopupVisible()) {
            myMapComboBox.setPopupVisible(false);
          }
          else{
            clickDefaultButton();
          }
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
      JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
    );

    return myPanel;
  }

  private void updateDescription() {
    if (myDescriptionTextArea == null) {
      return;
    }
    MigrationMap map = getMigrationMap();
    if (map == null) {
      myDescriptionTextArea.setText("");
      return;
    }
    myDescriptionTextArea.setText(map.getDescription());
  }

  private void editMap() {
    MigrationMap oldMap = getMigrationMap();
    if (oldMap == null) {
      return;
    }
    MigrationMap newMap = oldMap.cloneMap();
    if (editMap(newMap)) {
      myMigrationMapSet.replaceMap(oldMap, newMap);
      initMapCombobox();
      myMapComboBox.setSelectedItem(newMap);
      try {
        myMigrationMapSet.saveMaps();
      }
      catch (IOException e) {
        LOG.error("Cannot save migration maps", e);
      }
    }
  }

  private boolean editMap(MigrationMap map) {
    if (map == null)
      return false;
    EditMigrationDialog dialog = new EditMigrationDialog(myProject, map);
    if (!dialog.showAndGet()) {
      return false;
    }
    map.setName(dialog.getName());
    map.setDescription(dialog.getDescription());
    return true;
  }

  private void addNewMap() {
    MigrationMap migrationMap = new MigrationMap();
    if (editMap(migrationMap)) {
      myMigrationMapSet.addMap(migrationMap);
      initMapCombobox();
      myMapComboBox.setSelectedItem(migrationMap);
      try {
        myMigrationMapSet.saveMaps();
      }
      catch (IOException e) {
        LOG.error("Cannot save migration maps", e);
      }
    }
  }

  private void removeMap() {
    MigrationMap map = getMigrationMap();
    if (map == null) {
      return;
    }
    myMigrationMapSet.removeMap(map);
    MigrationMap[] maps = myMigrationMapSet.getMaps();
    initMapCombobox();
    if (maps.length > 0) {
      myMapComboBox.setSelectedItem(maps[0]);
    }
    try {
      myMigrationMapSet.saveMaps();
    }
    catch (IOException e) {
      LOG.error("Cannot save migration maps", e);
    }
  }

  public MigrationMap getMigrationMap() {
    return (MigrationMap)myMapComboBox.getSelectedItem();
  }

  private void initMapCombobox() {
    if (myMapComboBox.getItemCount() > 0) {
      myMapComboBox.removeAllItems();
    }
    MigrationMap[] maps = myMigrationMapSet.getMaps();
    for (MigrationMap map : maps) {
      myMapComboBox.addItem(map);
    }
    updateDescription();
  }
}