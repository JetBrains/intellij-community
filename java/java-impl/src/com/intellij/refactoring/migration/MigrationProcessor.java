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
package com.intellij.refactoring.migration;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMigration;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringHelper;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * @author ven
 */
class MigrationProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.migration.MigrationProcessor");
  private final MigrationMap myMigrationMap;
  private static final String REFACTORING_NAME = RefactoringBundle.message("migration.title");
  private PsiMigration myPsiMigration;

  public MigrationProcessor(Project project, MigrationMap migrationMap) {
    super(project);
    myMigrationMap = migrationMap;
    myPsiMigration = startMigration(PsiManager.getInstance(project));
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new MigrationUsagesViewDescriptor(myMigrationMap, false);
  }

  private PsiMigration startMigration(final PsiManager psiManager) {
    final PsiMigration migration = JavaPsiFacade.getInstance(psiManager.getProject()).startMigration();
    findOrCreateEntries(psiManager, migration);
    return migration;
  }

  private void findOrCreateEntries(final PsiManager psiManager, final PsiMigration migration) {
    for (int i = 0; i < myMigrationMap.getEntryCount(); i++) {
      MigrationMapEntry entry = myMigrationMap.getEntryAt(i);
      if (entry.getType() == MigrationMapEntry.PACKAGE) {
        MigrationUtil.findOrCreatePackage(psiManager, migration, entry.getOldName());
      }
      else {
        MigrationUtil.findOrCreateClass(psiManager, migration, entry.getOldName());
      }
    }
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    ArrayList<UsageInfo> usagesVector = new ArrayList<UsageInfo>();
    PsiManager psiManager = PsiManager.getInstance(myProject);
    try {
      if (myMigrationMap == null) {
        return UsageInfo.EMPTY_ARRAY;
      }
      for (int i = 0; i < myMigrationMap.getEntryCount(); i++) {
        MigrationMapEntry entry = myMigrationMap.getEntryAt(i);
        UsageInfo[] usages;
        if (entry.getType() == MigrationMapEntry.PACKAGE) {
          usages = MigrationUtil.findPackageUsages(psiManager, myPsiMigration, entry.getOldName());
        }
        else {
          usages = MigrationUtil.findClassUsages(psiManager, myPsiMigration, entry.getOldName());
        }

        for (UsageInfo usage : usages) {
          usagesVector.add(new MigrationUsageInfo(usage, entry));
        }
      }
    }
    finally {
      myPsiMigration.finish();
      myPsiMigration = null;
    }
    return usagesVector.toArray(new MigrationUsageInfo[usagesVector.size()]);
  }

  protected void refreshElements(PsiElement[] elements) {
  }

  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    if (refUsages.get().length == 0) {
      Messages.showInfoMessage(myProject, RefactoringBundle.message("migration.no.usages.found.in.the.project"), REFACTORING_NAME);
      return false;
    }
    setPreviewUsages(true);
    return true;
  }

  protected void performRefactoring(UsageInfo[] usages) {
    PsiManager psiManager = PsiManager.getInstance(myProject);
    final PsiMigration psiMigration = JavaPsiFacade.getInstance(psiManager.getProject()).startMigration();
    LocalHistoryAction a = LocalHistory.getInstance().startAction(getCommandName());

    try {
      for (int i = 0; i < myMigrationMap.getEntryCount(); i++) {
        MigrationMapEntry entry = myMigrationMap.getEntryAt(i);
        if (entry.getType() == MigrationMapEntry.PACKAGE) {
          MigrationUtil.doPackageMigration(psiManager, psiMigration, entry.getNewName(), usages);
        }
        if (entry.getType() == MigrationMapEntry.CLASS) {
          MigrationUtil.doClassMigration(psiManager, psiMigration, entry.getNewName(), usages);
        }
      }

      for(RefactoringHelper helper: Extensions.getExtensions(RefactoringHelper.EP_NAME)) {
        Object preparedData = helper.prepareOperation(usages);
        //noinspection unchecked
        helper.performOperation(myProject, preparedData);
      }
    }
    finally {
      a.finish();
      psiMigration.finish();
    }
  }


  protected String getCommandName() {
    return REFACTORING_NAME;
  }

  public static class MigrationUsageInfo extends UsageInfo {
    public MigrationMapEntry mapEntry;

    public MigrationUsageInfo(UsageInfo info, MigrationMapEntry mapEntry) {
      super(info.getElement(), info.startOffset, info.endOffset);
      this.mapEntry = mapEntry;
    }
  }
}