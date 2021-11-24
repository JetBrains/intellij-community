// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.migration;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class MigrationManager {
  private final Project myProject;
  private final MigrationMapSet myMigrationMapSet = new MigrationMapSet();
  private final static Logger LOG = Logger.getInstance(MigrationManager.class);

  public MigrationManager(Project project) {
    myProject = project;
  }

  public void showMigrationDialog(MigrationMap map) {
    final MigrationDialog migrationDialog = new MigrationDialog(myProject, map, myMigrationMapSet);
    if (!migrationDialog.showAndGet()) {
      return;
    }

    GlobalSearchScope migrationScope = migrationDialog.getMigrationScope();
    if (migrationScope == null) return;

    new MigrationProcessor(myProject, map, migrationScope).run();
  }

  public void createNewMigration() {
    MigrationMap newMap = new MigrationMap();
    final EditMigrationDialog editMigrationDialog = new EditMigrationDialog(myProject, newMap, myMigrationMapSet, "");
    if (!editMigrationDialog.showAndGet()) {
      return;
    }
    updateMapFromDialog(newMap, editMigrationDialog);

    myMigrationMapSet.addMap(newMap);
    try {
      myMigrationMapSet.saveMaps();
    }
    catch (IOException e) {
      LOG.error("Couldn't save migration maps.", e);
    }
  }

  public static void updateMapFromDialog(MigrationMap map, EditMigrationDialog dialog) {
    map.setName(dialog.getName());
    map.setDescription(dialog.getDescription());
    map.setFileName(FileUtil.sanitizeFileName(map.getName()));
  }

  @Nullable
  public MigrationMap findMigrationMap(@NotNull String name) {
    return myMigrationMapSet.findMigrationMap(name);
  }

  public MigrationMapSet getMigrationsMap() {
    return myMigrationMapSet;
  }
}
