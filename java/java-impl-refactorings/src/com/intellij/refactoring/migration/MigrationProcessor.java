/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMigration;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.migration.PsiMigrationManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * @author ven
 */
public class MigrationProcessor extends BaseRefactoringProcessor {
  private final MigrationMap myMigrationMap;
  private PsiMigration myPsiMigration;
  private final GlobalSearchScope mySearchScope;
  private ArrayList<SmartPsiElementPointer<PsiElement>> myRefsToShorten;

  public MigrationProcessor(Project project, MigrationMap migrationMap) {
    this(project, migrationMap, GlobalSearchScope.projectScope(project));
  }

  public MigrationProcessor(Project project, MigrationMap migrationMap, GlobalSearchScope scope) {
    super(project);
    myMigrationMap = migrationMap;
    mySearchScope = scope;
    
  }

  @Override
  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new MigrationUsagesViewDescriptor(myMigrationMap, false);
  }

  private PsiMigration startMigration(Project project) {
    final PsiMigration migration = PsiMigrationManager.getInstance(project).startMigration();
    findOrCreateEntries(project, migration);
    return migration;
  }

  private void findOrCreateEntries(Project project, final PsiMigration migration) {
    for (int i = 0; i < myMigrationMap.getEntryCount(); i++) {
      MigrationMapEntry entry = myMigrationMap.getEntryAt(i);
      if (entry.getType() == MigrationMapEntry.PACKAGE) {
        MigrationUtil.findOrCreatePackage(project, migration, entry.getOldName());
      }
      else {
        MigrationUtil.findOrCreateClass(project, migration, entry.getOldName());
      }
    }
  }

  @Override
  protected void refreshElements(PsiElement @NotNull [] elements) {
    myPsiMigration = startMigration(myProject);
  }

  @Override
  protected void doRun() {
    myPsiMigration = startMigration(myProject);
    super.doRun();
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    ArrayList<UsageInfo> usagesVector = new ArrayList<>();
    try {
      if (myMigrationMap == null) {
        return UsageInfo.EMPTY_ARRAY;
      }
      for (int i = 0; i < myMigrationMap.getEntryCount(); i++) {
        MigrationMapEntry entry = myMigrationMap.getEntryAt(i);
        UsageInfo[] usages;
        if (entry.getType() == MigrationMapEntry.PACKAGE) {
          usages = MigrationUtil.findPackageUsages(myProject, myPsiMigration, entry.getOldName(), mySearchScope);
        }
        else {
          usages = MigrationUtil.findClassUsages(myProject, myPsiMigration, entry.getOldName(), mySearchScope);
        }

        for (UsageInfo usage : usages) {
          usagesVector.add(new MigrationUsageInfo(usage, entry));
        }
      }
    }
    finally {
      //invalidating resolve caches without write action could lead to situations when somebody with read action resolves reference and gets ResolveResult
      //then here, in another read actions, all caches are invalidated but those resolve result is used without additional checks inside that read action - but it's already invalid
      ApplicationManager.getApplication().invokeLater(() -> WriteAction.run(this::finishFindMigration), myProject.getDisposed());
    }
    return usagesVector.toArray(UsageInfo.EMPTY_ARRAY);
  }

  private void finishFindMigration() {
    if (myPsiMigration != null) {
      myPsiMigration.finish();
      myPsiMigration = null;
    }
  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    if (refUsages.get().length == 0) {
      Messages.showInfoMessage(myProject, JavaRefactoringBundle.message("migration.no.usages.found.in.the.project"), getRefactoringName());
      return false;
    }
    setPreviewUsages(true);
    return true;
  }

  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usages) {
    finishFindMigration();
    final PsiMigration psiMigration = PsiMigrationManager.getInstance(myProject).startMigration();
    LocalHistoryAction a = LocalHistory.getInstance().startAction(getCommandName());

    myRefsToShorten = new ArrayList<>();
    try {
      boolean sameShortNames = false;
      for (int i = 0; i < myMigrationMap.getEntryCount(); i++) {
        MigrationMapEntry entry = myMigrationMap.getEntryAt(i);
        String newName = entry.getNewName();
        PsiElement element = entry.getType() == MigrationMapEntry.PACKAGE ? MigrationUtil.findOrCreatePackage(myProject, psiMigration, newName)
                                                                          : MigrationUtil.findOrCreateClass(myProject, psiMigration, newName)[0];
        doMigration(element, newName, usages, myRefsToShorten);
        if (!sameShortNames && Comparing.strEqual(StringUtil.getShortName(entry.getOldName()), StringUtil.getShortName(entry.getNewName()))) {
          sameShortNames = true;
        }
      }

      if (!sameShortNames) {
        myRefsToShorten.clear();
      }
    }
    finally {
      a.finish();
      psiMigration.finish();
    }
  }

  protected void doMigration(
    PsiElement elementToBind,
    String newQName,
    UsageInfo[] usages,
    ArrayList<? super SmartPsiElementPointer<PsiElement>> refsToShorten
  ) {
    MigrationUtil.doMigration(elementToBind, newQName, usages, refsToShorten);
  }


  @Override
  protected void performPsiSpoilingRefactoring() {
    JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(myProject);
    for (SmartPsiElementPointer<PsiElement> pointer : myRefsToShorten) {
      PsiElement element = pointer.getElement();
      if (element != null) {
        styleManager.shortenClassReferences(element);
      }
    }
  }

  @Override
  @NotNull
  protected String getCommandName() {
    return getRefactoringName();
  }

  protected static class MigrationUsageInfo extends UsageInfo {
    public MigrationMapEntry mapEntry;

    MigrationUsageInfo(UsageInfo info, MigrationMapEntry mapEntry) {
      super(info.getElement(), info.getRangeInElement().getStartOffset(), info.getRangeInElement().getEndOffset());
      this.mapEntry = mapEntry;
    }
  }

  private static @NlsContexts.DialogTitle String getRefactoringName() {
    return JavaRefactoringBundle.message("migration.title");
  }
}
