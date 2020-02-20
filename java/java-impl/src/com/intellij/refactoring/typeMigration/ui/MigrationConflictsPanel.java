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
package com.intellij.refactoring.typeMigration.ui;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.ui.UsagesPanel;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author anna
 */
public class MigrationConflictsPanel extends UsagesPanel{
  public MigrationConflictsPanel(Project project) {
    super(project);
  }

  @Override
  public String getInitialPositionText() {
    return JavaRefactoringBundle.message("type.migration.no.conflicts.found");
  }

  @Override
  public String getCodeUsagesString() {
    return JavaRefactoringBundle.message("type.migration.conflicts.found");
  }

  @Override
  public void showUsages(final PsiElement @NotNull [] primaryElements, final UsageInfo @NotNull [] usageInfos) {
    super.showUsages(primaryElements, usageInfos);
  }
}