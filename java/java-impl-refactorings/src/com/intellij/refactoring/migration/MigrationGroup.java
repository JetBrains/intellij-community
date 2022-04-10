// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.migration;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.refactoring.RefactoringManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class MigrationGroup extends ActionGroup {
  @Override
  public AnAction @NotNull [] getChildren(AnActionEvent e) {
    if (e == null) return AnAction.EMPTY_ARRAY;
    final Project project = e.getProject();
    if (project == null) return AnAction.EMPTY_ARRAY;

    final ArrayList<AnAction> availableMigrations = new ArrayList<>();
    final MigrationManager manager = RefactoringManager.getInstance(project).getMigrateManager();
    for (MigrationMap map: manager.getMigrationsMap().getMaps()) {
      availableMigrations.add(new AnAction(map.getName()) {
        @Override
        public void actionPerformed(AnActionEvent e) {
           manager.showMigrationDialog(map);
        }
      });
    }

    return availableMigrations.toArray(AnAction[]::new);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    Project project = event.getProject();
    presentation.setEnabledAndVisible(project != null && !ActionPlaces.isPopupPlace(event.getPlace()));
  }
}
