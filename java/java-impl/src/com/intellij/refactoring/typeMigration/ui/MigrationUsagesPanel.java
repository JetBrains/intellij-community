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

import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.ui.UsagesPanel;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;
import com.intellij.usageView.UsageInfo;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author anna
 */
public class MigrationUsagesPanel extends UsagesPanel {
  public MigrationUsagesPanel(Project project) {
    super(project);
  }

  public String getInitialPositionText() {
    return "Select root to find reasons to migrate";
  }

  public String getCodeUsagesString() {
    return "Found reasons to migrate";
  }

  public void showRootUsages(UsageInfo root, UsageInfo migration, final TypeMigrationLabeler labeler) {
    final PsiElement rootElement = root.getElement();
    if (rootElement == null) return;
    final Set<PsiElement> usages = labeler.getTypeUsages((TypeMigrationUsageInfo)migration, ((TypeMigrationUsageInfo)root));
    if (usages != null) {
      final List<UsageInfo> infos = new ArrayList<>(usages.size());
      for (PsiElement usage : usages) {
        if (usage != null && usage.isValid()) {
          infos.add(new UsageInfo(usage));
        }
      }
      showUsages(new PsiElement[]{rootElement}, infos.toArray(new UsageInfo[infos.size()]));
    }
    else {
      showUsages(new PsiElement[]{rootElement}, new UsageInfo[] {migration});
    }
  }

  @Override
  public Dimension getMaximumSize() {
    return new Dimension(-1, 300);
  }
}