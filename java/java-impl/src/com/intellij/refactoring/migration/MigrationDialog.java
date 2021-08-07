// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.migration;

import com.intellij.find.FindBundle;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.observable.properties.GraphProperty;
import com.intellij.openapi.observable.properties.GraphPropertyImpl;
import com.intellij.openapi.observable.properties.PropertyGraph;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.HelpID;
import com.intellij.ui.ComboboxSpeedSearch;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.ui.layout.ButtonSelectorAction;
import com.intellij.ui.layout.ButtonSelectorToolbar;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.psi.search.GlobalSearchScope.projectScope;

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
  private JScrollPane myDescriptionScroll;
  private JButton myCopyButton;
  private JPanel myScopePanel;

  private GlobalSearchScope myMigrationScope;

  public MigrationDialog(Project project, MigrationMapSet migrationMapSet) {
    super(project, true);
    myProject = project;
    myMigrationScope = projectScope(myProject);
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
      MyTextArea(@Nls String s, int rows, int columns) {
        super(s, rows, columns);
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

    myMapComboBox.addActionListener(event -> updateDescription());
    myEditMapButton.addActionListener(event -> editMap());
    myCopyButton.addActionListener(e -> copyMap());
    myRemoveMapButton.addActionListener(event -> removeMap());
    myNewMapButton.addActionListener(event -> addNewMap());

    myMapComboBox.registerKeyboardAction(
      e -> {
        if (myMapComboBox.isPopupVisible()) {
          myMapComboBox.setPopupVisible(false);
        }
        else {
          clickDefaultButton();
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
      JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
    );

    myScopePanel.setLayout(new HorizontalLayout(UIUtil.DEFAULT_HGAP / 2));

    PropertyGraph propertyGraph = new PropertyGraph();
    GraphProperty<ScopeOption> scopeProperty = new GraphPropertyImpl<>(propertyGraph, () -> ScopeOption.PROJECT);
    ButtonSelectorToolbar toolbar = new ButtonSelectorToolbar(
      "MigrationScopeSelector",
      new DefaultActionGroup(List.of(
        new ButtonSelectorAction<>(ScopeOption.PROJECT, scopeProperty, FindBundle.messagePointer("find.popup.scope.project")),
        new ButtonSelectorAction<>(ScopeOption.MODULE, scopeProperty, FindBundle.messagePointer("find.popup.scope.module"))
      )),
      true,
      true
    );
    toolbar.setTargetComponent(null);
    myScopePanel.add(toolbar);

    ComboBox<Module> moduleComboBox = new ComboBox<>();
    moduleComboBox.setMinimumAndPreferredWidth(200);
    moduleComboBox.setVisible(false);
    moduleComboBox.setSwingPopup(false);
    moduleComboBox.setRenderer(SimpleListCellRenderer.create("", Module::getName));
    ComboboxSpeedSearch.installSpeedSearch(moduleComboBox, Module::getName);

    List<Module> jvmModules = getModuleOptions();
    moduleComboBox.setModel(new DefaultComboBoxModel<>(jvmModules.toArray(Module.EMPTY_ARRAY)));
    if (!jvmModules.isEmpty()) {
      moduleComboBox.setItem(jvmModules.get(0));
    }
    myScopePanel.add(moduleComboBox);

    scopeProperty.afterChange(option -> {
      moduleComboBox.setVisible(option == ScopeOption.MODULE);
      myScopePanel.revalidate();

      if (option == ScopeOption.PROJECT) {
        myMigrationScope = projectScope(myProject);
      }
      else if (moduleComboBox.getSelectedItem() != null) {
        Module module = (Module)moduleComboBox.getSelectedItem();
        myMigrationScope = module.getModuleContentScope();
      }
      else {
        myMigrationScope = null;
      }

      return null;
    });

    moduleComboBox.addItemListener(e -> {
      if (e.getStateChange() == ItemEvent.SELECTED && moduleComboBox.isVisible()) {
        Module module = (Module)moduleComboBox.getSelectedItem();
        if (module != null) {
          myMigrationScope = module.getModuleScope();
        } else {
          myMigrationScope = null;
        }
      }
    });

    return myPanel;
  }

  private @NotNull List<Module> getModuleOptions() {
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    List<Module> jvmModules = new ArrayList<>();
    for (Module module : moduleManager.getModules()) {
      ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      if (!moduleRootManager.getSourceRoots(JavaModuleSourceRootTypes.SOURCES).isEmpty() ||
          !moduleRootManager.getSourceRoots(JavaModuleSourceRootTypes.RESOURCES).isEmpty()) {
        jvmModules.add(module);
      }
    }
    return jvmModules;
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
    boolean predefined = MigrationMapSet.isPredefined(map.getFileName());
    myEditMapButton.setEnabled(!predefined);
    myRemoveMapButton.setEnabled(!predefined);
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
    if (map == null) {
      return false;
    }
    EditMigrationDialog dialog = new EditMigrationDialog(myProject, map);
    if (!dialog.showAndGet()) {
      return false;
    }
    map.setName(dialog.getName());
    map.setDescription(dialog.getDescription());
    return true;
  }

  private void addNewMap() {
    editNewMap(new MigrationMap());
  }

  private void copyMap() {
    MigrationMap map = getMigrationMap().cloneMap();
    map.setName(KeyMapBundle.message("new.keymap.name", map.getName()));
    map.setFileName(null);
    editNewMap(map);
  }

  private void editNewMap(MigrationMap migrationMap) {
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

  public @Nullable GlobalSearchScope getMigrationScope() {
    return myMigrationScope;
  }

  private enum ScopeOption {
    PROJECT,
    MODULE
  }
}