// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.UnusedImportProvider;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ExternalSourceProblemGroup;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.codeInspection.unusedImport.MissortedImportsInspection;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.annotation.ProblemGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Function;

class UnusedImportsVisitor extends JavaElementVisitor {
  private final LocalRefUseInfo myRefCountHolder;
  @NotNull private final Project myProject;
  private final PsiFile myFile;
  @NotNull private final Document myDocument;
  private IntentionAction myOptimizeImportsFix; // when not null, there are not-optimized imports in the file
  private int myCurrentEntryIndex = -1;
  private boolean errorFound;

  UnusedImportsVisitor(@NotNull PsiFile file, @NotNull Document document) throws ProcessCanceledException {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    myProject = file.getProject();
    myFile = file;
    myDocument = document;
    myRefCountHolder = LocalRefUseInfo.forFile(file);
  }

  void collectHighlights(@NotNull HighlightInfoHolder holder) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();

    HighlightDisplayKey unusedImportKey = HighlightDisplayKey.find(UnusedImportInspection.SHORT_NAME);
    PsiJavaFile javaFile = ObjectUtils.tryCast(myFile, PsiJavaFile.class);
    PsiImportList importList = javaFile == null ? null : javaFile.getImportList();
    if (unusedImportKey != null && isUnusedImportEnabled(unusedImportKey) && importList != null) {
      PsiImportStatementBase[] imports = importList.getAllImportStatements();
      for (PsiImportStatementBase statement : imports) {
        ProgressManager.checkCanceled();
        processImport(holder, javaFile, statement, unusedImportKey);
      }
    }

