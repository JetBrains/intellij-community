// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.refactoring.migration.MigrationManager;

public class RefactoringManager {
  private final MigrationManager myMigrateManager;

  public static RefactoringManager getInstance(Project project) {
    return project.getService(RefactoringManager.class);
  }

  public RefactoringManager(Project project) {
    myMigrateManager = new MigrationManager(project);
  }

  public MigrationManager getMigrateManager() {
    return myMigrateManager;
  }

}
