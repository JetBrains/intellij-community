// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.migration;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.HelpID;
import com.intellij.ui.components.ActionLink;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.psi.search.GlobalSearchScope.projectScope;

/**
 * This dialog makes it possible to run, edit, or duplicate a given {@link MigrationMap}.
 */
public class MigrationDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(MigrationDialog.class);

  private MigrationDialogUi myUi;
  private final Project myProject;
  private MigrationMap myMigrationMap;
  private final MigrationMapSet myMigrationMapSet;

  public MigrationDialog(Project project, MigrationMap migrationMap, MigrationMapSet migrationMapSet) {
    super(project, true);
    myProject = project;
    myMigrationMap = migrationMap;
    myMigrationMapSet = migrationMapSet;
    setTitle(JavaRefactoringBundle.message("migration.dialog.title"));
    setOKButtonText(JavaRefactoringBundle.message("migration.dialog.ok.button.text"));
    setResizable(false);
    init();
  }

  @Override
  protected String getHelpId() {
    return HelpID.MIGRATION;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myUi.preferredFocusedComponent();
  }

  @Override
  protected JComponent createCenterPanel() {
    myUi = new MigrationDialogUi(myMigrationMap);

    myUi.getEditLink().addActionListener(event -> editMap(myMigrationMap));
    ActionLink removeLink = myUi.getRemoveLink();
    if (removeLink != null) removeLink.addActionListener(event -> removeMap());

    myUi.modulesCombo.setModules(getModuleOptions());
    myUi.modulesCombo.allowEmptySelection(JavaRefactoringBundle.message("migration.dialog.scope.whole.project"));

    return myUi.getPanel();
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

  private void editMap(MigrationMap map) {
    if (map == null) {
      return;
    }

    final boolean predefined = MigrationMapSet.isPredefined(map.getFileName());

    MigrationMap mapToEdit = map;
    String editedMapName = "";
    if (predefined) {
      mapToEdit = map.cloneMap();
      mapToEdit.setName(JavaRefactoringBundle.message("migration.edit.duplicated.migration.name", mapToEdit.getName()));
    } else {
      editedMapName = mapToEdit.getName();
    }

    EditMigrationDialog dialog = new EditMigrationDialog(myProject, mapToEdit, myMigrationMapSet, editedMapName);
    if (!dialog.showAndGet()) {
      return;
    }

    MigrationManager.updateMapFromDialog(mapToEdit, dialog);

    if (predefined) {
      // Save new map and close the dialog
      try {
        myMigrationMapSet.addMap(mapToEdit);
        myMigrationMapSet.saveMaps();
        doCancelAction();
      }
      catch (IOException e) {
        LOG.error("Couldn't save migration maps.", e);
      }
    } else {
      // Update the dialog with edited map
      myMigrationMap = mapToEdit;
      myUi.update(myMigrationMap);
    }
  }

  private void removeMap() {
    if (MessageDialogBuilder
      .yesNo(JavaRefactoringBundle.message("migration.dialog.alert.name"),
             JavaRefactoringBundle.message("migration.dialog.alert.text", myMigrationMap.getName()))
      .yesText(JavaRefactoringBundle.message("migration.dialog.alert.delete"))
      .ask(myUi.getPanel())) {
      myMigrationMapSet.removeMap(myMigrationMap);
      try {
        myMigrationMapSet.saveMaps();
        doCancelAction();
      }
      catch (IOException e) {
        LOG.error("Couldn't save migration maps.", e);
      }
    }
  }

  public @NotNull GlobalSearchScope getMigrationScope() {
    final Module selectedModule = myUi.getModulesCombo().getSelectedModule();

    if (selectedModule == null) {
      return projectScope(myProject);
    } else {
      return selectedModule.getModuleScope();
    }
  }
}