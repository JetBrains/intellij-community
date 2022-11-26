// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.*;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.PriorityIntentionActionWrapper;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspectionBase;
import com.intellij.codeInspection.util.SpecialAnnotationsUtilBase;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomNamedTarget;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

class PostHighlightingVisitor {
  private static final Logger LOG = Logger.getInstance(PostHighlightingVisitor.class);
  private final RefCountHolder myRefCountHolder;
  @NotNull private final Project myProject;
  private final PsiFile myFile;
  @NotNull private final Document myDocument;

  private boolean myHasRedundantImports;
  private int myCurrentEntryIndex;
  private boolean myHasMisSortedImports;
  private final UnusedSymbolLocalInspectionBase myUnusedSymbolInspection;
  private final HighlightDisplayKey myDeadCodeKey;
  private final HighlightInfoType myDeadCodeInfoType;
  private final UnusedDeclarationInspectionBase myDeadCodeInspection;

  private void optimizeImportsOnTheFlyLater(@NotNull ProgressIndicator progress) {
    if ((myHasRedundantImports || myHasMisSortedImports) && !progress.isCanceled()) {
      scheduleOptimizeOnDaemonFinished();
    }
  }

  private void scheduleOptimizeOnDaemonFinished() {
    Disposable daemonDisposable = Disposer.newDisposable();
    VirtualFile virtualFile = myFile.getVirtualFile();
    boolean isInContent = virtualFile != null && ModuleUtilCore.projectContainsFile(myProject, virtualFile, false);

    // schedule optimise action after all applyInformation() calls
    myProject.getMessageBus().connect(daemonDisposable)
      .subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
        @Override
        public void daemonFinished(@NotNull Collection<? extends FileEditor> incomingFileEditors) {
          Disposer.dispose(daemonDisposable);
          if (((DaemonCodeAnalyzerEx)DaemonCodeAnalyzer.getInstance(myProject)).isErrorAnalyzingFinished(myFile)) {
            // later because should invoke when highlighting is finished (OptimizeImportsFix relies on that)
            AppUIExecutor.onUiThread().later().withDocumentsCommitted(myProject).execute(() -> {
              if (!myFile.isValid() || !myFile.isWritable()) return;
              IntentionAction optimizeImportsFix = QuickFixFactory.getInstance().createOptimizeImportsFix(true, isInContent);
              if (optimizeImportsFix.isAvailable(myProject, null, myFile)) {
                optimizeImportsFix.invoke(myProject, null, myFile);
              }
            });
          }
          else {
            scheduleOptimizeOnDaemonFinished();
          }
        }
      });
  }

  PostHighlightingVisitor(@NotNull PsiFile file,
                          @NotNull Document document,
                          @NotNull RefCountHolder refCountHolder) throws ProcessCanceledException {
    myProject = file.getProject();
    myFile = file;
    myDocument = document;

    myCurrentEntryIndex = -1;

    myRefCountHolder = refCountHolder;


    ApplicationManager.getApplication().assertReadAccessAllowed();

    InspectionProfile profile = InspectionProjectProfileManager.getInstance(myProject).getCurrentProfile();

    myDeadCodeKey = HighlightDisplayKey.find(UnusedDeclarationInspectionBase.SHORT_NAME);

    myDeadCodeInspection = (UnusedDeclarationInspectionBase)profile.getUnwrappedTool(UnusedDeclarationInspectionBase.SHORT_NAME, myFile);
    LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode() || myDeadCodeInspection != null);

    myUnusedSymbolInspection = myDeadCodeInspection != null ? myDeadCodeInspection.getSharedLocalInspectionTool() : null;

    myDeadCodeInfoType = myDeadCodeKey == null
                         ? HighlightInfoType.UNUSED_SYMBOL
                         : new HighlightInfoType.HighlightInfoTypeImpl(profile.getErrorLevel(myDeadCodeKey, myFile).getSeverity(),
                                                                       ObjectUtils.notNull(profile.getEditorAttributes(myDeadCodeKey.toString(), myFile), 
                                                                                           HighlightInfoType.UNUSED_SYMBOL.getAttributesKey()));
  }

  void collectHighlights(@NotNull HighlightInfoHolder result, @NotNull ProgressIndicator progress) {
    boolean errorFound = false;

    if (isToolEnabled(myDeadCodeKey)) {
      GlobalUsageHelper globalUsageHelper = myRefCountHolder.getGlobalUsageHelper(myFile, myDeadCodeInspection);
      FileViewProvider viewProvider = myFile.getViewProvider();
      Set<Language> relevantLanguages = viewProvider.getLanguages();
      for (Language language : relevantLanguages) {
        ProgressManager.checkCanceled();
        PsiElement psiRoot = viewProvider.getPsi(language);
        HighlightingLevelManager highlightingLevelManager = HighlightingLevelManager.getInstance(myProject);
        if (!highlightingLevelManager.shouldInspect(psiRoot) || highlightingLevelManager.runEssentialHighlightingOnly(psiRoot)) continue;
        List<PsiElement> elements = CollectHighlightsUtil.getElementsInRange(psiRoot, 0, myFile.getTextLength());
        for (PsiElement element : elements) {
          ProgressManager.checkCanceled();
          if (element instanceof PsiIdentifier identifier) {
            HighlightInfo.Builder builder = processIdentifier(identifier, progress, globalUsageHelper);
            if (builder != null) {
              HighlightInfo info = builder.create();
              errorFound |= info != null && info.getSeverity() == HighlightSeverity.ERROR;
              result.add(info);
            }
          }
        }
      }
    }

    HighlightDisplayKey unusedImportKey = HighlightDisplayKey.find(UnusedImportInspection.SHORT_NAME);
    if (isUnusedImportEnabled(unusedImportKey)) {
      PsiImportList importList = ((PsiJavaFile)myFile).getImportList();
      if (importList != null) {
        PsiImportStatementBase[] imports = importList.getAllImportStatements();
        for (PsiImportStatementBase statement : imports) {
          ProgressManager.checkCanceled();
          HighlightInfo.Builder builder = processImport(statement, unusedImportKey);
          if (builder != null) {
            HighlightInfo info = builder.create();
            errorFound |= info != null && info.getSeverity() == HighlightSeverity.ERROR;
            result.add(info);
          }
        }
      }
    }

    if (errorFound) {
      DaemonCodeAnalyzerEx daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(myProject);
      FileStatusMap fileStatusMap = daemonCodeAnalyzer.getFileStatusMap();
      fileStatusMap.setErrorFoundFlag(myProject, myDocument, true);
    }

    optimizeImportsOnTheFlyLater(progress);
  }

  private boolean isUnusedImportEnabled(HighlightDisplayKey unusedImportKey) {
    if (isToolEnabled(unusedImportKey)) return true;
    for (ImplicitUsageProvider provider : ImplicitUsageProvider.EP_NAME.getExtensionList()) {
      if (provider instanceof UnusedImportProvider && ((UnusedImportProvider)provider).isUnusedImportEnabled(myFile)) return true;
    }
    return false;
  }

  private boolean isToolEnabled(HighlightDisplayKey displayKey) {
    if (!(myFile instanceof PsiJavaFile)) {
      return false;
    }
    InspectionProfile profile = getCurrentProfile();
    return profile.isToolEnabled(displayKey, myFile) &&
           HighlightingLevelManager.getInstance(myProject).shouldInspect(myFile) &&
           !HighlightingLevelManager.getInstance(myProject).runEssentialHighlightingOnly(myFile);
  }

  @NotNull
  private InspectionProfile getCurrentProfile() {
    Function<? super InspectionProfile, ? extends InspectionProfileWrapper> custom = InspectionProfileWrapper.getCustomInspectionProfileWrapper(myFile);
    InspectionProfileImpl currentProfile = InspectionProjectProfileManager.getInstance(myProject).getCurrentProfile();
    return custom != null ? custom.apply(currentProfile).getInspectionProfile() : currentProfile;
  }

  private HighlightInfo.Builder processIdentifier(@NotNull PsiIdentifier identifier, @NotNull ProgressIndicator progress, @NotNull GlobalUsageHelper helper) {
    PsiElement parent = identifier.getParent();
    if (!(parent instanceof PsiVariable || parent instanceof PsiMember)) return null;

    if (SuppressionUtil.inspectionResultSuppressed(identifier, myUnusedSymbolInspection)) return null;

    if (parent instanceof PsiLocalVariable && myUnusedSymbolInspection.LOCAL_VARIABLE) {
      return processLocalVariable((PsiLocalVariable)parent, identifier);
    }
    if (parent instanceof PsiField && compareVisibilities((PsiModifierListOwner)parent, myUnusedSymbolInspection.getFieldVisibility())) {
      return processField(myProject, (PsiField)parent, identifier, progress, helper);
    }
    if (parent instanceof PsiParameter) {
      PsiElement declarationScope = ((PsiParameter)parent).getDeclarationScope();
      boolean needToProcessParameter;
      if (declarationScope instanceof PsiMethod || declarationScope instanceof PsiLambdaExpression) {
        if (declarationScope instanceof PsiLambdaExpression) {
          declarationScope = PsiTreeUtil.getParentOfType(declarationScope, PsiModifierListOwner.class);
        }
        needToProcessParameter = compareVisibilities((PsiModifierListOwner)declarationScope, myUnusedSymbolInspection.getParameterVisibility());
      }
      else {
        needToProcessParameter = myUnusedSymbolInspection.LOCAL_VARIABLE;
      }
      if (needToProcessParameter) {
        if (SuppressionUtil.isSuppressed(identifier, UnusedSymbolLocalInspectionBase.UNUSED_PARAMETERS_SHORT_NAME)) return null;
        return processParameter(myProject, (PsiParameter)parent, identifier);
      }
    }
    if (parent instanceof PsiMethod) {
      if (myUnusedSymbolInspection.isIgnoreAccessors() && PropertyUtilBase.isSimplePropertyAccessor((PsiMethod)parent)) {
        return null;
      }
      if (compareVisibilities((PsiModifierListOwner)parent, myUnusedSymbolInspection.getMethodVisibility())) {
        return processMethod(myProject, (PsiMethod)parent, identifier, progress, helper);
      }
    }
    if (parent instanceof PsiClass) {
      String acceptedVisibility = ((PsiClass)parent).getContainingClass() == null ? myUnusedSymbolInspection.getClassVisibility()
                                                                                        : myUnusedSymbolInspection.getInnerClassVisibility();
      if (compareVisibilities((PsiModifierListOwner)parent, acceptedVisibility)) {
        return processClass(myProject, (PsiClass)parent, identifier, progress, helper);
      }
    }
    return null;
  }

  private static boolean compareVisibilities(PsiModifierListOwner listOwner, String visibility) {
    if (visibility != null) {
      while (listOwner != null) {
        if (VisibilityUtil.compare(VisibilityUtil.getVisibilityModifier(listOwner.getModifierList()), visibility) >= 0) {
          return true;
        }
        listOwner = PsiTreeUtil.getParentOfType(listOwner, PsiModifierListOwner.class, true);
      }
    }
    return false;
  }

  private HighlightInfo.Builder processLocalVariable(@NotNull PsiLocalVariable variable,
                                                     @NotNull PsiIdentifier identifier) {
    if (PsiUtil.isIgnoredName(variable.getName())) return null;
    if (UnusedSymbolUtil.isImplicitUsage(myProject, variable)) return null;

    String message = null;
    IntentionAction fix = null;
    if (!myRefCountHolder.isReferenced(variable)) {
      message = JavaErrorBundle.message("local.variable.is.never.used", identifier.getText());
      fix = variable instanceof PsiResourceVariable ? QuickFixFactory.getInstance().createRenameToIgnoredFix(variable, false)
                                                    : QuickFixFactory.getInstance().createRemoveUnusedVariableFix(variable);
    }

    else if (!myRefCountHolder.isReferencedForRead(variable) && !UnusedSymbolUtil.isImplicitRead(myProject, variable)) {
      message = JavaErrorBundle.message("local.variable.is.not.used.for.reading", identifier.getText());
      fix = QuickFixFactory.getInstance().createRemoveUnusedVariableFix(variable);
    }

    else if (!variable.hasInitializer() &&
        !myRefCountHolder.isReferencedForWrite(variable) &&
        !UnusedSymbolUtil.isImplicitWrite(myProject, variable)) {
      message = JavaErrorBundle.message("local.variable.is.not.assigned", identifier.getText());
      fix = QuickFixFactory.getInstance().createAddVariableInitializerFix(variable);
    }

    if (message != null) {
      HighlightInfo.Builder highlightInfo = UnusedSymbolUtil.createUnusedSymbolInfoBuilder(identifier, message, myDeadCodeInfoType, UnusedDeclarationInspectionBase.SHORT_NAME);
      highlightInfo.registerFix(fix, null, HighlightDisplayKey.getDisplayNameByKey(myDeadCodeKey), null, myDeadCodeKey);
      return highlightInfo;
    }

    return null;
  }

  @Nullable
  private HighlightInfo.Builder processField(@NotNull Project project,
                                     @NotNull PsiField field,
                                     @NotNull PsiIdentifier identifier,
                                     @NotNull ProgressIndicator progress,
                                     @NotNull GlobalUsageHelper helper) {
    if (HighlightUtil.isSerializationImplicitlyUsedField(field)) {
      return null;
    }
    if (field.hasModifierProperty(PsiModifier.PRIVATE)) {
      QuickFixFactory quickFixFactory = QuickFixFactory.getInstance();
      if (!myRefCountHolder.isReferenced(field) && !UnusedSymbolUtil.isImplicitUsage(myProject, field)) {
        String message = JavaErrorBundle.message("private.field.is.not.used", identifier.getText());

        HighlightInfo.Builder builder = suggestionsToMakeFieldUsed(field, identifier, message);
        if (!field.hasInitializer() && !field.hasModifierProperty(PsiModifier.FINAL)) {
          TextRange fixRange = HighlightMethodUtil.getFixRange(field);
          IntentionAction action = quickFixFactory.createCreateConstructorParameterFromFieldFix(field);
          builder.registerFix(action, null, HighlightDisplayKey.getDisplayNameByKey(myDeadCodeKey), fixRange, myDeadCodeKey);
        }
        return builder;
      }

      boolean readReferenced = myRefCountHolder.isReferencedForRead(field);
      if (!readReferenced && !UnusedSymbolUtil.isImplicitRead(project, field)) {
        String message = getNotUsedForReadingMessage(field, identifier);
        return suggestionsToMakeFieldUsed(field, identifier, message);
      }

      if (field.hasInitializer()) {
        return null;
      }
      boolean writeReferenced = myRefCountHolder.isReferencedForWrite(field);
      if (!writeReferenced && !UnusedSymbolUtil.isImplicitWrite(project, field)) {
        String message = JavaErrorBundle.message("private.field.is.not.assigned", identifier.getText());
        HighlightInfo.Builder
          info = UnusedSymbolUtil.createUnusedSymbolInfoBuilder(identifier, message, myDeadCodeInfoType, UnusedDeclarationInspectionBase.SHORT_NAME);

        IntentionAction action1 = quickFixFactory.createCreateGetterOrSetterFix(false, true, field);
        info.registerFix(action1, null, HighlightDisplayKey.getDisplayNameByKey(myDeadCodeKey), null, myDeadCodeKey);
        if (!field.hasModifierProperty(PsiModifier.FINAL)) {
          TextRange fixRange = HighlightMethodUtil.getFixRange(field);
          IntentionAction action = quickFixFactory.createCreateConstructorParameterFromFieldFix(field);
          info.registerFix(action, null, HighlightDisplayKey.getDisplayNameByKey(myDeadCodeKey), fixRange, myDeadCodeKey);
        }
        SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes(field, annoName -> {
          IntentionAction action = quickFixFactory.createAddToImplicitlyWrittenFieldsFix(project, annoName);
          info.registerFix(action, null, HighlightDisplayKey.getDisplayNameByKey(myDeadCodeKey), null, myDeadCodeKey);
          return true;
        });
        return info;
      }
    }
    else if (UnusedSymbolUtil.isImplicitUsage(myProject, field) && !UnusedSymbolUtil.isImplicitWrite(myProject, field)) {
      return null;
    }
    else if (UnusedSymbolUtil.isFieldUnused(myProject, myFile, field, progress, helper)) {
      if (UnusedSymbolUtil.isImplicitWrite(myProject, field)) {
        String message = getNotUsedForReadingMessage(field, identifier);
        HighlightInfo.Builder
          highlightInfo = UnusedSymbolUtil.createUnusedSymbolInfoBuilder(identifier, message, myDeadCodeInfoType, UnusedDeclarationInspectionBase.SHORT_NAME);
        IntentionAction action = QuickFixFactory.getInstance().createSafeDeleteFix(field);
        highlightInfo.registerFix(action, null, HighlightDisplayKey.getDisplayNameByKey(myDeadCodeKey), null, myDeadCodeKey);
        return highlightInfo;
      }
      return formatUnusedSymbolHighlightInfo(project, "field.is.not.used", field, myDeadCodeKey, myDeadCodeInfoType, identifier);
    }
    return null;
  }

  @NotNull
  private static @NlsContexts.DetailedDescription String getNotUsedForReadingMessage(@NotNull PsiField field, @NotNull PsiIdentifier identifier) {
    String visibility = VisibilityUtil.getVisibilityStringToDisplay(field);

    String message = JavaErrorBundle.message("field.is.not.used.for.reading", visibility, identifier.getText());

    return StringUtil.capitalize(message);
  }

  @NotNull
  private HighlightInfo.Builder suggestionsToMakeFieldUsed(@NotNull PsiField field, @NotNull PsiIdentifier identifier, @NotNull @NlsContexts.DetailedDescription String message) {
    HighlightInfo.Builder builder = UnusedSymbolUtil.createUnusedSymbolInfoBuilder(identifier, message, myDeadCodeInfoType, UnusedDeclarationInspectionBase.SHORT_NAME);
    SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes(field, annoName -> {
      @Nullable IntentionAction action =
        QuickFixFactory.getInstance().createAddToDependencyInjectionAnnotationsFix(field.getProject(), annoName);
      builder.registerFix(action, null, HighlightDisplayKey.getDisplayNameByKey(myDeadCodeKey), null, myDeadCodeKey);
      return true;
    });
    IntentionAction action3 = QuickFixFactory.getInstance().createRemoveUnusedVariableFix(field);
    builder.registerFix(action3, null, HighlightDisplayKey.getDisplayNameByKey(myDeadCodeKey), null, myDeadCodeKey);
    IntentionAction action2 = QuickFixFactory.getInstance().createCreateGetterOrSetterFix(true, false, field);
    builder.registerFix(action2, null, HighlightDisplayKey.getDisplayNameByKey(myDeadCodeKey), null, myDeadCodeKey);
    IntentionAction action1 = QuickFixFactory.getInstance().createCreateGetterOrSetterFix(false, true, field);
    builder.registerFix(action1, null, HighlightDisplayKey.getDisplayNameByKey(myDeadCodeKey), null, myDeadCodeKey);
    IntentionAction action = QuickFixFactory.getInstance().createCreateGetterOrSetterFix(true, true, field);
    builder.registerFix(action, null, HighlightDisplayKey.getDisplayNameByKey(myDeadCodeKey), null, myDeadCodeKey);
    return builder;
  }

  private final Map<PsiMethod, Boolean> isOverriddenOrOverrides = ConcurrentFactoryMap.createMap(method-> {
      boolean overrides = SuperMethodsSearch.search(method, null, true, false).findFirst() != null;
      return overrides || OverridingMethodsSearch.search(method).findFirst() != null;
    }
  );

  private boolean isOverriddenOrOverrides(@NotNull PsiMethod method) {
    return isOverriddenOrOverrides.get(method);
  }

  private HighlightInfo.Builder processParameter(@NotNull Project project,
                                                 @NotNull PsiParameter parameter,
                                                 @NotNull PsiIdentifier identifier) {
    if (PsiUtil.isIgnoredName(parameter.getName())) return null;
    PsiElement declarationScope = parameter.getDeclarationScope();
    QuickFixFactory quickFixFactory = QuickFixFactory.getInstance();
    if (declarationScope instanceof PsiMethod method) {
      if (PsiUtilCore.hasErrorElementChild(method)) return null;
      if ((method.isConstructor() ||
           method.hasModifierProperty(PsiModifier.PRIVATE) ||
           method.hasModifierProperty(PsiModifier.STATIC) ||
           !method.hasModifierProperty(PsiModifier.ABSTRACT) &&
           (!isOverriddenOrOverrides(method) || myUnusedSymbolInspection.checkParameterExcludingHierarchy())) &&
          !method.hasModifierProperty(PsiModifier.NATIVE) &&
          !JavaHighlightUtil.isSerializationRelatedMethod(method, method.getContainingClass()) &&
          !PsiClassImplUtil.isMainOrPremainMethod(method)) {
        if (UnusedSymbolUtil.isInjected(project, method)) return null;
        HighlightInfo.Builder highlightInfo = checkUnusedParameter(parameter, identifier, method);
        if (highlightInfo != null) {
          IntentionAction action1 = quickFixFactory.createRenameToIgnoredFix(parameter, true);
          highlightInfo.registerFix(action1, null, HighlightDisplayKey.getDisplayNameByKey(myDeadCodeKey), null, myDeadCodeKey);
          IntentionAction action = PriorityIntentionActionWrapper.highPriority(quickFixFactory.createSafeDeleteUnusedParameterInHierarchyFix(parameter, myUnusedSymbolInspection.checkParameterExcludingHierarchy() && isOverriddenOrOverrides(method)));
          highlightInfo.registerFix(action, null, HighlightDisplayKey.getDisplayNameByKey(myDeadCodeKey), null, myDeadCodeKey);
          return highlightInfo;
        }
      }
    }
    else if (declarationScope instanceof PsiForeachStatement) {
      HighlightInfo.Builder highlightInfo = checkUnusedParameter(parameter, identifier, null);
      if (highlightInfo != null) {
        IntentionAction action = quickFixFactory.createRenameToIgnoredFix(parameter, false);
        highlightInfo.registerFix(action, null, HighlightDisplayKey.getDisplayNameByKey(myDeadCodeKey), null, myDeadCodeKey);
        return highlightInfo;
      }
    }
    else if (parameter instanceof PsiPatternVariable variable) {
      HighlightInfo.Builder highlightInfo = checkUnusedParameter(parameter, identifier, null);
      if (highlightInfo != null) {
        if (declarationScope.getParent() instanceof PsiSwitchBlock) {
          if (variable.getParent() instanceof PsiDeconstructionPattern) {
            IntentionAction action = quickFixFactory.createDeleteFix(parameter);
            highlightInfo.registerFix(action, null, null, null, null);
          }
          else {
            IntentionAction action = quickFixFactory.createRenameToIgnoredFix(parameter, false);
            highlightInfo.registerFix(action, null, null, null, null);
          }
        }
        else if (!(variable.getPattern() instanceof PsiTypeTestPattern pattern && pattern.getParent() instanceof PsiDeconstructionList)) {
          IntentionAction action = quickFixFactory.createDeleteFix(parameter);
          highlightInfo.registerFix(action, null, null, null, null);
        }
        return highlightInfo;
      }
    }
    else if (myUnusedSymbolInspection.checkParameterExcludingHierarchy() && declarationScope instanceof PsiLambdaExpression) {
      HighlightInfo.Builder highlightInfo = checkUnusedParameter(parameter, identifier, null);
      if (highlightInfo != null) {
        IntentionAction action1 = quickFixFactory.createRenameToIgnoredFix(parameter, true);
        highlightInfo.registerFix(action1, null, HighlightDisplayKey.getDisplayNameByKey(myDeadCodeKey), null, myDeadCodeKey);
        IntentionAction action =
          PriorityIntentionActionWrapper.lowPriority(quickFixFactory.createSafeDeleteUnusedParameterInHierarchyFix(parameter, true));
        highlightInfo.registerFix(action, null, HighlightDisplayKey.getDisplayNameByKey(myDeadCodeKey), null, myDeadCodeKey);
        return highlightInfo;
      }
    }

    return null;
  }

  private HighlightInfo.Builder checkUnusedParameter(@NotNull PsiParameter parameter,
                                                     @NotNull PsiIdentifier identifier,
                                                     @Nullable PsiMethod declarationMethod) {
    if (!myRefCountHolder.isReferenced(parameter) && !UnusedSymbolUtil.isImplicitUsage(myProject, parameter)) {
      String message = JavaErrorBundle.message(parameter instanceof PsiPatternVariable ? 
                                               "pattern.variable.is.not.used" : "parameter.is.not.used", identifier.getText());
      HighlightInfo.Builder info = UnusedSymbolUtil.createUnusedSymbolInfoBuilder(identifier, message, myDeadCodeInfoType, UnusedDeclarationInspectionBase.SHORT_NAME);
      if (declarationMethod != null) {
        IntentionAction assignFix = QuickFixFactory.getInstance().createAssignFieldFromParameterFix();
        IntentionAction createFieldFix = QuickFixFactory.getInstance().createCreateFieldFromParameterFix();
        if (!declarationMethod.isConstructor()) {
          assignFix = PriorityIntentionActionWrapper.lowPriority(assignFix);
          createFieldFix = PriorityIntentionActionWrapper.lowPriority(createFieldFix);
        }
        info.registerFix(assignFix, null, null, null, null);
        info.registerFix(createFieldFix, null, null, null, null);
      }
      return info;
    }
    return null;
  }

  private HighlightInfo.Builder processMethod(@NotNull Project project,
                                              @NotNull PsiMethod method,
                                              @NotNull PsiIdentifier identifier,
                                              @NotNull ProgressIndicator progress,
                                              @NotNull GlobalUsageHelper helper) {
    if (UnusedSymbolUtil.isMethodReferenced(myProject, myFile, method, progress, helper)) {
      return null;
    }
    String key;
    if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
      key = method.isConstructor() ? "private.constructor.is.not.used" : "private.method.is.not.used";
    }
    else {
      key = method.isConstructor() ? "constructor.is.not.used" : "method.is.not.used";
    }
    int options = PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_FQ_CLASS_NAMES;
    String symbolName = HighlightMessageUtil.getSymbolName(method, PsiSubstitutor.EMPTY, options);
    String message = JavaErrorBundle.message(key, symbolName);
    HighlightInfo.Builder builder = UnusedSymbolUtil.createUnusedSymbolInfoBuilder(identifier, message, myDeadCodeInfoType, UnusedDeclarationInspectionBase.SHORT_NAME);
    IntentionAction action = QuickFixFactory.getInstance().createSafeDeleteFix(method);
    builder.registerFix(action, null, HighlightDisplayKey.getDisplayNameByKey(myDeadCodeKey), null, myDeadCodeKey);
    SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes(method, annoName -> {
      IntentionAction fix = QuickFixFactory.getInstance().createAddToDependencyInjectionAnnotationsFix(project, annoName);
      builder.registerFix(fix, null, HighlightDisplayKey.getDisplayNameByKey(myDeadCodeKey), null, myDeadCodeKey);
      return true;
    });
    return builder;
  }

  private HighlightInfo.Builder processClass(@NotNull Project project,
                                                      @NotNull PsiClass aClass,
                                                      @NotNull PsiIdentifier identifier,
                                                      @NotNull ProgressIndicator progress,
                                                      @NotNull GlobalUsageHelper helper) {
    if (UnusedSymbolUtil.isClassUsed(project, myFile, aClass, progress, helper)) {
      return null;
    }

    String pattern;
    if (aClass.getContainingClass() != null && aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
      pattern = aClass.isInterface()
                       ? "private.inner.interface.is.not.used"
                       : "private.inner.class.is.not.used";
    }
    else if (aClass.getParent() instanceof PsiDeclarationStatement) { // local class
      pattern = "local.class.is.not.used";
    }
    else if (aClass instanceof PsiTypeParameter) {
      pattern = "type.parameter.is.not.used";
    }
    else if (aClass.isInterface()) {
      pattern = "interface.is.not.used";
    }
    else if (aClass.isEnum()) {
      pattern = "enum.is.not.used";
    }
    else {
      pattern = "class.is.not.used";
    }
    return formatUnusedSymbolHighlightInfo(myProject, pattern, aClass, myDeadCodeKey, myDeadCodeInfoType, identifier);
  }


  @NotNull
  private static HighlightInfo.Builder formatUnusedSymbolHighlightInfo(@NotNull Project project,
                                                               @NotNull @PropertyKey(resourceBundle = JavaErrorBundle.BUNDLE) String pattern,
                                                               @NotNull PsiNameIdentifierOwner aClass,
                                                               HighlightDisplayKey highlightDisplayKey,
                                                               @NotNull HighlightInfoType highlightInfoType,
                                                               @NotNull PsiElement identifier) {
    String symbolName = aClass.getName();
    String message = JavaErrorBundle.message(pattern, symbolName);
    HighlightInfo.Builder highlightInfo = UnusedSymbolUtil.createUnusedSymbolInfoBuilder(identifier, message, highlightInfoType, UnusedDeclarationInspectionBase.SHORT_NAME);
    IntentionAction action1 = QuickFixFactory.getInstance().createSafeDeleteFix(aClass);
    highlightInfo.registerFix(action1, null, HighlightDisplayKey.getDisplayNameByKey(highlightDisplayKey), null, highlightDisplayKey);
    SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes((PsiModifierListOwner)aClass, annoName -> {
      IntentionAction action = QuickFixFactory.getInstance().createAddToDependencyInjectionAnnotationsFix(project, annoName);
      highlightInfo.registerFix(action, null, HighlightDisplayKey.getDisplayNameByKey(highlightDisplayKey), null, highlightDisplayKey);
      return true;
    });
    return highlightInfo;
  }

  private HighlightInfo.Builder processImport(@NotNull PsiImportStatementBase importStatement, @NotNull HighlightDisplayKey unusedImportKey) {
    // jsp include directive hack
    if (importStatement.isForeignFileImport()) return null;

    if (PsiUtilCore.hasErrorElementChild(importStatement)) return null;

    boolean isRedundant = myRefCountHolder.isRedundant(importStatement);
    if (!isRedundant && !(importStatement instanceof PsiImportStaticStatement)) {
      //check import from same package
      String packageName = ((PsiClassOwner)importStatement.getContainingFile()).getPackageName();
      PsiJavaCodeReferenceElement reference = importStatement.getImportReference();
      PsiElement resolved = reference == null ? null : reference.resolve();
      if (resolved instanceof PsiPackage) {
        isRedundant = packageName.equals(((PsiQualifiedNamedElement)resolved).getQualifiedName());
      }
      else if (resolved instanceof PsiClass && !importStatement.isOnDemand()) {
        String qName = ((PsiClass)resolved).getQualifiedName();
        if (qName != null) {
          String name = ((PomNamedTarget)resolved).getName();
          isRedundant = qName.equals(packageName + '.' + name);
        }
      }
    }

    if (isRedundant) {
      return registerRedundantImport(importStatement, unusedImportKey);
    }

    int entryIndex = JavaCodeStyleManager.getInstance(myProject).findEntryIndex(importStatement);
    if (entryIndex < myCurrentEntryIndex) {
      myHasMisSortedImports = true;
    }
    myCurrentEntryIndex = entryIndex;

    return null;
  }

  @NotNull
  private HighlightInfo.Builder registerRedundantImport(@NotNull PsiImportStatementBase importStatement, @NotNull HighlightDisplayKey unusedImportKey) {
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(myFile);
    Set<String> imports = virtualFile != null ? virtualFile.getCopyableUserData(ImportsHighlightUtil.IMPORTS_FROM_TEMPLATE) : null;
    boolean predefinedImport = imports != null && imports.contains(importStatement.getText());
    String description = !predefinedImport ? JavaAnalysisBundle.message("unused.import.statement") :
                         JavaAnalysisBundle.message("text.unused.import.in.template");
    InspectionProfile profile = getCurrentProfile();
    HighlightInfoType.HighlightInfoTypeImpl configHighlightType =
      new HighlightInfoType.HighlightInfoTypeImpl(profile.getErrorLevel(unusedImportKey, myFile).getSeverity(),
                                                  ObjectUtils.notNull(profile.getEditorAttributes(unusedImportKey.toString(), myFile),
                                                                      JavaHighlightInfoTypes.UNUSED_IMPORT.getAttributesKey()));

    HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(configHighlightType)
        .range(importStatement)
        .descriptionAndTooltip(description)
        .group(GeneralHighlightingPass.POST_UPDATE_ALL);

    boolean isInContent = virtualFile != null && ModuleUtilCore.projectContainsFile(myProject, virtualFile, false);
    IntentionAction action1 = QuickFixFactory.getInstance().createOptimizeImportsFix(false, isInContent);
    info.registerFix(action1, null, HighlightDisplayKey.getDisplayNameByKey(unusedImportKey), null, unusedImportKey);

    @Nullable IntentionAction action = QuickFixFactory.getInstance().createEnableOptimizeImportsOnTheFlyFix();
    info.registerFix(action, null, HighlightDisplayKey.getDisplayNameByKey(unusedImportKey), null, unusedImportKey);
    if (!predefinedImport) myHasRedundantImports = true;
    return info;
  }
}
