/*
 * User: anna
 * Date: 26-Mar-2008
 */
package com.intellij.refactoring.typeMigration.ui;

import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.ui.UsagesPanel;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageInfoToUsageConverter;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
    final UsageInfoToUsageConverter.TargetElementsDescriptor targetElementsDescriptor =
        new UsageInfoToUsageConverter.TargetElementsDescriptor(rootElement);
    final Set<PsiElement> usages = labeler.getTypeUsages((TypeMigrationUsageInfo)migration, ((TypeMigrationUsageInfo)root));
    if (usages != null) {
      final List<UsageInfo> infos = new ArrayList<UsageInfo>(usages.size());
      for (PsiElement usage : usages) {
        if (usage != null && usage.isValid()) {
          infos.add(new UsageInfo(usage));
        }
      }
      showUsages(targetElementsDescriptor, infos.toArray(new UsageInfo[infos.size()]));
    } else {
      showUsages(targetElementsDescriptor, new UsageInfo[] {migration});
    }
  }

  @Override
  public Dimension getMaximumSize() {
    return new Dimension(-1, 300);
  }
}