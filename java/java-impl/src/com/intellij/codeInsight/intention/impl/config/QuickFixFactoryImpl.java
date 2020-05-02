// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.CodeInsightWorkspaceSettings;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.daemon.impl.analysis.IncreaseLanguageLevelFix;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.daemon.quickFix.CreateClassOrPackageFix;
import com.intellij.codeInsight.daemon.quickFix.CreateFieldOrPropertyFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.*;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspectionBase;
import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.diagnostic.AttachmentFactory;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.java.request.CreateConstructorFromUsage;
import com.intellij.lang.java.request.CreateMethodFromUsage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ClassKind;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyMemberType;
import com.intellij.refactoring.memberPushDown.JavaPushDownHandler;
import com.intellij.util.DocumentUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.fixes.CreateDefaultBranchFix;
import com.siyeh.ig.fixes.CreateMissingSwitchBranchesFix;
import com.siyeh.ipp.modifiers.ChangeModifierIntention;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author cdr
 */
public class QuickFixFactoryImpl extends QuickFixFactory {
  private static final Logger LOG = Logger.getInstance(QuickFixFactoryImpl.class);

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createModifierListFix(@NotNull PsiModifierList modifierList,
                                                                           @NotNull String modifier,
                                                                           boolean shouldHave,
                                                                           boolean showContainingClass) {
    return new ModifierFix(modifierList, modifier, shouldHave,showContainingClass);
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createModifierListFix(@NotNull PsiModifierListOwner owner,
                                                                           @NotNull final String modifier,
                                                                           final boolean shouldHave,
                                                                           final boolean showContainingClass) {
    return new ModifierFix(owner, modifier, shouldHave, showContainingClass);
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createMethodReturnFix(@NotNull PsiMethod method,
                                                                           @NotNull PsiType toReturn,
                                                                           boolean fixWholeHierarchy) {
    return new MethodReturnTypeFix(method, toReturn, fixWholeHierarchy);
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createMethodReturnFix(@NotNull PsiMethod method,
                                                                           @NotNull PsiType toReturn,
                                                                           boolean fixWholeHierarchy,
                                                                           boolean suggestSuperTypes) {
    return new MethodReturnTypeFix(method, toReturn, fixWholeHierarchy, suggestSuperTypes);
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createAddMethodFix(@NotNull PsiMethod method, @NotNull PsiClass toClass) {
    return new AddMethodFix(method, toClass);
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createAddMethodFix(@NotNull String methodText,
                                                                        @NotNull PsiClass toClass,
                                                                        String @NotNull ... exceptions) {
    return new AddMethodFix(methodText, toClass, exceptions);
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createImplementMethodsFix(@NotNull PsiClass aClass) {
    return new ImplementMethodsFix(aClass);
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createImplementMethodsFix(@NotNull PsiElement psiElement) {
    return new ImplementMethodsFix(psiElement);
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createAssignmentToComparisonFix(@NotNull PsiAssignmentExpression expr) {
    return new ReplaceAssignmentWithComparisonFix(expr);
  }

  @NotNull
  @Override
  public LocalQuickFixOnPsiElement createMethodThrowsFix(@NotNull PsiMethod method,
                                                         @NotNull PsiClassType exceptionClass,
                                                         boolean shouldThrow,
                                                         boolean showContainingClass) {
    return shouldThrow ? new MethodThrowsFix.Add(method, exceptionClass, showContainingClass) : new MethodThrowsFix.Remove(method, exceptionClass, showContainingClass);
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createAddDefaultConstructorFix(@NotNull PsiClass aClass) {
    return new AddDefaultConstructorFix(aClass);
  }

  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createAddConstructorFix(@NotNull PsiClass aClass, @NotNull String modifier) {
    return aClass.getName() != null ? new AddDefaultConstructorFix(aClass, modifier) : null;
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createMethodParameterTypeFix(@NotNull PsiMethod method,
                                                                                  int index,
                                                                                  @NotNull PsiType newType,
                                                                                  boolean fixWholeHierarchy) {
    return new MethodParameterFix(method, newType, index, fixWholeHierarchy);
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createMakeClassInterfaceFix(@NotNull PsiClass aClass, final boolean makeInterface) {
    return new MakeClassInterfaceFix(aClass, makeInterface);
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createExtendsListFix(@NotNull PsiClass aClass,
                                                                          @NotNull PsiClassType typeToExtendFrom,
                                                                          boolean toAdd) {
    return new ExtendsListFix(aClass, typeToExtendFrom, toAdd);
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createRemoveUnusedParameterFix(@NotNull PsiParameter parameter) {
    return new RemoveUnusedParameterFix(parameter);
  }

  @NotNull
  @Override
  public IntentionAction createRemoveUnusedVariableFix(@NotNull PsiVariable variable) {
    return new RemoveUnusedVariableFix(variable);
  }

  @Override
  @Nullable
  public IntentionAction createCreateClassOrPackageFix(@NotNull final PsiElement context, @NotNull final String qualifiedName, final boolean createClass, final String superClass) {
    return CreateClassOrPackageFix.createFix(qualifiedName, context, createClass ? ClassKind.CLASS : null, superClass);
  }

  @Override
  @Nullable
  public IntentionAction createCreateClassOrInterfaceFix(@NotNull final PsiElement context, @NotNull final String qualifiedName, final boolean createClass, final String superClass) {
    return CreateClassOrPackageFix.createFix(qualifiedName, context, createClass ? ClassKind.CLASS : ClassKind.INTERFACE, superClass);
  }

  @NotNull
  @Override
  public IntentionAction createCreateFieldOrPropertyFix(@NotNull final PsiClass aClass, @NotNull final String name, @NotNull final PsiType type, @NotNull final PropertyMemberType targetMember, final PsiAnnotation @NotNull ... annotations) {
    return new CreateFieldOrPropertyFix(aClass, name, type, targetMember, annotations);
  }

  @NotNull
  @Override
  public IntentionAction createAddExceptionToCatchFix() {
    return new AddExceptionToCatchFix(true);
  }

  @NotNull
  @Override
  public IntentionAction createAddExceptionToThrowsFix(@NotNull PsiElement element) {
    return new AddExceptionToThrowsFix(element);
  }

  @NotNull
  @Override
  public IntentionAction createAddExceptionFromFieldInitializerToConstructorThrowsFix(@NotNull PsiElement element) {
    return new AddExceptionFromFieldInitializerToConstructorThrowsFix(element);
  }

  @NotNull
  @Override
  public IntentionAction createSurroundWithTryCatchFix(@NotNull PsiElement element) {
    return new SurroundWithTryCatchFix(element);
  }

  @NotNull
  @Override
  public IntentionAction createAddExceptionToExistingCatch(@NotNull PsiElement element) {
    return new AddExceptionToExistingCatchFix(element);
  }

  @NotNull
  @Override
  public IntentionAction createChangeToAppendFix(@NotNull IElementType sign,
                                                 @NotNull PsiType type,
                                                 @NotNull PsiAssignmentExpression assignment) {
    return new ChangeToAppendFix(sign, type, assignment);
  }

  @NotNull
  @Override
  public IntentionAction createAddTypeCastFix(@NotNull PsiType type, @NotNull PsiExpression expression) {
    return new AddTypeCastFix(type, expression);
  }

  @NotNull
  @Override
  public IntentionAction createWrapExpressionFix(@NotNull PsiType type, @NotNull PsiExpression expression) {
    return new WrapExpressionFix(type, expression);
  }

  @NotNull
  @Override
  public IntentionAction createReuseVariableDeclarationFix(@NotNull PsiLocalVariable variable) {
    return new ReuseVariableDeclarationFix(variable);
  }

  @NotNull
  @Override
  public IntentionAction createNavigateToAlreadyDeclaredVariableFix(@NotNull PsiVariable variable) {
    return new NavigateToAlreadyDeclaredVariableFix(variable);
  }

  @NotNull
  @Override
  public IntentionAction createConvertToStringLiteralAction() {
    return new ConvertToStringLiteralAction();
  }

  @NotNull
  @Override
  public IntentionAction createDeleteReturnFix(@NotNull PsiMethod method, @NotNull PsiReturnStatement returnStatement) {
    return new DeleteReturnFix(method, returnStatement);
  }

  @NotNull
  @Override
  public IntentionAction createDeleteCatchFix(@NotNull PsiParameter parameter) {
    return new DeleteCatchFix(parameter);
  }

  @NotNull
  @Override
  public IntentionAction createDeleteMultiCatchFix(@NotNull PsiTypeElement element) {
    return new DeleteMultiCatchFix(element);
  }

  @NotNull
  @Override
  public IntentionAction createConvertSwitchToIfIntention(@NotNull PsiSwitchStatement statement) {
    return new ConvertSwitchToIfIntention(statement);
  }

  @NotNull
  @Override
  public IntentionAction createNegationBroadScopeFix(@NotNull PsiPrefixExpression expr) {
    return new NegationBroadScopeFix(expr);
  }

  @NotNull
  @Override
  public IntentionAction createCreateFieldFromUsageFix(@NotNull PsiReferenceExpression place) {
    return new CreateFieldFromUsageFix(place);
  }

  @NotNull
  @Override
  public IntentionAction createReplaceWithListAccessFix(@NotNull PsiArrayAccessExpression expression) {
    return new ReplaceWithListAccessFix(expression);
  }

  @NotNull
  @Override
  public IntentionAction createAddNewArrayExpressionFix(@NotNull PsiArrayInitializerExpression expression) {
    return new AddNewArrayExpressionFix(expression);
  }

  @NotNull
  @Override
  public IntentionAction createMoveCatchUpFix(@NotNull PsiCatchSection section, @NotNull PsiCatchSection section1) {
    return new MoveCatchUpFix(section, section1);
  }

  @NotNull
  @Override
  public IntentionAction createRenameWrongRefFix(@NotNull PsiReferenceExpression ref) {
    return new RenameWrongRefFix(ref);
  }

  @NotNull
  @Override
  public IntentionAction createRemoveQualifierFix(@NotNull PsiExpression qualifier,
                                                  @NotNull PsiReferenceExpression expression,
                                                  @NotNull PsiClass resolved) {
    return new RemoveQualifierFix(qualifier, expression, resolved);
  }

  @NotNull
  @Override
  public IntentionAction createRemoveParameterListFix(@NotNull PsiMethod parent) {
    return new RemoveParameterListFix(parent);
  }

  @NotNull
  @Override
  public IntentionAction createShowModulePropertiesFix(@NotNull PsiElement element) {
    return new ShowModulePropertiesFix(element);
  }
  @NotNull
  @Override
  public IntentionAction createShowModulePropertiesFix(@NotNull Module module) {
    return new ShowModulePropertiesFix(module);
  }

  @NotNull
  @Override
  public IntentionAction createIncreaseLanguageLevelFix(@NotNull LanguageLevel level) {
    return new IncreaseLanguageLevelFix(level);
  }

  @NotNull
  @Override
  public IntentionAction createChangeParameterClassFix(@NotNull PsiClass aClass, @NotNull PsiClassType type) {
    return new ChangeParameterClassFix(aClass, type);
  }

  @NotNull
  @Override
  public IntentionAction createReplaceInaccessibleFieldWithGetterSetterFix(@NotNull PsiElement element, @NotNull PsiMethod getter, boolean isSetter) {
    return new ReplaceInaccessibleFieldWithGetterSetterFix(element, getter, isSetter);
  }

  @NotNull
  @Override
  public IntentionAction createSurroundWithArrayFix(@Nullable PsiCall methodCall, @Nullable PsiExpression expression) {
    return new SurroundWithArrayFix(methodCall, expression);
  }

  @NotNull
  @Override
  public IntentionAction createImplementAbstractClassMethodsFix(@NotNull PsiElement elementToHighlight) {
    return new ImplementAbstractClassMethodsFix(elementToHighlight);
  }

  @NotNull
  @Override
  public IntentionAction createMoveClassToSeparateFileFix(@NotNull PsiClass aClass) {
    return new MoveClassToSeparateFileFix(aClass);
  }

  @NotNull
  @Override
  public IntentionAction createRenameFileFix(@NotNull String newName) {
    return new RenameFileFix(newName);
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createRenameElementFix(@NotNull PsiNamedElement element) {
    return new RenameElementFix(element);
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createRenameElementFix(@NotNull PsiNamedElement element, @NotNull String newName) {
    return new RenameElementFix(element, newName);
  }

  @NotNull
  @Override
  public IntentionAction createChangeExtendsToImplementsFix(@NotNull PsiClass aClass, @NotNull PsiClassType classToExtendFrom) {
    return new ChangeExtendsToImplementsFix(aClass, classToExtendFrom);
  }

  @NotNull
  @Override
  public IntentionAction createCreateConstructorMatchingSuperFix(@NotNull PsiClass aClass) {
    return new CreateConstructorMatchingSuperFix(aClass);
  }

  @NotNull
  @Override
  public IntentionAction createRemoveNewQualifierFix(@NotNull PsiNewExpression expression, PsiClass aClass) {
    return new RemoveNewQualifierFix(expression, aClass);
  }

  @NotNull
  @Override
  public IntentionAction createSuperMethodReturnFix(@NotNull PsiMethod superMethod, @NotNull PsiType superMethodType) {
    return new SuperMethodReturnFix(superMethod, superMethodType);
  }

  @NotNull
  @Override
  public IntentionAction createInsertNewFix(@NotNull PsiMethodCallExpression call, @NotNull PsiClass aClass) {
    return new InsertNewFix(call, aClass);
  }

  @NotNull
  @Override
  public IntentionAction createAddMethodBodyFix(@NotNull PsiMethod method) {
    return new AddMethodBodyFix(method);
  }

  @NotNull
  @Override
  public IntentionAction createDeleteMethodBodyFix(@NotNull PsiMethod method) {
    return new DeleteMethodBodyFix(method);
  }

  @NotNull
  @Override
  public IntentionAction createInsertSuperFix(@NotNull PsiMethod constructor) {
    return new InsertSuperFix(constructor);
  }

  @NotNull
  @Override
  public IntentionAction createInsertThisFix(@NotNull PsiMethod constructor) {
    return new InsertThisFix(constructor);
  }

  @NotNull
  @Override
  public IntentionAction createChangeMethodSignatureFromUsageFix(@NotNull PsiMethod targetMethod,
                                                                 PsiExpression @NotNull [] expressions,
                                                                 @NotNull PsiSubstitutor substitutor,
                                                                 @NotNull PsiElement context,
                                                                 boolean changeAllUsages,
                                                                 int minUsagesNumberToShowDialog) {
    return new ChangeMethodSignatureFromUsageFix(targetMethod, expressions, substitutor, context, changeAllUsages, minUsagesNumberToShowDialog);
  }

  @NotNull
  @Override
  public IntentionAction createChangeMethodSignatureFromUsageReverseOrderFix(@NotNull PsiMethod targetMethod,
                                                                             PsiExpression @NotNull [] expressions,
                                                                             @NotNull PsiSubstitutor substitutor,
                                                                             @NotNull PsiElement context,
                                                                             boolean changeAllUsages,
                                                                             int minUsagesNumberToShowDialog) {
    return new ChangeMethodSignatureFromUsageReverseOrderFix(targetMethod, expressions, substitutor, context, changeAllUsages, minUsagesNumberToShowDialog);
  }

  @NotNull
  @Override
  public List<IntentionAction> createCreateMethodFromUsageFixes(@NotNull PsiMethodCallExpression call) {
    return CreateMethodFromUsage.generateActions(call);
  }

  @NotNull
  @Override
  public IntentionAction createCreateMethodFromUsageFix(@NotNull PsiMethodReferenceExpression methodReferenceExpression) {
    return new CreateMethodFromMethodReferenceFix(methodReferenceExpression);
  }

  @NotNull
  @Override
  public List<IntentionAction> createCreateConstructorFromCallExpressionFixes(@NotNull PsiMethodCallExpression call) {
    return CreateConstructorFromUsage.generateConstructorActions(call);
  }

  @NotNull
  @Override
  public IntentionAction createStaticImportMethodFix(@NotNull PsiMethodCallExpression call) {
    return new StaticImportMethodFix(call.getContainingFile(), call);
  }

  @NotNull
  @Override
  public IntentionAction createQualifyStaticMethodCallFix(@NotNull PsiMethodCallExpression call) {
    return new QualifyStaticMethodCallFix(call.getContainingFile(), call);
  }

  @NotNull
  @Override
  public IntentionAction createReplaceAddAllArrayToCollectionFix(@NotNull PsiMethodCallExpression call) {
    return new ReplaceAddAllArrayToCollectionFix(call);
  }

  @NotNull
  @Override
  public List<IntentionAction> createCreateConstructorFromUsageFixes(@NotNull PsiConstructorCall call) {
    return CreateConstructorFromUsage.generateConstructorActions(call);
  }

  @NotNull
  @Override
  public List<IntentionAction> getVariableTypeFromCallFixes(@NotNull PsiMethodCallExpression call, @NotNull PsiExpressionList list) {
    return VariableTypeFromCallFix.getQuickFixActions(call, list);
  }

  @NotNull
  @Override
  public IntentionAction createAddReturnFix(@NotNull PsiParameterListOwner methodOrLambda) {
    return new AddReturnFix(methodOrLambda);
  }

  @NotNull
  @Override
  public IntentionAction createAddVariableInitializerFix(@NotNull PsiVariable variable) {
    return new AddVariableInitializerFix(variable);
  }

  @NotNull
  @Override
  public IntentionAction createDeferFinalAssignmentFix(@NotNull PsiVariable variable, @NotNull PsiReferenceExpression expression) {
    return new DeferFinalAssignmentFix(variable, expression);
  }

  @NotNull
  @Override
  public IntentionAction createVariableAccessFromInnerClassFix(@NotNull PsiVariable variable, @NotNull PsiElement scope) {
    return new VariableAccessFromInnerClassFix(variable, scope);
  }

  @NotNull
  @Override
  public IntentionAction createCreateConstructorParameterFromFieldFix(@NotNull PsiField field) {
    return new CreateConstructorParameterFromFieldFix(field);
  }

  @NotNull
  @Override
  public IntentionAction createInitializeFinalFieldInConstructorFix(@NotNull PsiField field) {
    return new InitializeFinalFieldInConstructorFix(field);
  }

  @NotNull
  @Override
  public IntentionAction createRemoveTypeArgumentsFix(@NotNull PsiElement variable) {
    return new RemoveTypeArgumentsFix(variable);
  }

  @NotNull
  @Override
  public IntentionAction createChangeClassSignatureFromUsageFix(@NotNull PsiClass owner, @NotNull PsiReferenceParameterList parameterList) {
    return new ChangeClassSignatureFromUsageFix(owner, parameterList);
  }

  @NotNull
  @Override
  public IntentionAction createReplacePrimitiveWithBoxedTypeAction(@NotNull PsiTypeElement element, @NotNull String typeName, @NotNull String boxedTypeName) {
    return new ReplacePrimitiveWithBoxedTypeAction(element, typeName, boxedTypeName);
  }

  @NotNull
  @Override
  public IntentionAction createMakeVarargParameterLastFix(@NotNull PsiParameter parameter) {
    return new MakeVarargParameterLastFix(parameter);
  }

  @NotNull
  @Override
  public IntentionAction createMoveBoundClassToFrontFix(@NotNull PsiClass aClass, @NotNull PsiClassType type) {
    return new MoveBoundClassToFrontFix(aClass, type);
  }

  @Override
  public void registerPullAsAbstractUpFixes(@NotNull PsiMethod method, @NotNull QuickFixActionRegistrar registrar) {
    PullAsAbstractUpFix.registerQuickFix(method, registrar);
  }

  @NotNull
  @Override
  public IntentionAction createCreateAnnotationMethodFromUsageFix(@NotNull PsiNameValuePair pair) {
    return new CreateAnnotationMethodFromUsageFix(pair);
  }

  @NotNull
  @Override
  public IntentionAction createOptimizeImportsFix(final boolean onTheFly) {
    return new OptimizeImportsAction(onTheFly);
  }

  @Override
  public void registerFixesForUnusedParameter(@NotNull PsiParameter parameter, @NotNull Object highlightInfo) {
    Project myProject = parameter.getProject();
    InspectionProfile profile = InspectionProjectProfileManager.getInstance(myProject).getCurrentProfile();
    UnusedDeclarationInspectionBase unusedParametersInspection =
      (UnusedDeclarationInspectionBase)profile.getUnwrappedTool(UnusedSymbolLocalInspectionBase.SHORT_NAME, parameter);
    LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode() || unusedParametersInspection != null);
    HighlightDisplayKey myUnusedSymbolKey = HighlightDisplayKey.find(UnusedSymbolLocalInspectionBase.SHORT_NAME);
    List<IntentionAction> options =
      new ArrayList<>(IntentionManager.getInstance().getStandardIntentionOptions(myUnusedSymbolKey, parameter));
    if (unusedParametersInspection != null) {
      SuppressQuickFix[] batchSuppressActions = unusedParametersInspection.getBatchSuppressActions(parameter);
      Collections.addAll(options, SuppressIntentionActionFromFix.convertBatchToSuppressIntentionActions(batchSuppressActions));
    }
    //need suppress from Unused Parameters but settings from Unused Symbol
    QuickFixAction.registerQuickFixAction((HighlightInfo)highlightInfo, new SafeDeleteFix(parameter),
                                          options, HighlightDisplayKey.getDisplayNameByKey(myUnusedSymbolKey));
  }

  @NotNull
  @Override
  public IntentionAction createAddToDependencyInjectionAnnotationsFix(@NotNull Project project,
                                                                      @NotNull String qualifiedName) {
    final EntryPointsManagerBase entryPointsManager = EntryPointsManagerBase.getInstance(project);
    return SpecialAnnotationsUtil.createAddToSpecialAnnotationsListIntentionAction(
      QuickFixBundle.message("fix.unused.symbol.injection.text", qualifiedName),
      QuickFixBundle.message("fix.unused.symbol.injection.family"),
      entryPointsManager.ADDITIONAL_ANNOTATIONS, qualifiedName);
  }

  @NotNull
  @Override
  public IntentionAction createAddToImplicitlyWrittenFieldsFix(@NotNull Project project, @NotNull final String qualifiedName) {
    EntryPointsManagerBase entryPointsManagerBase = EntryPointsManagerBase.getInstance(project);
    return entryPointsManagerBase.new AddImplicitlyWriteAnnotation(qualifiedName);
  }

  @NotNull
  @Override
  public IntentionAction createCreateGetterOrSetterFix(boolean createGetter, boolean createSetter, @NotNull PsiField field) {
    return new CreateGetterOrSetterFix(createGetter, createSetter, field);
  }

  @NotNull
  @Override
  public IntentionAction createRenameToIgnoredFix(@NotNull PsiNamedElement namedElement) {
    return new RenameToIgnoredFix(namedElement);
  }

  @NotNull
  @Override
  public IntentionAction createEnableOptimizeImportsOnTheFlyFix() {
    return new EnableOptimizeImportsOnTheFlyFix();
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createDeleteFix(@NotNull PsiElement element) {
    return new DeleteElementFix(element);
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createDeleteFix(@NotNull PsiElement element, @Nls @NotNull String text) {
    return new DeleteElementFix(element, text);
  }

  @NotNull
  @Override
  public IntentionAction createSafeDeleteFix(@NotNull PsiElement element) {
    return new SafeDeleteFix(element);
  }

  @Nullable
  @Override
  public List<LocalQuickFix> registerOrderEntryFixes(@NotNull QuickFixActionRegistrar registrar, @NotNull PsiReference reference) {
    return OrderEntryFix.registerFixes(registrar, reference);
  }

  private static void invokeOnTheFlyImportOptimizer(@NotNull PsiFile file) {
    final Project project = file.getProject();
    final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document == null) return;

    String beforeText = file.getText();
    long oldStamp = document.getModificationStamp();
    DocumentUtil.writeInRunUndoTransparentAction(() -> JavaCodeStyleManager.getInstance(project).optimizeImports(file));
    if (oldStamp != document.getModificationStamp()) {
      String afterText = file.getText();
      if (Comparing.strEqual(beforeText, afterText)) {
        LOG.error("Import optimizer hasn't optimized any imports",
                  new Throwable(file.getViewProvider().getVirtualFile().getPath()),
                  AttachmentFactory.createAttachment(file.getViewProvider().getVirtualFile()));
      }
    }
  }

  @NotNull
  @Override
  public IntentionAction createAddMissingRequiredAnnotationParametersFix(@NotNull final PsiAnnotation annotation,
                                                                         final PsiMethod @NotNull [] annotationMethods,
                                                                         @NotNull final Collection<String> missedElements) {
    return new AddMissingRequiredAnnotationParametersFix(annotation, annotationMethods, missedElements);
  }

  @NotNull
  @Override
  public IntentionAction createSurroundWithQuotesAnnotationParameterValueFix(@NotNull PsiAnnotationMemberValue value,
                                                                             @NotNull PsiType expectedType) {
    return new SurroundWithQuotesAnnotationParameterValueFix(value, expectedType);
  }

  @NotNull
  @Override
  public IntentionAction addMethodQualifierFix(@NotNull PsiMethodCallExpression methodCall) {
    return new AddMethodQualifierFix(methodCall);
  }

  @NotNull
  @Override
  public IntentionAction createWrapWithOptionalFix(@Nullable PsiType type, @NotNull PsiExpression expression) {
    return WrapObjectWithOptionalOfNullableFix.createFix(type, expression);
  }

  @Nullable
  @Override
  public IntentionAction createNotIterableForEachLoopFix(@NotNull PsiExpression expression) {
    final PsiElement parent = expression.getParent();
    if (parent instanceof PsiForeachStatement) {
      final PsiType type = expression.getType();
      if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_ITERATOR)) {
        return new ReplaceIteratorForEachLoopWithIteratorForLoopFix((PsiForeachStatement)parent);
      }
    }
    return null;
  }

  @NotNull
  @Override
  public List<IntentionAction> createAddAnnotationAttributeNameFixes(@NotNull PsiNameValuePair pair) {
    return AddAnnotationAttributeNameFix.createFixes(pair);
  }

  private static boolean timeToOptimizeImports(@NotNull PsiFile file) {
    if (!CodeInsightWorkspaceSettings.getInstance(file.getProject()).optimizeImportsOnTheFly) return false;

    DaemonCodeAnalyzerEx codeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(file.getProject());
    // dont optimize out imports in JSP since it can be included in other JSP
    if (!codeAnalyzer.isHighlightingAvailable(file) || !(file instanceof PsiJavaFile) || file instanceof ServerPageFile) return false;

    if (!codeAnalyzer.isErrorAnalyzingFinished(file)) return false;
    boolean errors = containsErrorsPreventingOptimize(file);

    return !errors && DaemonListeners.canChangeFileSilently(file);
  }

  private static boolean containsErrorsPreventingOptimize(@NotNull PsiFile file) {
    Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    if (document == null) return true;
    // ignore unresolved imports errors
    PsiImportList importList = ((PsiJavaFile)file).getImportList();
    final TextRange importsRange = importList == null ? TextRange.EMPTY_RANGE : importList.getTextRange();
    //noinspection UnnecessaryLocalVariable
    boolean hasErrorsExceptUnresolvedImports = !DaemonCodeAnalyzerEx
      .processHighlights(document, file.getProject(), HighlightSeverity.ERROR, 0, document.getTextLength(), error -> {
        if (error.type instanceof LocalInspectionsPass.InspectionHighlightInfoType) return true;
        int infoStart = error.getActualStartOffset();
        int infoEnd = error.getActualEndOffset();

        return importsRange.containsRange(infoStart, infoEnd) && error.type.equals(HighlightInfoType.WRONG_REF);
      });

    return hasErrorsExceptUnresolvedImports;
  }

  @NotNull
  @Override
  public IntentionAction createCollectionToArrayFix(@NotNull PsiExpression collectionExpression,
                                                    @NotNull PsiExpression expressionToReplace,
                                                    @NotNull PsiArrayType arrayType) {
    return new ConvertCollectionToArrayFix(collectionExpression, expressionToReplace, arrayType);
  }

  @NotNull
  @Override
  public IntentionAction createInsertMethodCallFix(@NotNull PsiMethodCallExpression call, @NotNull PsiMethod method) {
    return new InsertMethodCallFix(call, method);
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createAccessStaticViaInstanceFix(@NotNull PsiReferenceExpression methodRef,
                                                                                      @NotNull JavaResolveResult result) {
    return new AccessStaticViaInstanceFix(methodRef, result, true);
  }

  @NotNull
  @Override
  public IntentionAction createWrapWithAdapterFix(@Nullable PsiType type, @NotNull PsiExpression expression) {
    return new WrapWithAdapterMethodCallFix(type, expression);
  }

  @NotNull
  @Override
  public IntentionAction createDeleteSideEffectAwareFix(@NotNull PsiExpressionStatement statement) {
    return new DeleteSideEffectsAwareFix(statement, statement.getExpression());
  }

  @Nullable
  @Override
  public IntentionAction createCreateClassInPackageInModuleFix(@NotNull Module module, @Nullable String packageName) {
    return CreateClassInPackageInModuleFix.createFix(module, packageName);
  }

  @NotNull
  @Override
  public IntentionAction createPushDownMethodFix() {
    return new RunRefactoringAction(new JavaPushDownHandler(), "Push method down...") {
      @NotNull
      @Override
      public Priority getPriority() {
        return Priority.NORMAL;
      }
    };
  }

  @NotNull
  @Override
  public IntentionAction createSameErasureButDifferentMethodsFix(@NotNull PsiMethod method, @NotNull PsiMethod superMethod) {
    return new SameErasureButDifferentMethodsFix(method, superMethod);
  }

  @NotNull
  @Override
  public IntentionAction createAddMissingEnumBranchesFix(@NotNull PsiSwitchBlock switchBlock, @NotNull Set<String> missingCases) {
    return new CreateMissingSwitchBranchesFix(switchBlock, missingCases);
  } 

  @NotNull
  @Override
  public IntentionAction createAddSwitchDefaultFix(@NotNull PsiSwitchBlock switchBlock, String message) {
    return new CreateDefaultBranchFix(switchBlock, message);
  }

  @Nullable
  @Override
  public IntentionAction createCollapseAnnotationsFix(@NotNull PsiAnnotation annotation) {
    return CollapseAnnotationsFix.from(annotation);
  }

  @NotNull
  @Override
  public IntentionAction createChangeModifierFix() {
    return new ChangeModifierIntention(true);
  }

  @NotNull
  @Override
  public IntentionAction createWrapSwitchRuleStatementsIntoBlockFix(PsiSwitchLabeledRuleStatement rule) {
    return new WrapSwitchRuleStatementsIntoBlockFix(rule);
  }

  @NotNull
  @Override
  public IntentionAction createAddParameterListFix(PsiMethod method) {
    return new AddParameterListFix(method);
  }

  @NotNull
  @Override
  public IntentionAction createAddEmptyRecordHeaderFix(PsiClass psiClass) {
    return new AddEmptyRecordHeaderFix(psiClass);
  }

  private static class OptimizeImportsAction implements IntentionAction {
    private final boolean myOnTheFly;

    public OptimizeImportsAction(boolean onTheFly) {myOnTheFly = onTheFly;}

    @NotNull
    @Override
    public String getText() {
      return QuickFixBundle.message("optimize.imports.fix");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return QuickFixBundle.message("optimize.imports.fix");
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return (!myOnTheFly || timeToOptimizeImports(file)) && file instanceof PsiJavaFile && BaseIntentionAction.canModify(file);
    }

    @Override
    public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
      invokeOnTheFlyImportOptimizer(file);
    }

    @Override
    public boolean startInWriteAction() {
      return true;
    }
  }
}
