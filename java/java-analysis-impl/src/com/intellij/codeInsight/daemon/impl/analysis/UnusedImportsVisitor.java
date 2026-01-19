// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.UnusedImportProvider;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.codeInsight.multiverse.CodeInsightContextUtil;
import com.intellij.codeInspection.ExternalSourceProblemGroup;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.codeInspection.unusedImport.MissortedImportsInspection;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.annotation.ProblemGroup;
import com.intellij.modcommand.ModCommandAction;
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
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.ImportUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

@ApiStatus.Internal
public class UnusedImportsVisitor extends JavaElementVisitor {
  private final @NotNull Document myDocument;
  private final @NotNull CodeInsightContext myContext;
  private final @NotNull State myState;
  private final @NotNull PsiFile myPsiFile;
  private final @NotNull Project myProject;

  UnusedImportsVisitor(@NotNull PsiFile psiFile, @NotNull Document document) throws ProcessCanceledException {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    myPsiFile = psiFile;
    myProject = psiFile.getProject();
    myState = new State(psiFile);
    myDocument = document;
    myContext = CodeInsightContextUtil.getCodeInsightContext(psiFile);
  }

  void collectHighlights(@NotNull HighlightInfoHolder holder) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();

    HighlightDisplayKey unusedImportKey = HighlightDisplayKey.find(UnusedImportInspection.SHORT_NAME);
    PsiJavaFile javaFile = ObjectUtils.tryCast(myPsiFile, PsiJavaFile.class);
    PsiImportList importList = javaFile == null ? null : javaFile.getImportList();
    if (unusedImportKey != null && isUnusedImportEnabled(unusedImportKey) && importList != null) {
      processImports(javaFile, importList, unusedImportKey);
    }

    boolean errorFound = false;
    for (HighlightInfo.Builder builder : myState.builderList) {
      errorFound |= addInfo(builder, holder);
    }

    if (errorFound) {
      DaemonCodeAnalyzerEx daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(myProject);
      FileStatusMap fileStatusMap = daemonCodeAnalyzer.getFileStatusMap();
      fileStatusMap.setErrorFoundFlag(myDocument, myContext, true);
    }

