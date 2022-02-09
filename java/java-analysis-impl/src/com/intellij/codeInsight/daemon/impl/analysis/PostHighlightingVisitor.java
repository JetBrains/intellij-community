// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.UnusedImportProvider;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.PriorityIntentionActionWrapper;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
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
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
      // schedule optimise action at the time of session disposal, which is after all applyInformation() calls
      Disposable invokeFixLater = () -> {
        // later because should invoke when highlighting is finished
        AppUIExecutor.onUiThread().later().withDocumentsCommitted(myProject).execute(() -> {
          if (!myFile.isValid() || !myFile.isWritable()) return;
          IntentionAction optimizeImportsFix = QuickFixFactory.getInstance().createOptimizeImportsFix(true);
          if (optimizeImportsFix.isAvailable(myProject, null, myFile)) {
            optimizeImportsFix.invoke(myProject, null, myFile);
          }
        });
      };
      try {
        Disposer.register((DaemonProgressIndicator)progress, invokeFixLater);
      }
      catch (Exception ignored) {
        // suppress "parent already has been disposed" exception here
      }
      if (progress.isCanceled()) {
        Disposer.dispose(invokeFixLater);
        Disposer.dispose((DaemonProgressIndicator)progress);
        progress.checkCanceled();
      }
    }
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
                                                                       HighlightInfoType.UNUSED_SYMBOL.getAttributesKey());
  }

  void collectHighlights(@NotNull HighlightInfoHolder result, @NotNull ProgressIndicator progress) {
    boolean errorFound = false;

    if (isToolEnabled(myDeadCodeKey)) {
      GlobalUsageHelper globalUsageHelper = myRefCountHolder.getGlobalUsageHelper(myFile, myDeadCodeInspection, true);
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
          if (element instanceof PsiIdentifier) {
            PsiIdentifier identifier = (PsiIdentifier)element;
            HighlightInfo info = processIdentifier(identifier, progress, globalUsageHelper);
            if (info != null) {
              errorFound |= info.getSeverity() == HighlightSeverity.ERROR;
              result.add(info);
              result.queueToUpdateIncrementally();
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
          HighlightInfo info = processImport(statement, unusedImportKey);
          if (info != null) {
            errorFound |= info.getSeverity() == HighlightSeverity.ERROR;
            result.add(info);
            result.queueToUpdateIncrementally();
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
    InspectionProfile profile = InspectionProjectProfileManager.getInstance(myProject).getCurrentProfile();
    return profile.isToolEnabled(displayKey, myFile) &&
           myFile instanceof PsiJavaFile &&
           HighlightingLevelManager.getInstance(myProject).shouldInspect(myFile) &&
           !HighlightingLevelManager.getInstance(myProject).runEssentialHighlightingOnly(myFile);
  }

  @Nullable
  private HighlightInfo processIdentifier(@NotNull PsiIdentifier identifier, @NotNull ProgressIndicator progress, @NotNull GlobalUsageHelper helper) {
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

  @Nullable
  private HighlightInfo processLocalVariable(@NotNull PsiLocalVariable variable,
                                             @NotNull PsiIdentifier identifier) {
    if (variable instanceof PsiResourceVariable && PsiUtil.isIgnoredName(variable.getName())) return null;
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
      HighlightInfo highlightInfo = UnusedSymbolUtil.createUnusedSymbolInfo(identifier, message, myDeadCodeInfoType, UnusedDeclarationInspectionBase.SHORT_NAME);
      QuickFixAction.registerQuickFixAction(highlightInfo, fix, myDeadCodeKey);
      return highlightInfo;
    }

    return null;
  }

  @Nullable
  private HighlightInfo processField(@NotNull Project project,
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

        HighlightInfo highlightInfo = suggestionsToMakeFieldUsed(field, identifier, message);
        if (!field.hasInitializer() && !field.hasModifierProperty(PsiModifier.FINAL)) {
          QuickFixAction.registerQuickFixAction(highlightInfo, HighlightMethodUtil.getFixRange(field),
                                                quickFixFactory.createCreateConstructorParameterFromFieldFix(field), myDeadCodeKey);
        }
        return highlightInfo;
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
        HighlightInfo info = UnusedSymbolUtil.createUnusedSymbolInfo(identifier, message, myDeadCodeInfoType, UnusedDeclarationInspectionBase.SHORT_NAME);

        QuickFixAction.registerQuickFixAction(info, quickFixFactory.createCreateGetterOrSetterFix(false, true, field), myDeadCodeKey);
        if (!field.hasModifierProperty(PsiModifier.FINAL)) {
          QuickFixAction.registerQuickFixAction(info, HighlightMethodUtil.getFixRange(field),
                                                quickFixFactory.createCreateConstructorParameterFromFieldFix(field), myDeadCodeKey);
        }
        SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes(field, annoName -> {
          QuickFixAction.registerQuickFixAction(info, quickFixFactory.createAddToImplicitlyWrittenFieldsFix(project, annoName), myDeadCodeKey);
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
        HighlightInfo highlightInfo = UnusedSymbolUtil.createUnusedSymbolInfo(identifier, message, myDeadCodeInfoType, UnusedDeclarationInspectionBase.SHORT_NAME);
        QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createSafeDeleteFix(field), myDeadCodeKey);
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

  private HighlightInfo suggestionsToMakeFieldUsed(@NotNull PsiField field, @NotNull PsiIdentifier identifier, @NotNull @NlsContexts.DetailedDescription String message) {
    HighlightInfo highlightInfo = UnusedSymbolUtil.createUnusedSymbolInfo(identifier, message, myDeadCodeInfoType, UnusedDeclarationInspectionBase.SHORT_NAME);
    SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes(field, annoName -> {
      QuickFixAction
        .registerQuickFixAction(highlightInfo,
                                QuickFixFactory.getInstance().createAddToDependencyInjectionAnnotationsFix(field.getProject(), annoName),
                                myDeadCodeKey);
      return true;
    });
    QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createRemoveUnusedVariableFix(field), myDeadCodeKey);
    QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createCreateGetterOrSetterFix(true, false, field), myDeadCodeKey);
    QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createCreateGetterOrSetterFix(false, true, field), myDeadCodeKey);
    QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createCreateGetterOrSetterFix(true, true, field), myDeadCodeKey);
    return highlightInfo;
  }

  private final Map<PsiMethod, Boolean> isOverriddenOrOverrides = ConcurrentFactoryMap.createMap(method-> {
      boolean overrides = SuperMethodsSearch.search(method, null, true, false).findFirst() != null;
      return overrides || OverridingMethodsSearch.search(method).findFirst() != null;
    }
  );

  private boolean isOverriddenOrOverrides(@NotNull PsiMethod method) {
    return isOverriddenOrOverrides.get(method);
  }

  @Nullable
  private HighlightInfo processParameter(@NotNull Project project,
                                         @NotNull PsiParameter parameter,
                                         @NotNull PsiIdentifier identifier) {
    if (PsiUtil.isIgnoredName(parameter.getName())) return null;
    PsiElement declarationScope = parameter.getDeclarationScope();
    QuickFixFactory quickFixFactory = QuickFixFactory.getInstance();
    if (declarationScope instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)declarationScope;
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
        HighlightInfo highlightInfo = checkUnusedParameter(parameter, identifier, method);
        if (highlightInfo != null) {
          QuickFixAction.registerQuickFixAction(highlightInfo, quickFixFactory.createRenameToIgnoredFix(parameter, true), myDeadCodeKey);
          QuickFixAction.registerQuickFixAction(highlightInfo, PriorityIntentionActionWrapper.highPriority(quickFixFactory.createSafeDeleteUnusedParameterInHierarchyFix(parameter, myUnusedSymbolInspection.checkParameterExcludingHierarchy() && isOverriddenOrOverrides(method))), myDeadCodeKey);
          return highlightInfo;
        }
      }
    }
    else if (declarationScope instanceof PsiForeachStatement) {
      HighlightInfo highlightInfo = checkUnusedParameter(parameter, identifier, null);
      if (highlightInfo != null) {
        QuickFixAction.registerQuickFixAction(highlightInfo, quickFixFactory.createRenameToIgnoredFix(parameter, false), myDeadCodeKey);
        return highlightInfo;
      }
    }
    else if (parameter instanceof PsiPatternVariable) {
      HighlightInfo highlightInfo = checkUnusedParameter(parameter, identifier, null);
      if (highlightInfo != null) {
        if (declarationScope.getParent() instanceof PsiSwitchBlock) {
          QuickFixAction.registerQuickFixAction(highlightInfo, quickFixFactory.createRenameToIgnoredFix(parameter, false));
        }
        else {
          QuickFixAction.registerQuickFixAction(highlightInfo, quickFixFactory.createDeleteFix(parameter));
        }
        return highlightInfo;
      }
    }
    else if (myUnusedSymbolInspection.checkParameterExcludingHierarchy() && declarationScope instanceof PsiLambdaExpression) {
      HighlightInfo highlightInfo = checkUnusedParameter(parameter, identifier, null);
      if (highlightInfo != null) {
        QuickFixAction.registerQuickFixAction(highlightInfo, quickFixFactory.createRenameToIgnoredFix(parameter, true), myDeadCodeKey);
        QuickFixAction.registerQuickFixAction(highlightInfo, PriorityIntentionActionWrapper.lowPriority(quickFixFactory.createSafeDeleteUnusedParameterInHierarchyFix(parameter, true)), myDeadCodeKey);
        return highlightInfo;
      }
    }

    return null;
  }

  private HighlightInfo checkUnusedParameter(@NotNull PsiParameter parameter,
                                             @NotNull PsiIdentifier identifier,
                                             @Nullable PsiMethod declarationMethod) {
    if (!myRefCountHolder.isReferenced(parameter) && !UnusedSymbolUtil.isImplicitUsage(myProject, parameter)) {
      String message = JavaErrorBundle.message(parameter instanceof PsiPatternVariable ? 
                                               "pattern.variable.is.not.used" : "parameter.is.not.used", identifier.getText());
      HighlightInfo info = UnusedSymbolUtil.createUnusedSymbolInfo(identifier, message, myDeadCodeInfoType, UnusedDeclarationInspectionBase.SHORT_NAME);
      if (declarationMethod != null) {
        IntentionAction assignFix = QuickFixFactory.getInstance().createAssignFieldFromParameterFix();
        IntentionAction createFieldFix = QuickFixFactory.getInstance().createCreateFieldFromParameterFix();
        if (!declarationMethod.isConstructor()) {
          assignFix = PriorityIntentionActionWrapper.lowPriority(assignFix);
          createFieldFix = PriorityIntentionActionWrapper.lowPriority(createFieldFix);
        }
        QuickFixAction.registerQuickFixAction(info, assignFix);
        QuickFixAction.registerQuickFixAction(info, createFieldFix);
      }
      return info;
    }
    return null;
  }

  @Nullable
  private HighlightInfo processMethod(@NotNull Project project,
                                      @NotNull PsiMethod method,
                                      @NotNull PsiIdentifier identifier,
                                      @NotNull ProgressIndicator progress,
                                      @NotNull GlobalUsageHelper helper) {
    if (UnusedSymbolUtil.isMethodReferenced(myProject, myFile, method, progress, helper)) return null;
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
    HighlightInfo highlightInfo = UnusedSymbolUtil.createUnusedSymbolInfo(identifier, message, myDeadCodeInfoType, UnusedDeclarationInspectionBase.SHORT_NAME);
    QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createSafeDeleteFix(method), myDeadCodeKey);
    SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes(method, annoName -> {
      IntentionAction fix = QuickFixFactory.getInstance().createAddToDependencyInjectionAnnotationsFix(project, annoName);
      QuickFixAction.registerQuickFixAction(highlightInfo, fix, myDeadCodeKey);
      return true;
    });
    return highlightInfo;
  }

  @Nullable
  private HighlightInfo processClass(@NotNull Project project,
                                     @NotNull PsiClass aClass,
                                     @NotNull PsiIdentifier identifier,
                                     @NotNull ProgressIndicator progress,
                                     @NotNull GlobalUsageHelper helper) {
    if (UnusedSymbolUtil.isClassUsed(project, myFile, aClass, progress, helper)) return null;

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


  private static HighlightInfo formatUnusedSymbolHighlightInfo(@NotNull Project project,
                                                               @NotNull @PropertyKey(resourceBundle = JavaErrorBundle.BUNDLE) String pattern,
                                                               @NotNull PsiNameIdentifierOwner aClass,
                                                               HighlightDisplayKey highlightDisplayKey,
                                                               @NotNull HighlightInfoType highlightInfoType,
                                                               @NotNull PsiElement identifier) {
    String symbolName = aClass.getName();
    String message = JavaErrorBundle.message(pattern, symbolName);
    HighlightInfo highlightInfo = UnusedSymbolUtil.createUnusedSymbolInfo(identifier, message, highlightInfoType, UnusedDeclarationInspectionBase.SHORT_NAME);
    QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createSafeDeleteFix(aClass), highlightDisplayKey);
    SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes((PsiModifierListOwner)aClass, annoName -> {
      QuickFixAction
        .registerQuickFixAction(highlightInfo,
                                QuickFixFactory.getInstance().createAddToDependencyInjectionAnnotationsFix(project, annoName),
                                highlightDisplayKey);
      return true;
    });
    return highlightInfo;
  }

  @Nullable
  private HighlightInfo processImport(@NotNull PsiImportStatementBase importStatement, @NotNull HighlightDisplayKey unusedImportKey) {
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

  private HighlightInfo registerRedundantImport(@NotNull PsiImportStatementBase importStatement, @NotNull HighlightDisplayKey unusedImportKey) {
    VirtualFile file = PsiUtilCore.getVirtualFile(myFile);
    Set<String> imports = file != null ? file.getCopyableUserData(ImportsHighlightUtil.IMPORTS_FROM_TEMPLATE) : null;
    boolean predefinedImport = imports != null && imports.contains(importStatement.getText());
    String description = !predefinedImport ? JavaAnalysisBundle.message("unused.import.statement") :
                         JavaAnalysisBundle.message("text.unused.import.in.template");
    HighlightInfo info = HighlightInfo.newHighlightInfo(JavaHighlightInfoTypes.UNUSED_IMPORT)
        .range(importStatement)
        .descriptionAndTooltip(description)
        .group(GeneralHighlightingPass.POST_UPDATE_ALL)
        .create();

    QuickFixAction.registerQuickFixAction(info, QuickFixFactory.getInstance().createOptimizeImportsFix(false), unusedImportKey);
    QuickFixAction.registerQuickFixAction(info, QuickFixFactory.getInstance().createEnableOptimizeImportsOnTheFlyFix(), unusedImportKey);
    if (!predefinedImport) myHasRedundantImports = true;
    return info;
  }
}
