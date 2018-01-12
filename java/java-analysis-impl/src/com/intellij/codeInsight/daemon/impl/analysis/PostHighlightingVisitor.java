// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.UnusedImportProvider;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspectionBase;
import com.intellij.codeInspection.util.SpecialAnnotationsUtilBase;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomNamedTarget;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.List;
import java.util.Map;
import java.util.Set;

class PostHighlightingVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.PostHighlightingPass");
  private final RefCountHolder myRefCountHolder;
  @NotNull private final Project myProject;
  private final PsiFile myFile;
  @NotNull private final Document myDocument;

  private boolean myHasRedundantImports;
  private int myCurrentEntryIndex;
  private boolean myHasMissortedImports;
  private final UnusedSymbolLocalInspectionBase myUnusedSymbolInspection;
  private final HighlightDisplayKey myDeadCodeKey;
  private final HighlightInfoType myDeadCodeInfoType;
  private final UnusedDeclarationInspectionBase myDeadCodeInspection;

  private void optimizeImportsOnTheFlyLater(@NotNull final ProgressIndicator progress) {
    if ((myHasRedundantImports || myHasMissortedImports) && !progress.isCanceled()) {
      // schedule optimise action at the time of session disposal, which is after all applyInformation() calls
      Disposable invokeFixLater = () -> {
        // later because should invoke when highlighting is finished
        TransactionGuard.getInstance().submitTransactionLater(myProject, () -> {
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
    DaemonCodeAnalyzerEx daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(myProject);
    FileStatusMap fileStatusMap = daemonCodeAnalyzer.getFileStatusMap();
    InspectionProfile profile = InspectionProjectProfileManager.getInstance(myProject).getCurrentProfile();

    boolean unusedSymbolEnabled = profile.isToolEnabled(myDeadCodeKey, myFile);
    GlobalUsageHelper globalUsageHelper = myRefCountHolder.getGlobalUsageHelper(myFile, myDeadCodeInspection, unusedSymbolEnabled);

    boolean errorFound = false;

    if (unusedSymbolEnabled) {
      final FileViewProvider viewProvider = myFile.getViewProvider();
      final Set<Language> relevantLanguages = viewProvider.getLanguages();
      for (Language language : relevantLanguages) {
        ProgressManager.checkCanceled();
        PsiElement psiRoot = viewProvider.getPsi(language);
        if (!HighlightingLevelManager.getInstance(myProject).shouldInspect(psiRoot)) continue;
        List<PsiElement> elements = CollectHighlightsUtil.getElementsInRange(psiRoot, 0, myFile.getTextLength());
        for (PsiElement element : elements) {
          ProgressManager.checkCanceled();
          if (element instanceof PsiIdentifier) {
            PsiIdentifier identifier = (PsiIdentifier)element;
            HighlightInfo info = processIdentifier(identifier, progress, globalUsageHelper);
            if (info != null) {
              errorFound |= info.getSeverity() == HighlightSeverity.ERROR;
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
        final PsiImportStatementBase[] imports = importList.getAllImportStatements();
        for (PsiImportStatementBase statement : imports) {
          ProgressManager.checkCanceled();
          final HighlightInfo info = processImport(statement, unusedImportKey);
          if (info != null) {
            errorFound |= info.getSeverity() == HighlightSeverity.ERROR;
            result.add(info);
          }
        }
      }
    }

    if (errorFound) {
      fileStatusMap.setErrorFoundFlag(myProject, myDocument, true);
    }

    optimizeImportsOnTheFlyLater(progress);
  }

  private boolean isUnusedImportEnabled(HighlightDisplayKey unusedImportKey) {
    InspectionProfile profile = InspectionProjectProfileManager.getInstance(myProject).getCurrentProfile();
    if (profile.isToolEnabled(unusedImportKey, myFile) &&
        myFile instanceof PsiJavaFile &&
        HighlightingLevelManager.getInstance(myProject).shouldInspect(myFile)) {
      return true;
    }
    final ImplicitUsageProvider[] implicitUsageProviders = Extensions.getExtensions(ImplicitUsageProvider.EP_NAME);
    for (ImplicitUsageProvider provider : implicitUsageProviders) {
      if (provider instanceof UnusedImportProvider && ((UnusedImportProvider)provider).isUnusedImportEnabled(myFile)) return true;
    }
    return false;
  }

  @Nullable
  private HighlightInfo processIdentifier(@NotNull PsiIdentifier identifier, @NotNull ProgressIndicator progress, @NotNull GlobalUsageHelper helper) {
    PsiElement parent = identifier.getParent();
    if (!(parent instanceof PsiVariable || parent instanceof PsiMember)) return null;

    if (SuppressionUtil.inspectionResultSuppressed(identifier, myUnusedSymbolInspection)) return null;

    if (parent instanceof PsiLocalVariable && myUnusedSymbolInspection.LOCAL_VARIABLE) {
      return processLocalVariable((PsiLocalVariable)parent, identifier, progress);
    }
    if (parent instanceof PsiField && compareVisibilities((PsiModifierListOwner)parent, myUnusedSymbolInspection.getFieldVisibility())) {
      return processField(myProject, (PsiField)parent, identifier, progress, helper);
    }
    if (parent instanceof PsiParameter) {
      final PsiElement declarationScope = ((PsiParameter)parent).getDeclarationScope();
      if (declarationScope instanceof PsiMethod ? compareVisibilities((PsiModifierListOwner)declarationScope, myUnusedSymbolInspection.getParameterVisibility())
                                                : myUnusedSymbolInspection.LOCAL_VARIABLE) {
        if (SuppressionUtil.isSuppressed(identifier, UnusedSymbolLocalInspectionBase.UNUSED_PARAMETERS_SHORT_NAME)) return null;
        return processParameter(myProject, (PsiParameter)parent, identifier, progress);
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
      final String acceptedVisibility = ((PsiClass)parent).getContainingClass() == null ? myUnusedSymbolInspection.getClassVisibility()
                                                                                        : myUnusedSymbolInspection.getInnerClassVisibility();
      if (compareVisibilities((PsiModifierListOwner)parent, acceptedVisibility)) {
        return processClass(myProject, (PsiClass)parent, identifier, progress, helper);
      }
    }
    return null;
  }

  private static boolean compareVisibilities(PsiModifierListOwner listOwner, final String visibility) {
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
                                             @NotNull PsiIdentifier identifier,
                                             @NotNull ProgressIndicator progress) {
    if (variable instanceof PsiResourceVariable && PsiUtil.isIgnoredName(variable.getName())) return null;
    if (UnusedSymbolUtil.isImplicitUsage(myProject, variable, progress)) return null;

    if (!myRefCountHolder.isReferenced(variable)) {
      String message = JavaErrorMessages.message("local.variable.is.never.used", identifier.getText());
      HighlightInfo highlightInfo = UnusedSymbolUtil.createUnusedSymbolInfo(identifier, message, myDeadCodeInfoType);
      IntentionAction fix = variable instanceof PsiResourceVariable ? QuickFixFactory.getInstance().createRenameToIgnoredFix(variable) : QuickFixFactory.getInstance().createRemoveUnusedVariableFix(variable);
      QuickFixAction.registerQuickFixAction(highlightInfo, fix, myDeadCodeKey);
      return highlightInfo;
    }

    boolean referenced = myRefCountHolder.isReferencedForRead(variable);
    if (!referenced && !UnusedSymbolUtil.isImplicitRead(myProject, variable, progress)) {
      String message = JavaErrorMessages.message("local.variable.is.not.used.for.reading", identifier.getText());
      HighlightInfo highlightInfo = UnusedSymbolUtil.createUnusedSymbolInfo(identifier, message, myDeadCodeInfoType);
      QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createRemoveUnusedVariableFix(variable), myDeadCodeKey);
      return highlightInfo;
    }

    if (!variable.hasInitializer()) {
      referenced = myRefCountHolder.isReferencedForWrite(variable);
      if (!referenced && !UnusedSymbolUtil.isImplicitWrite(myProject, variable, progress)) {
        String message = JavaErrorMessages.message("local.variable.is.not.assigned", identifier.getText());
        final HighlightInfo unusedSymbolInfo = UnusedSymbolUtil.createUnusedSymbolInfo(identifier, message, myDeadCodeInfoType);
        QuickFixAction.registerQuickFixAction(unusedSymbolInfo, new EmptyIntentionAction(UnusedSymbolLocalInspectionBase.DISPLAY_NAME), myDeadCodeKey);
        return unusedSymbolInfo;
      }
    }

    return null;
  }

  @Nullable
  private HighlightInfo processField(@NotNull final Project project,
                                     @NotNull final PsiField field,
                                     @NotNull PsiIdentifier identifier,
                                     @NotNull ProgressIndicator progress,
                                     @NotNull GlobalUsageHelper helper) {
    if (HighlightUtil.isSerializationImplicitlyUsedField(field)) {
      return null;
    }
    if (field.hasModifierProperty(PsiModifier.PRIVATE)) {
      final QuickFixFactory quickFixFactory = QuickFixFactory.getInstance();
      if (!myRefCountHolder.isReferenced(field) && !UnusedSymbolUtil.isImplicitUsage(myProject, field, progress)) {
        String message = JavaErrorMessages.message("private.field.is.not.used", identifier.getText());

        HighlightInfo highlightInfo = suggestionsToMakeFieldUsed(field, identifier, message);
        if (!field.hasInitializer() && !field.hasModifierProperty(PsiModifier.FINAL)) {
          QuickFixAction.registerQuickFixAction(highlightInfo, HighlightMethodUtil.getFixRange(field), 
                                                quickFixFactory.createCreateConstructorParameterFromFieldFix(field));
        }
        return highlightInfo;
      }

      final boolean readReferenced = myRefCountHolder.isReferencedForRead(field);
      if (!readReferenced && !UnusedSymbolUtil.isImplicitRead(project, field, progress)) {
        String message = JavaErrorMessages.message("private.field.is.not.used.for.reading", identifier.getText());
        return suggestionsToMakeFieldUsed(field, identifier, message);
      }

      if (field.hasInitializer()) {
        return null;
      }
      final boolean writeReferenced = myRefCountHolder.isReferencedForWrite(field);
      if (!writeReferenced && !UnusedSymbolUtil.isImplicitWrite(project, field, progress)) {
        String message = JavaErrorMessages.message("private.field.is.not.assigned", identifier.getText());
        final HighlightInfo info = UnusedSymbolUtil.createUnusedSymbolInfo(identifier, message, myDeadCodeInfoType);

        QuickFixAction.registerQuickFixAction(info, quickFixFactory.createCreateGetterOrSetterFix(false, true, field), myDeadCodeKey);
        if (!field.hasModifierProperty(PsiModifier.FINAL)) {
          QuickFixAction.registerQuickFixAction(info, HighlightMethodUtil.getFixRange(field), 
                                                quickFixFactory.createCreateConstructorParameterFromFieldFix(field));
        }
        SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes(field, annoName -> {
          QuickFixAction.registerQuickFixAction(info, quickFixFactory.createAddToImplicitlyWrittenFieldsFix(project, annoName));
          return true;
        });
        return info;
      }
    }
    else if (UnusedSymbolUtil.isImplicitUsage(myProject, field, progress) && !UnusedSymbolUtil.isImplicitWrite(myProject, field, progress)) {
      return null;
    }
    else if (UnusedSymbolUtil.isFieldUnused(myProject, myFile, field, progress, helper)) {
      if (UnusedSymbolUtil.isImplicitWrite(myProject, field, progress)) {
        String message = JavaErrorMessages.message("private.field.is.not.used.for.reading", identifier.getText());
        HighlightInfo highlightInfo = UnusedSymbolUtil.createUnusedSymbolInfo(identifier, message, myDeadCodeInfoType);
        QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createSafeDeleteFix(field), myDeadCodeKey);
        return highlightInfo;
      }
      return formatUnusedSymbolHighlightInfo(project, "field.is.not.used", field, "fields", myDeadCodeKey, myDeadCodeInfoType, identifier);
    }
    return null;
  }

  private HighlightInfo suggestionsToMakeFieldUsed(@NotNull PsiField field, @NotNull PsiIdentifier identifier, @NotNull String message) {
    HighlightInfo highlightInfo = UnusedSymbolUtil.createUnusedSymbolInfo(identifier, message, myDeadCodeInfoType);
    SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes(field, annoName -> {
      QuickFixAction
        .registerQuickFixAction(highlightInfo,
                                QuickFixFactory.getInstance().createAddToDependencyInjectionAnnotationsFix(field.getProject(), annoName, "fields"));
      return true;
    });
    QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createRemoveUnusedVariableFix(field), myDeadCodeKey);
    QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createCreateGetterOrSetterFix(true, false, field), myDeadCodeKey);
    QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createCreateGetterOrSetterFix(false, true, field), myDeadCodeKey);
    QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createCreateGetterOrSetterFix(true, true, field), myDeadCodeKey);
    return highlightInfo;
  }

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
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
                                         @NotNull PsiIdentifier identifier,
                                         @NotNull ProgressIndicator progress) {
    PsiElement declarationScope = parameter.getDeclarationScope();
    if (declarationScope instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)declarationScope;
      if (PsiUtilCore.hasErrorElementChild(method)) return null;
      if ((method.isConstructor() ||
           method.hasModifierProperty(PsiModifier.PRIVATE) ||
           method.hasModifierProperty(PsiModifier.STATIC) ||
           !method.hasModifierProperty(PsiModifier.ABSTRACT) &&
           !isOverriddenOrOverrides(method)) &&
          !method.hasModifierProperty(PsiModifier.NATIVE) &&
          !JavaHighlightUtil.isSerializationRelatedMethod(method, method.getContainingClass()) &&
          !PsiClassImplUtil.isMainOrPremainMethod(method)) {
        if (UnusedSymbolUtil.isInjected(project, method)) return null;
        HighlightInfo highlightInfo = checkUnusedParameter(parameter, identifier, progress);
        if (highlightInfo != null) {
          QuickFixFactory.getInstance().registerFixesForUnusedParameter(parameter, highlightInfo);
          return highlightInfo;
        }
      }
    }
    else if (declarationScope instanceof PsiForeachStatement && !PsiUtil.isIgnoredName(parameter.getName())) {
      HighlightInfo highlightInfo = checkUnusedParameter(parameter, identifier, progress);
      if (highlightInfo != null) {
        QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createRenameToIgnoredFix(parameter), myDeadCodeKey);
        return highlightInfo;
      }
    }

    return null;
  }

  @Nullable
  private HighlightInfo checkUnusedParameter(@NotNull PsiParameter parameter,
                                             @NotNull PsiIdentifier identifier,
                                             @NotNull ProgressIndicator progress) {
    if (!myRefCountHolder.isReferenced(parameter) && !UnusedSymbolUtil.isImplicitUsage(myProject, parameter, progress)) {
      String message = JavaErrorMessages.message("parameter.is.not.used", identifier.getText());
      return UnusedSymbolUtil.createUnusedSymbolInfo(identifier, message, myDeadCodeInfoType);
    }
    return null;
  }

  @Nullable
  private HighlightInfo processMethod(@NotNull final Project project,
                                      @NotNull final PsiMethod method,
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
    String symbolName = HighlightMessageUtil.getSymbolName(method, PsiSubstitutor.EMPTY);
    String message = JavaErrorMessages.message(key, symbolName);
    final HighlightInfo highlightInfo = UnusedSymbolUtil.createUnusedSymbolInfo(identifier, message, myDeadCodeInfoType);
    QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createSafeDeleteFix(method), myDeadCodeKey);
    SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes(method, annoName -> {
      IntentionAction fix = QuickFixFactory.getInstance().createAddToDependencyInjectionAnnotationsFix(project, annoName, "methods");
      QuickFixAction.registerQuickFixAction(highlightInfo, fix);
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
    return formatUnusedSymbolHighlightInfo(myProject, pattern, aClass, "classes", myDeadCodeKey, myDeadCodeInfoType, identifier);
  }


  private static HighlightInfo formatUnusedSymbolHighlightInfo(@NotNull final Project project,
                                                               @NotNull @PropertyKey(resourceBundle = JavaErrorMessages.BUNDLE) String pattern,
                                                               @NotNull final PsiNameIdentifierOwner aClass,
                                                               @NotNull final String element,
                                                               HighlightDisplayKey highlightDisplayKey,
                                                               @NotNull HighlightInfoType highlightInfoType,
                                                               @NotNull PsiElement identifier) {
    String symbolName = aClass.getName();
    String message = JavaErrorMessages.message(pattern, symbolName);
    final HighlightInfo highlightInfo = UnusedSymbolUtil.createUnusedSymbolInfo(identifier, message, highlightInfoType);
    QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createSafeDeleteFix(aClass), highlightDisplayKey);
    SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes((PsiModifierListOwner)aClass, annoName -> {
      QuickFixAction
        .registerQuickFixAction(highlightInfo,
                                QuickFixFactory.getInstance().createAddToDependencyInjectionAnnotationsFix(project, annoName, element));
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
      myHasMissortedImports = true;
    }
    myCurrentEntryIndex = entryIndex;

    return null;
  }

  private HighlightInfo registerRedundantImport(@NotNull PsiImportStatementBase importStatement, @NotNull HighlightDisplayKey unusedImportKey) {
    VirtualFile file = PsiUtilCore.getVirtualFile(myFile);
    Set<String> imports = file != null ? file.getCopyableUserData(ImportsHighlightUtil.IMPORTS_FROM_TEMPLATE) : null;
    boolean predefinedImport = imports != null && imports.contains(importStatement.getText());
    String description = !predefinedImport ? InspectionsBundle.message("unused.import.statement") : "Unused import (specified in template)";
    HighlightInfo info =
      HighlightInfo.newHighlightInfo(JavaHighlightInfoTypes.UNUSED_IMPORT).range(importStatement).descriptionAndTooltip(description)
        .create();

    QuickFixAction.registerQuickFixAction(info, QuickFixFactory.getInstance().createOptimizeImportsFix(false), unusedImportKey);
    QuickFixAction.registerQuickFixAction(info, QuickFixFactory.getInstance().createEnableOptimizeImportsOnTheFlyFix(), unusedImportKey);
    if (!predefinedImport) myHasRedundantImports = true;
    return info;
  }
}
