// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.UnusedImportProvider;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.daemon.impl.quickfix.ReplaceWithUnnamedPatternFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.PriorityIntentionActionWrapper;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.codeInspection.unusedImport.MissortedImportsInspection;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspectionBase;
import com.intellij.codeInspection.util.SpecialAnnotationsUtilBase;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Predicates;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

class PostHighlightingVisitor extends JavaElementVisitor {
  private final RefCountHolder myRefCountHolder;
  @NotNull private final Project myProject;
  private final PsiFile myFile;
  @NotNull private final Document myDocument;
  private final GlobalUsageHelper myGlobalUsageHelper;
  private IntentionAction myOptimizeImportsFix; // when not null, there are not-optimized imports in the file
  private int myCurrentEntryIndex = -1;
  private final UnusedSymbolLocalInspectionBase myUnusedSymbolInspection;
  private final HighlightDisplayKey myDeadCodeKey;
  private final HighlightInfoType myDeadCodeInfoType;
  private boolean errorFound;

  PostHighlightingVisitor(@NotNull PsiFile file, @NotNull Document document, @NotNull RefCountHolder refCountHolder) throws ProcessCanceledException {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    myProject = file.getProject();
    myFile = file;
    myDocument = document;
    myRefCountHolder = refCountHolder;
    InspectionProfileImpl profile = InspectionProjectProfileManager.getInstance(myProject).getCurrentProfile();
    myDeadCodeKey = HighlightDisplayKey.find(UnusedDeclarationInspectionBase.SHORT_NAME);
    UnusedDeclarationInspectionBase deadCodeInspection = (UnusedDeclarationInspectionBase)profile.getUnwrappedTool(UnusedDeclarationInspectionBase.SHORT_NAME, myFile);
    myUnusedSymbolInspection = deadCodeInspection == null ? null : deadCodeInspection.getSharedLocalInspectionTool();
    myDeadCodeInfoType = myDeadCodeKey == null ? HighlightInfoType.UNUSED_SYMBOL
                         : new HighlightInfoType.HighlightInfoTypeImpl(profile.getErrorLevel(myDeadCodeKey, myFile).getSeverity(),
                            ObjectUtils.notNull(profile.getEditorAttributes(myDeadCodeKey.toString(), myFile),
                                                HighlightInfoType.UNUSED_SYMBOL.getAttributesKey()));
    myGlobalUsageHelper = myRefCountHolder.getGlobalUsageHelper(myFile, deadCodeInspection);
  }

  void collectHighlights(@NotNull HighlightInfoHolder holder) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    if (myDeadCodeKey != null && isToolEnabled(myDeadCodeKey)) {
      TextRange priorityRange = holder.getAnnotationSession().getPriorityRange();
      JavaElementVisitor identifierVisitor = new JavaElementVisitor() {
        @Override
        public void visitIdentifier(@NotNull PsiIdentifier identifier) {
          processIdentifier(holder, identifier);
        }
      };
      Divider.divideInsideAndOutsideAllRoots(myFile, myFile.getTextRange(), priorityRange, Predicates.alwaysTrue(), dividedElements -> {
        ProgressManager.checkCanceled();
        PsiFile psiRoot = dividedElements.psiRoot();
        HighlightingLevelManager highlightingLevelManager = HighlightingLevelManager.getInstance(myProject);
        if (!highlightingLevelManager.shouldInspect(psiRoot) || highlightingLevelManager.runEssentialHighlightingOnly(psiRoot)) {
          return true;
        }
        for (PsiElement element : dividedElements.inside()) {
          ProgressManager.checkCanceled();
          element.accept(identifierVisitor);
        }
        for (PsiElement element : dividedElements.outside()) {
          ProgressManager.checkCanceled();
          element.accept(identifierVisitor);
        }
        return true;
      });
    }

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
    if (!(myFile instanceof PsiJavaFile) || myUnusedSymbolInspection == null) {
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

  private String message;
  private final List<IntentionAction> quickFixes = new ArrayList<>();
  private final List<IntentionAction> quickFixOptions = new ArrayList<>();

  public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
    if (myUnusedSymbolInspection.LOCAL_VARIABLE) {
      processLocalVariable(variable);
    }
  }