    if (errorFound) {
      DaemonCodeAnalyzerEx daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(myProject);
      FileStatusMap fileStatusMap = daemonCodeAnalyzer.getFileStatusMap();
      fileStatusMap.setErrorFoundFlag(myProject, myDocument, true);
    }
    IntentionAction fix = myOptimizeImportsFix;
    if (fix != null) {
      OptimizeImportRestarter.getInstance(myProject).scheduleOnDaemonFinish(myFile, fix);
    }
    HighlightDisplayKey misSortedKey = HighlightDisplayKey.find(MissortedImportsInspection.SHORT_NAME);
    if (misSortedKey != null && isToolEnabled(misSortedKey) && fix != null && importList != null) {
      holder.add(HighlightInfo.newHighlightInfo(JavaHighlightInfoTypes.MISSORTED_IMPORTS)
        .range(importList)
        .registerFix(fix, null, HighlightDisplayKey.getDisplayNameByKey(misSortedKey), null, misSortedKey)
        .create());
    }
  }

  private void addInfo(@NotNull HighlightInfoHolder holder, @NotNull HighlightInfo.Builder builder) {
    HighlightInfo info = builder.create();
    if (info != null && info.getSeverity() == HighlightSeverity.ERROR) {
      errorFound = true;
    }
    holder.add(info);
  }

  private boolean isUnusedImportEnabled(@NotNull HighlightDisplayKey unusedImportKey) {
    if (isToolEnabled(unusedImportKey)) return true;
    for (ImplicitUsageProvider provider : ImplicitUsageProvider.EP_NAME.getExtensionList()) {
      if (provider instanceof UnusedImportProvider uip && uip.isUnusedImportEnabled(myFile)) return true;
    }
    return false;
  }

  private boolean isToolEnabled(@NotNull HighlightDisplayKey displayKey) {
    if (!(myFile instanceof PsiJavaFile)) {
      return false;
    }
    InspectionProfile profile = getCurrentProfile(myFile);
    return profile.isToolEnabled(displayKey, myFile) &&
           HighlightingLevelManager.getInstance(myProject).shouldInspect(myFile) &&
           !HighlightingLevelManager.getInstance(myProject).runEssentialHighlightingOnly(myFile);
  }

  @NotNull
  private static InspectionProfile getCurrentProfile(@NotNull PsiFile file) {
    Function<? super InspectionProfile, ? extends InspectionProfileWrapper> custom = InspectionProfileWrapper.getCustomInspectionProfileWrapper(file);
    InspectionProfileImpl currentProfile = InspectionProjectProfileManager.getInstance(file.getProject()).getCurrentProfile();
    return custom != null ? custom.apply(currentProfile).getInspectionProfile() : currentProfile;
  }


  private void processImport(@NotNull HighlightInfoHolder holder,
                             @NotNull PsiJavaFile javaFile,
                             @NotNull PsiImportStatementBase importStatement,
                             @NotNull HighlightDisplayKey unusedImportKey) {
    // jsp include directive hack
    if (importStatement.isForeignFileImport()) return;

    if (PsiUtilCore.hasErrorElementChild(importStatement)) return;

    boolean isRedundant = isRedundantImport(javaFile, importStatement);

    if (isRedundant) {
      registerRedundantImport(holder, importStatement, unusedImportKey);
      return;
    }

    int entryIndex = JavaCodeStyleManager.getInstance(myProject).findEntryIndex(importStatement);
    if (entryIndex < myCurrentEntryIndex && myOptimizeImportsFix == null) {
      // mis-sorted imports found
      myOptimizeImportsFix = QuickFixFactory.getInstance().createOptimizeImportsFix(true, myFile);
    }
    myCurrentEntryIndex = entryIndex;
  }

  private boolean isRedundantImport(@NotNull PsiJavaFile javaFile, @NotNull PsiImportStatementBase importStatement) {
    boolean isRedundant = myRefCountHolder.isRedundant(importStatement);
    if (!isRedundant && !(importStatement instanceof PsiImportStaticStatement)) {
      // check import from the same package
      String packageName = javaFile.getPackageName();
      PsiJavaCodeReferenceElement reference = importStatement.getImportReference();
      PsiElement resolved = reference == null ? null : reference.resolve();
      if (resolved instanceof PsiPackage psiPackage) {
        isRedundant = packageName.equals(psiPackage.getQualifiedName());
      }
      else if (resolved instanceof PsiClass psiClass && !importStatement.isOnDemand()) {
        String qName = psiClass.getQualifiedName();
        if (qName != null) {
          String name = psiClass.getName();
          isRedundant = qName.equals(packageName + '.' + name);
        }
      }
    }
    return isRedundant;
  }

  private void registerRedundantImport(@NotNull HighlightInfoHolder holder,
                                       @NotNull PsiImportStatementBase importStatement, @NotNull HighlightDisplayKey unusedImportKey) {
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(myFile);
    Set<String> imports = virtualFile != null ? virtualFile.getCopyableUserData(ImportsHighlightUtil.IMPORTS_FROM_TEMPLATE) : null;
    boolean predefinedImport = imports != null && imports.contains(importStatement.getText());
    String description = !predefinedImport ? JavaAnalysisBundle.message("unused.import.statement") :
                         JavaAnalysisBundle.message("text.unused.import.in.template");
    InspectionProfile profile = getCurrentProfile(myFile);
    TextAttributesKey key = ObjectUtils.notNull(profile.getEditorAttributes(unusedImportKey.getShortName(), myFile),
                                                JavaHighlightInfoTypes.UNUSED_IMPORT.getAttributesKey());
    HighlightInfoType.HighlightInfoTypeImpl configHighlightType =
      new HighlightInfoType.HighlightInfoTypeImpl(profile.getErrorLevel(unusedImportKey, myFile).getSeverity(), key);

    ProblemGroup problemGroup = new ExternalSourceProblemGroup() {
      @Override
      public String getExternalCheckName() {
        return UnusedImportInspection.SHORT_NAME;
      }

      @Override
      public @Nullable String getProblemName() {
        return null;
      }
    };

    HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(configHighlightType)
        .range(importStatement)
        .descriptionAndTooltip(description)
        .problemGroup(problemGroup);

    builder.registerFix(new RemoveAllUnusedImportsFix(), null, HighlightDisplayKey.getDisplayNameByKey(unusedImportKey), null, unusedImportKey);

    IntentionAction switchFix = QuickFixFactory.getInstance().createEnableOptimizeImportsOnTheFlyFix();
    builder.registerFix(switchFix, null, HighlightDisplayKey.getDisplayNameByKey(unusedImportKey), null, unusedImportKey);
    if (!predefinedImport && myOptimizeImportsFix == null) {
      myOptimizeImportsFix = QuickFixFactory.getInstance().createOptimizeImportsFix(true, myFile);
    }
    addInfo(holder, builder);
  }
  static boolean isUnusedImportHighlightInfo(@NotNull PsiFile psiFile, @NotNull HighlightInfo info) {
    TextAttributesKey key = info.type.getAttributesKey();
    InspectionProfile profile = getCurrentProfile(psiFile);
    return key.equals(profile.getEditorAttributes(UnusedImportInspection.SHORT_NAME, psiFile))
          || key.equals(JavaHighlightInfoTypes.UNUSED_IMPORT.getAttributesKey());
  }
}
