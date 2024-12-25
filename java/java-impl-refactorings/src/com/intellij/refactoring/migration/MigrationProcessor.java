// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.psi.impl.migration.PsiMigrationManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.generate.UastCodeGenerationPluginKt;

import java.util.ArrayList;

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
  protected @NotNull UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
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
        MigrationUtil.doMigration(element, newName, usages, myRefsToShorten);
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

  @Override
  protected void performPsiSpoilingRefactoring() {
    for (SmartPsiElementPointer<PsiElement> pointer : myRefsToShorten) {
      PsiElement element = pointer.getElement();
      if (element != null) {
        UElement uElement = UastContextKt.toUElement(element);
        if (uElement instanceof UReferenceExpression) {
          UastCodeGenerationPluginKt.shortenReference((UReferenceExpression)uElement);
        }
      }
    }
  }

  @Override
  protected @NotNull String getCommandName() {
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