    if (myState.requiresFix) {
      ModCommandAction fix = QuickFixFactory.getInstance().createOptimizeImportsFix(true, myPsiFile);
      OptimizeImportRestarter.getInstance(myProject).scheduleOnDaemonFinish(myPsiFile, fix);
    }
  }

  private boolean isUnusedImportEnabled(@NotNull HighlightDisplayKey unusedImportKey) {
    if (isToolEnabled(unusedImportKey)) return true;
    for (ImplicitUsageProvider provider : ImplicitUsageProvider.EP_NAME.getExtensionList()) {
      if (provider instanceof UnusedImportProvider uip && uip.isUnusedImportEnabled(myPsiFile)) return true;
    }
    return false;
  }

  private boolean isToolEnabled(@NotNull HighlightDisplayKey displayKey) {
    if (!(myPsiFile instanceof PsiJavaFile)) {
      return false;
    }
    InspectionProfile profile = getCurrentProfile(myPsiFile);
    return profile.isToolEnabled(displayKey, myPsiFile) &&
           HighlightingLevelManager.getInstance(myProject).shouldInspect(myPsiFile) &&
           !HighlightingLevelManager.getInstance(myProject).runEssentialHighlightingOnly(myPsiFile);
  }

  private static @NotNull InspectionProfile getCurrentProfile(@NotNull PsiFile psiFile) {
    Function<? super InspectionProfile, ? extends InspectionProfileWrapper> custom =
      InspectionProfileWrapper.getCustomInspectionProfileWrapper(psiFile);
    InspectionProfileImpl currentProfile = InspectionProjectProfileManager.getInstance(psiFile.getProject()).getCurrentProfile();
    return custom != null ? custom.apply(currentProfile).getInspectionProfile() : currentProfile;
  }

  private static boolean isRedundantImport(@NotNull PsiJavaFile javaFile,
                                           @NotNull PsiImportStatementBase importStatement,
                                           @NotNull LocalRefUseInfo refCountHolder) {
    boolean isRedundant = refCountHolder.isRedundant(importStatement);
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
    if (isRedundant && importStatement instanceof PsiImportModuleStatement moduleStatement &&
        !JavaCodeStyleSettings.getInstance(javaFile).isDeleteUnusedModuleImports()) {
      List<PsiImportModuleStatement> moduleStatements = ImportUtils.optimizeModuleImports(javaFile);
      if (moduleStatements.contains(moduleStatement)) {
        return false;
      }
    }
    if (importStatement instanceof PsiImportModuleStatement moduleStatement) {
      List<PsiImportModuleStatement> moduleStatements = ImportUtils.optimizeModuleImports(javaFile);
      if (!moduleStatements.contains(moduleStatement)) {
        return true;
      }
    }
    return isRedundant;
  }

  private void processImports(@NotNull PsiJavaFile javaFile, @NotNull PsiImportList importList, @NotNull HighlightDisplayKey unusedImportKey) {
    List<PsiImportStatementBase> imports = getRedundantImports(myState, javaFile, importList, statement -> ProgressManager.checkCanceled());
    registerRedundantImports(imports, unusedImportKey);

    HighlightDisplayKey missortedKey = HighlightDisplayKey.find(MissortedImportsInspection.SHORT_NAME);
    if (missortedKey != null && isToolEnabled(missortedKey) && myState.requiresFix) {
      myState.builderList.add(
        HighlightInfo.newHighlightInfo(JavaHighlightInfoTypes.MISSORTED_IMPORTS)
          .range(importList)
          .registerFix(
            QuickFixFactory.getInstance().createOptimizeImportsFix(false, myPsiFile),
            null, HighlightDisplayKey.getDisplayNameByKey(missortedKey), null, missortedKey));
    }
  }

  /**
   * @return all unused imports and their quickfixes in the current file
   */
  public static @Nullable UnusedImportsVisitor.ImportProblems getImportProblems(@NotNull PsiJavaFile javaFile) {
    PsiImportList importList = javaFile.getImportList();
    if (importList == null) return null;
    State state = new State(javaFile);
    List<RedundantImportInfo> redundantImportInfoList = new ArrayList<>();
    List<PsiImportStatementBase> redundantImportList = getRedundantImports(state, javaFile, importList, null);
    for (PsiImportStatementBase importStatement: redundantImportList) {
      List<ModCommandAction> fixes = getRedundantImportFixes(redundantImportList);
      redundantImportInfoList.add(
        new RedundantImportInfo(
          importStatement,
          HighlightSeverity.WARNING,
          UnusedImportInspection.SHORT_NAME,
          JavaAnalysisBundle.message("unused.import.statement"),
          fixes
        )
      );
    }
    return new ImportProblems(redundantImportInfoList, !redundantImportList.isEmpty() || state.requiresFix);
  }

  private static @NotNull List<PsiImportStatementBase> getRedundantImports(
    @NotNull State state,
    @NotNull PsiJavaFile javaFile,
    @NotNull PsiImportList list,
    @Nullable Consumer<PsiImportStatementBase> statementPreProcessor
  ) {
    List<PsiImportStatementBase> redundantImports = new ArrayList<>();
    for (PsiImportStatementBase importStatement : list.getAllImportStatements()) {
      if (statementPreProcessor != null) {
        statementPreProcessor.accept(importStatement);
      }
      if (importStatement.isForeignFileImport()) continue;
      if (PsiUtilCore.hasErrorElementChild(importStatement)) continue;

      if (isRedundantImport(javaFile, importStatement, state.refCountHolder)) {
        redundantImports.add(importStatement);
      }
      else {
        int entryIndex = JavaCodeStyleManager.getInstance(javaFile.getProject()).findEntryIndex(importStatement);
        if (entryIndex < state.currentEntryIndex && !state.requiresFix) {
          // missorted imports found
          state.requiresFix = true;
        }
        state.currentEntryIndex = entryIndex;
      }
    }
    return redundantImports;
  }

  /**
   * @return true if added highlighted info was a error, false otherwise
   */
  private static boolean addInfo(@NotNull HighlightInfo.Builder builder, @NotNull HighlightInfoHolder holder) {
    HighlightInfo info = builder.create();
    boolean errorFound = false;
    if (info != null && info.getSeverity() == HighlightSeverity.ERROR) {
      errorFound = true;
    }
    holder.add(info);
    return errorFound;
  }

  private void registerRedundantImports(
    @NotNull List<PsiImportStatementBase> redundantImportList,
    @NotNull HighlightDisplayKey unusedImportKey
  ) {
    for (PsiImportStatementBase importStatement : redundantImportList) {
      VirtualFile virtualFile = PsiUtilCore.getVirtualFile(myPsiFile);
      Set<String> imports = virtualFile != null ? virtualFile.getCopyableUserData(ImportsHighlightUtil.IMPORTS_FROM_TEMPLATE) : null;
      boolean predefinedImport = imports != null && imports.contains(importStatement.getText());
      String description = !predefinedImport ? JavaAnalysisBundle.message("unused.import.statement") :
                           JavaAnalysisBundle.message("text.unused.import.in.template");
      InspectionProfile profile = getCurrentProfile(myPsiFile);
      TextAttributesKey key = ObjectUtils.notNull(profile.getEditorAttributes(unusedImportKey.getShortName(), myPsiFile),
                                                  JavaHighlightInfoTypes.UNUSED_IMPORT.getAttributesKey());
      HighlightInfoType.HighlightInfoTypeImpl configHighlightType =
        new HighlightInfoType.HighlightInfoTypeImpl(profile.getErrorLevel(unusedImportKey, myPsiFile).getSeverity(), key);

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

      List<ModCommandAction> fixes = getRedundantImportFixes(redundantImportList);
      for (ModCommandAction fix : fixes) {
        builder.registerFix(fix, null, HighlightDisplayKey.getDisplayNameByKey(unusedImportKey), null, unusedImportKey);
      }

      myState.builderList.add(builder);

      if (!predefinedImport && !myState.requiresFix/* && importStatement.resolve() != null*/) {
        myState.requiresFix = true;
      }
    }
  }

  private static @NotNull List<ModCommandAction> getRedundantImportFixes(List<PsiImportStatementBase> redundantImportList) {
    IntentionAction switchFix = QuickFixFactory.getInstance().createEnableOptimizeImportsOnTheFlyFix();
    return List.of(new RemoveAllUnusedImportsFix(redundantImportList), Objects.requireNonNull(switchFix.asModCommandAction()));
  }

  private static class State {
    private boolean requiresFix = false;
    private int currentEntryIndex = -1;
    private final @NotNull LocalRefUseInfo refCountHolder;
    private final @NotNull List<HighlightInfo.Builder> builderList = new ArrayList<>();

    State(@NotNull PsiFile psiFile) {
      refCountHolder = LocalRefUseInfo.forFile(psiFile);
    }
  }

  /**
   * Stores information about highlighting for redundant or unused import from the point of IntelliJ.
   */
  public record RedundantImportInfo(@NotNull PsiImportStatementBase context, @NotNull HighlightSeverity severity, @NotNull String shortName,
                                    @NotNull String description, @NotNull List<ModCommandAction> fixes) {
  }

  /**
   * Stores all information about inconsistencies inside an import list.
   *
   * @param shouldOrganizeImports whether the imports are missorted and could be organized
   */
  public record ImportProblems(@NotNull List<RedundantImportInfo> redundantImportInfoList, boolean shouldOrganizeImports) {
  }
}