  @Override
  public void visitField(@NotNull PsiField field) {
    if (compareVisibilities(field, myUnusedSymbolInspection.getFieldVisibility())) {
      processField(myProject, field);
    }
  }

  @Override
  public void visitParameter(@NotNull PsiParameter parameter) {
    PsiElement declarationScope = parameter.getDeclarationScope();
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
      processParameter(myProject, parameter);
    }
  }

  @Override
  public void visitMethod(@NotNull PsiMethod method) {
    if (myUnusedSymbolInspection.isIgnoreAccessors() && PropertyUtilBase.isSimplePropertyAccessor(method)) {
      return;
    }
    if (compareVisibilities(method, myUnusedSymbolInspection.getMethodVisibility())) {
      processMethod(myProject, method);
    }
  }

  @Override
  public void visitClass(@NotNull PsiClass aClass) {
    String acceptedVisibility = aClass.getContainingClass() == null ? myUnusedSymbolInspection.getClassVisibility()
                                                                    : myUnusedSymbolInspection.getInnerClassVisibility();
    if (compareVisibilities(aClass, acceptedVisibility)) {
      processClass(myProject, aClass);
    }
  }

  private void processIdentifier(@NotNull HighlightInfoHolder holder, @NotNull PsiIdentifier identifier) {
    PsiElement parent = identifier.getParent();
    if (parent == null) return;
    if ((parent instanceof PsiVariable || parent instanceof PsiMember) && SuppressionUtil.inspectionResultSuppressed(identifier, myUnusedSymbolInspection)) return;
    if (parent instanceof PsiParameter && SuppressionUtil.isSuppressed(identifier, UnusedSymbolLocalInspectionBase.UNUSED_PARAMETERS_SHORT_NAME)) return;

    parent.accept(this);
    if (message != null) {
      HighlightInfo.Builder builder =
        UnusedSymbolUtil.createUnusedSymbolInfoBuilder(identifier, message, myDeadCodeInfoType, UnusedDeclarationInspectionBase.SHORT_NAME);
      for (IntentionAction fix : quickFixes) {
        TextRange fixRange = parent instanceof PsiField ? HighlightMethodUtil.getFixRange(parent) : null;
        builder.registerFix(fix, null, HighlightDisplayKey.getDisplayNameByKey(myDeadCodeKey), fixRange, myDeadCodeKey);
      }
      for (IntentionAction fix : quickFixOptions) {
        TextRange fixRange = parent instanceof PsiField ? HighlightMethodUtil.getFixRange(parent) : null;
        builder.registerFix(fix, null, HighlightDisplayKey.getDisplayNameByKey(myDeadCodeKey), fixRange, null);
      }
      addInfo(holder, builder);
      message = null;
      quickFixes.clear();
      quickFixOptions.clear();
    }
    else {
      assert quickFixes.isEmpty();
      assert quickFixOptions.isEmpty();
    }
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

  private void processLocalVariable(@NotNull PsiLocalVariable variable) {
    if (variable.isUnnamed() || PsiUtil.isIgnoredName(variable.getName())) return;
    if (UnusedSymbolUtil.isImplicitUsage(myProject, variable)) return;

    if (!myRefCountHolder.isReferenced(variable)) {
      message = JavaErrorBundle.message("local.variable.is.never.used", variable.getName());
      quickFixes.add(variable instanceof PsiResourceVariable ? QuickFixFactory.getInstance().createRenameToIgnoredFix(variable, false)
                                                    : QuickFixFactory.getInstance().createRemoveUnusedVariableFix(variable));
    }

    else if (!myRefCountHolder.isReferencedForRead(variable) && !UnusedSymbolUtil.isImplicitRead(myProject, variable)) {
      message = JavaErrorBundle.message("local.variable.is.not.used.for.reading", variable.getName());
      quickFixes.add(QuickFixFactory.getInstance().createRemoveUnusedVariableFix(variable));
    }

    else if (!variable.hasInitializer() &&
        !myRefCountHolder.isReferencedForWrite(variable) &&
        !UnusedSymbolUtil.isImplicitWrite(myProject, variable)) {
      message = JavaErrorBundle.message("local.variable.is.not.assigned", variable.getName());
      quickFixes.add(QuickFixFactory.getInstance().createAddVariableInitializerFix(variable));
    }
  }

  private void processField(@NotNull Project project, @NotNull PsiField field) {
    if (HighlightUtil.isSerializationImplicitlyUsedField(field)) {
      return;
    }
    if (field.hasModifierProperty(PsiModifier.PRIVATE)) {
      if (!myRefCountHolder.isReferenced(field) && !UnusedSymbolUtil.isImplicitUsage(myProject, field)) {
        message = JavaErrorBundle.message("private.field.is.not.used", field.getName());
        suggestionsToMakeFieldUsed(field);
        if (!field.hasInitializer() && !field.hasModifierProperty(PsiModifier.FINAL)) {
          quickFixes.add(QuickFixFactory.getInstance().createCreateConstructorParameterFromFieldFix(field));
        }
        return;
      }

      boolean readReferenced = myRefCountHolder.isReferencedForRead(field);
      if (!readReferenced && !UnusedSymbolUtil.isImplicitRead(project, field)) {
        message = getNotUsedForReadingMessage(field);
        suggestionsToMakeFieldUsed(field);
        return;
      }

      if (field.hasInitializer()) {
        return;
      }
      boolean writeReferenced = myRefCountHolder.isReferencedForWrite(field);
      if (!writeReferenced && !UnusedSymbolUtil.isImplicitWrite(project, field)) {
        message = JavaErrorBundle.message("private.field.is.not.assigned", field.getName());

        quickFixes.add(QuickFixFactory.getInstance().createCreateGetterOrSetterFix(false, true, field));
        if (!field.hasModifierProperty(PsiModifier.FINAL)) {
          quickFixes.add(QuickFixFactory.getInstance().createCreateConstructorParameterFromFieldFix(field));
        }
        SpecialAnnotationsUtilBase.processUnknownAnnotations(field, annoName ->
          quickFixes.add(QuickFixFactory.getInstance().createAddToImplicitlyWrittenFieldsFix(project, annoName)));
      }
    }
    else if (!UnusedSymbolUtil.isFieldUsed(myProject, myFile, field, ProgressManager.getGlobalProgressIndicator(), myGlobalUsageHelper)) {
      if (UnusedSymbolUtil.isImplicitWrite(myProject, field)) {
        message = getNotUsedForReadingMessage(field);
        quickFixes.add(QuickFixFactory.getInstance().createSafeDeleteFix(field));
      }
      else if (!UnusedSymbolUtil.isImplicitUsage(myProject, field)) {
        formatUnusedSymbolHighlightInfo(project, "field.is.not.used", field);
      }
    }
  }

  @NotNull
  private static @NlsContexts.DetailedDescription String getNotUsedForReadingMessage(@NotNull PsiField field) {
    String visibility = VisibilityUtil.getVisibilityStringToDisplay(field);
    String message = JavaErrorBundle.message("field.is.not.used.for.reading", visibility, field.getName());
    return StringUtil.capitalize(message);
  }

  private void suggestionsToMakeFieldUsed(@NotNull PsiField field) {
    SpecialAnnotationsUtilBase.processUnknownAnnotations(field, annoName ->
      quickFixes.add(QuickFixFactory.getInstance().createAddToDependencyInjectionAnnotationsFix(field.getProject(), annoName)));
    quickFixes.add(QuickFixFactory.getInstance().createRemoveUnusedVariableFix(field));
    quickFixes.add(QuickFixFactory.getInstance().createCreateGetterOrSetterFix(true, false, field));
    quickFixes.add(QuickFixFactory.getInstance().createCreateGetterOrSetterFix(false, true, field));
    quickFixes.add(QuickFixFactory.getInstance().createCreateGetterOrSetterFix(true, true, field));
  }

  private final Map<PsiMethod, Boolean> isOverriddenOrOverrides = ConcurrentFactoryMap.createMap(method-> {
      boolean overrides = SuperMethodsSearch.search(method, null, true, false).findFirst() != null;
      return overrides || OverridingMethodsSearch.search(method).findFirst() != null;
    }
  );

  private boolean isOverriddenOrOverrides(@NotNull PsiMethod method) {
    return isOverriddenOrOverrides.get(method);
  }

  private void processParameter(@NotNull Project project, @NotNull PsiParameter parameter) {
    if (parameter.isUnnamed() || PsiUtil.isIgnoredName(parameter.getName())) return;
    PsiElement declarationScope = parameter.getDeclarationScope();
    QuickFixFactory quickFixFactory = QuickFixFactory.getInstance();
    if (declarationScope instanceof PsiMethod method) {
      if (PsiUtilCore.hasErrorElementChild(method)) return;
      if ((method.isConstructor() ||
           method.hasModifierProperty(PsiModifier.PRIVATE) ||
           method.hasModifierProperty(PsiModifier.STATIC) ||
           !method.hasModifierProperty(PsiModifier.ABSTRACT) &&
           (!isOverriddenOrOverrides(method) || myUnusedSymbolInspection.checkParameterExcludingHierarchy())) &&
          !method.hasModifierProperty(PsiModifier.NATIVE) &&
          !JavaHighlightUtil.isSerializationRelatedMethod(method, method.getContainingClass()) &&
          !isUsedMainOrPremainMethod(method)) {
        if (UnusedSymbolUtil.isInjected(project, method)) return;
        checkUnusedParameter(parameter, method);
        if (message != null) {
          quickFixes.add(quickFixFactory.createRenameToIgnoredFix(parameter, true));
          quickFixes.add(PriorityIntentionActionWrapper.highPriority(
            quickFixFactory.createSafeDeleteUnusedParameterInHierarchyFix(parameter,
                                                                          myUnusedSymbolInspection.checkParameterExcludingHierarchy() &&
                                                                          isOverriddenOrOverrides(method))));
        }
      }
    }
    else if (declarationScope instanceof PsiForeachStatement) {
      checkUnusedParameter(parameter, null);
      if (message != null) {
        quickFixes.add(quickFixFactory.createRenameToIgnoredFix(parameter, false));
      }
    }
    else if (parameter instanceof PsiPatternVariable variable) {
      checkUnusedParameter(parameter, null);
      if (message != null) {
        PsiPattern pattern = variable.getPattern();
        IntentionAction action = null;
        if (HighlightingFeature.UNNAMED_PATTERNS_AND_VARIABLES.isAvailable(parameter)) {
          if (pattern instanceof PsiTypeTestPattern ttPattern && pattern.getParent() instanceof PsiDeconstructionList) {
            PsiRecordComponent component = JavaPsiPatternUtil.getRecordComponentForPattern(pattern);
            PsiTypeElement checkType = ttPattern.getCheckType();
            if (component != null && checkType != null && checkType.getType().isAssignableFrom(component.getType())) {
              action = new ReplaceWithUnnamedPatternFix(pattern).asIntention();
            }
          }
        }
        if (action == null && declarationScope.getParent() instanceof PsiSwitchBlock) {
          action = variable.getParent() instanceof PsiDeconstructionPattern
                                   ? quickFixFactory.createDeleteFix(parameter)
                                   : quickFixFactory.createRenameToIgnoredFix(parameter, false);
        }
        else if (!(pattern instanceof PsiTypeTestPattern && pattern.getParent() instanceof PsiDeconstructionList)) {
          action = quickFixFactory.createDeleteFix(parameter);
        }
        if (action != null) {
          quickFixOptions.add(action);
        }
      }
    }
    else if ((myUnusedSymbolInspection.checkParameterExcludingHierarchy() ||
              HighlightingFeature.UNNAMED_PATTERNS_AND_VARIABLES.isAvailable(declarationScope))
             && declarationScope instanceof PsiLambdaExpression) {
      checkUnusedParameter(parameter, null);
      if (message != null) {
        quickFixes.add(quickFixFactory.createRenameToIgnoredFix(parameter, true));
        quickFixes.add(PriorityIntentionActionWrapper.lowPriority(quickFixFactory.createSafeDeleteUnusedParameterInHierarchyFix(parameter, true)));
      }
    }
  }

  private static boolean isUsedMainOrPremainMethod(@NotNull PsiMethod method) {
    if (!PsiClassImplUtil.isMainOrPremainMethod(method)) {
      return false;
    }
    //premain
    if (!"main".equals(method.getName())) {
      return true;
    }
    if (!HighlightingFeature.IMPLICIT_CLASSES.isAvailable(method)) {
      return true;
    }
    return false;
  }

  private void checkUnusedParameter(@NotNull PsiParameter parameter, @Nullable PsiMethod declarationMethod) {
    if (!myRefCountHolder.isReferenced(parameter) && !UnusedSymbolUtil.isImplicitUsage(myProject, parameter)) {
      message = JavaErrorBundle.message(parameter instanceof PsiPatternVariable ?
                                               "pattern.variable.is.not.used" : "parameter.is.not.used", parameter.getName());
      if (declarationMethod != null) {
        IntentionAction assignFix = QuickFixFactory.getInstance().createAssignFieldFromParameterFix();
        IntentionAction createFieldFix = QuickFixFactory.getInstance().createCreateFieldFromParameterFix();
        if (!declarationMethod.isConstructor()) {
          assignFix = PriorityIntentionActionWrapper.lowPriority(assignFix);
          createFieldFix = PriorityIntentionActionWrapper.lowPriority(createFieldFix);
        }
        quickFixOptions.add(assignFix);
        quickFixOptions.add(createFieldFix);
      }
    }
  }

  private void processMethod(@NotNull Project project, @NotNull PsiMethod method) {
    if (UnusedSymbolUtil.isMethodUsed(myProject, myFile, method, ProgressIndicatorProvider.getGlobalProgressIndicator(), myGlobalUsageHelper)) {
      return;
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
    message = JavaErrorBundle.message(key, symbolName);
    quickFixes.add(QuickFixFactory.getInstance().createSafeDeleteFix(method));
    SpecialAnnotationsUtilBase.processUnknownAnnotations(method, annoName ->
      quickFixes.add(QuickFixFactory.getInstance().createAddToDependencyInjectionAnnotationsFix(project, annoName)));
  }

  private void processClass(@NotNull Project project, @NotNull PsiClass aClass) {
    if (UnusedSymbolUtil.isClassUsed(project, myFile, aClass, ProgressIndicatorProvider.getGlobalProgressIndicator(), myGlobalUsageHelper)) {
      return;
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
    formatUnusedSymbolHighlightInfo(myProject, pattern, aClass);
  }


  private void formatUnusedSymbolHighlightInfo(@NotNull Project project,
                                               @NotNull @PropertyKey(resourceBundle = JavaErrorBundle.BUNDLE) String pattern,
                                               @NotNull PsiMember member) {
    String symbolName = member.getName();
    message = JavaErrorBundle.message(pattern, symbolName);
    quickFixes.add(QuickFixFactory.getInstance().createSafeDeleteFix(member));
    SpecialAnnotationsUtilBase.processUnknownAnnotations(member, annoName ->
      quickFixes.add(QuickFixFactory.getInstance().createAddToDependencyInjectionAnnotationsFix(project, annoName)));
  }

  private void processImport(@NotNull HighlightInfoHolder holder,
                             @NotNull PsiJavaFile javaFile,
                             @NotNull PsiImportStatementBase importStatement,
                             @NotNull HighlightDisplayKey unusedImportKey) {
    // jsp include directive hack
    if (importStatement.isForeignFileImport()) return;

    if (PsiUtilCore.hasErrorElementChild(importStatement)) return;

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

  private void registerRedundantImport(@NotNull HighlightInfoHolder holder,
                                       @NotNull PsiImportStatementBase importStatement, @NotNull HighlightDisplayKey unusedImportKey) {
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(myFile);
    Set<String> imports = virtualFile != null ? virtualFile.getCopyableUserData(ImportsHighlightUtil.IMPORTS_FROM_TEMPLATE) : null;
    boolean predefinedImport = imports != null && imports.contains(importStatement.getText());
    String description = !predefinedImport ? JavaAnalysisBundle.message("unused.import.statement") :
                         JavaAnalysisBundle.message("text.unused.import.in.template");
    InspectionProfile profile = getCurrentProfile(myFile);
    TextAttributesKey key = ObjectUtils.notNull(profile.getEditorAttributes(unusedImportKey.toString(), myFile),
                                                JavaHighlightInfoTypes.UNUSED_IMPORT.getAttributesKey());
    HighlightInfoType.HighlightInfoTypeImpl configHighlightType =
      new HighlightInfoType.HighlightInfoTypeImpl(profile.getErrorLevel(unusedImportKey, myFile).getSeverity(), key);

    HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(configHighlightType)
        .range(importStatement)
        .descriptionAndTooltip(description)
        .group(GeneralHighlightingPass.POST_UPDATE_ALL);

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
