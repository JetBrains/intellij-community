// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.CodeInsightWorkspaceSettings;
import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.DaemonListeners;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SilentChangeVetoer;
import com.intellij.codeInsight.daemon.impl.analysis.IncreaseLanguageLevelFix;
import com.intellij.codeInsight.daemon.impl.analysis.UpgradeSdkFix;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.daemon.impl.quickfix.makefinal.MakeVarEffectivelyFinalFix;
import com.intellij.codeInsight.daemon.quickFix.CreateClassOrPackageFix;
import com.intellij.codeInsight.daemon.quickFix.CreateFieldOrPropertyFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.*;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.actions.UnimplementInterfaceAction;
import com.intellij.codeInspection.dataFlow.fix.DeleteSwitchLabelFix;
import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.diagnostic.CoreAttachmentFactory;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.java.JavaBundle;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.java.request.CreateConstructorFromUsage;
import com.intellij.lang.java.request.CreateMethodFromUsage;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.NonPhysicalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ClassKind;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyMemberType;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.util.DocumentUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.siyeh.ig.controlflow.UnnecessaryDefaultInspection;
import com.siyeh.ig.fixes.*;
import com.siyeh.ipp.modifiers.ChangeModifierIntention;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class QuickFixFactoryImpl extends QuickFixFactory {
  private static final Logger LOG = Logger.getInstance(QuickFixFactoryImpl.class);

  @Override
  public @NotNull LocalQuickFixAndIntentionActionOnPsiElement createModifierListFix(@NotNull PsiModifierList modifierList,
                                                                                    @NotNull String modifier,
                                                                                    boolean shouldHave,
                                                                                    boolean showContainingClass) {
    return LocalQuickFixAndIntentionActionOnPsiElement.from(new ModifierFix(modifierList, modifier, shouldHave, showContainingClass),
                                                            modifierList.getParent());
  }

  @Override
  public @NotNull LocalQuickFixAndIntentionActionOnPsiElement createModifierListFix(@NotNull PsiModifierListOwner owner,
                                                                                    final @NotNull String modifier,
                                                                                    final boolean shouldHave,
                                                                                    final boolean showContainingClass) {
    return LocalQuickFixAndIntentionActionOnPsiElement.from(new ModifierFix(owner, modifier, shouldHave, showContainingClass), owner);
  }

  @Override
  public @NotNull LocalQuickFixAndIntentionActionOnPsiElement createMethodReturnFix(@NotNull PsiMethod method,
                                                                                    @NotNull PsiType toReturn,
                                                                                    boolean fixWholeHierarchy) {
    return new MethodReturnTypeFix(method, toReturn, fixWholeHierarchy);
  }

  @Override
  public @NotNull LocalQuickFixAndIntentionActionOnPsiElement createMethodReturnFix(@NotNull PsiMethod method,
                                                                                    @NotNull PsiType toReturn,
                                                                                    boolean fixWholeHierarchy,
                                                                                    boolean suggestSuperTypes) {
    return new MethodReturnTypeFix(method, toReturn, fixWholeHierarchy, suggestSuperTypes);
  }

  @Override
  public @NotNull LocalQuickFixAndIntentionActionOnPsiElement createAnnotationMethodReturnFix(@NotNull PsiMethod method,
                                                                                              @NotNull PsiType toReturn,
                                                                                              boolean fromDefaultValue) {
    return new AnnotationMethodReturnTypeFix(method, toReturn, fromDefaultValue);
  }

  @Override
  public @NotNull LocalQuickFixAndIntentionActionOnPsiElement createImplementMethodsFix(@NotNull PsiClass aClass) {
    return new ImplementMethodsFix(aClass);
  }

  @Override
  public @NotNull LocalQuickFixAndIntentionActionOnPsiElement createImplementMethodsFix(@NotNull PsiElement psiElement) {
    return new ImplementMethodsFix(psiElement);
  }

  @Override
  public @NotNull IntentionAction createAssignmentToComparisonFix(@NotNull PsiAssignmentExpression expr) {
    return new ReplaceAssignmentWithComparisonFix(expr).asIntention();
  }

  @Override
  public @NotNull LocalQuickFixAndIntentionActionOnPsiElement createMethodThrowsFix(@NotNull PsiMethod method,
                                                                                    @NotNull PsiClassType exceptionClass,
                                                                                    boolean shouldThrow,
                                                                                    boolean showContainingClass) {
    ModCommandAction action = shouldThrow
                              ? new MethodThrowsFix.Add(method, exceptionClass, showContainingClass)
                              : new MethodThrowsFix.Remove(method, exceptionClass, showContainingClass);
    return LocalQuickFixAndIntentionActionOnPsiElement.from(action, method);
  }

  @Override
  public @NotNull LocalQuickFixAndIntentionActionOnPsiElement createAddDefaultConstructorFix(@NotNull PsiClass aClass) {
    return LocalQuickFixAndIntentionActionOnPsiElement.from(new AddDefaultConstructorFix(aClass), aClass);
  }

  @Override
  public @NotNull LocalQuickFixAndIntentionActionOnPsiElement createMethodParameterTypeFix(@NotNull PsiMethod method,
                                                                                           int index,
                                                                                           @NotNull PsiType newType,
                                                                                           boolean fixWholeHierarchy) {
    return new MethodParameterFix(method, newType, index, fixWholeHierarchy);
  }

  @Override
  public @NotNull LocalQuickFixAndIntentionActionOnPsiElement createExtendsListFix(@NotNull PsiClass aClass,
                                                                                   @NotNull PsiClassType typeToExtendFrom,
                                                                                   boolean toAdd) {
    return new ExtendsListFix(aClass, typeToExtendFrom, toAdd);
  }

  @Override
  public @NotNull LocalQuickFixAndIntentionActionOnPsiElement createRemoveUnusedParameterFix(@NotNull PsiParameter parameter) {
    return new RemoveUnusedParameterFix(parameter);
  }

  @Override
  public @NotNull IntentionAction createRemoveUnusedVariableFix(@NotNull PsiVariable variable) {
    return new RemoveUnusedVariableFix(variable).asIntention();
  }

  @Override
  public @Nullable IntentionAction createCreateClassOrPackageFix(@NotNull PsiElement context,
                                                                 @NotNull String qualifiedName,
                                                                 boolean createClass,
                                                                 String superClass) {
    return CreateClassOrPackageFix.createFix(qualifiedName, context, createClass ? ClassKind.CLASS : null, superClass);
  }

  @Override
  public @Nullable IntentionAction createCreateClassOrInterfaceFix(@NotNull PsiElement context,
                                                                   @NotNull String qualifiedName,
                                                                   boolean createClass,
                                                                   String superClass) {
    return CreateClassOrPackageFix.createFix(qualifiedName, context, createClass ? ClassKind.CLASS : ClassKind.INTERFACE, superClass);
  }

  @Override
  public @NotNull IntentionAction createCreateFieldOrPropertyFix(@NotNull PsiClass aClass,
                                                                 @NotNull String name,
                                                                 @NotNull PsiType type,
                                                                 @NotNull PropertyMemberType targetMember,
                                                                 PsiAnnotation @NotNull ... annotations) {
    return new CreateFieldOrPropertyFix(aClass, name, type, targetMember, annotations).asIntention();
  }

  @Override
  public @NotNull IntentionAction createAddExceptionToCatchFix() {
    return new AddExceptionToCatchFix(true).asIntention();
  }

  @Override
  public @NotNull IntentionAction createAddExceptionToThrowsFix(@NotNull PsiElement element) {
    return new AddExceptionToThrowsFix(element).asIntention();
  }

  @Override
  public @NotNull IntentionAction createAddExceptionFromFieldInitializerToConstructorThrowsFix(@NotNull PsiElement element) {
    return new AddExceptionFromFieldInitializerToConstructorThrowsFix(element).asIntention();
  }

  @Override
  public @NotNull IntentionAction createSurroundWithTryCatchFix(@NotNull PsiElement element) {
    return new SurroundWithTryCatchFix(element).asIntention();
  }

  @Override
  public @NotNull IntentionAction createAddExceptionToExistingCatch(@NotNull PsiElement element) {
    return new AddExceptionToExistingCatchFix(element).asIntention();
  }

  @Override
  public @NotNull IntentionAction createChangeToAppendFix(@NotNull IElementType sign,
                                                          @NotNull PsiType type,
                                                          @NotNull PsiAssignmentExpression assignment) {
    return new ChangeToAppendFix(sign, type, assignment).asIntention();
  }

  @Override
  public @NotNull IntentionAction createAddTypeCastFix(@NotNull PsiType type, @NotNull PsiExpression expression) {
    return new AddTypeCastFix(type, expression).asIntention();
  }

  @Override
  public @NotNull IntentionAction createReuseVariableDeclarationFix(@NotNull PsiLocalVariable variable) {
    return new ReuseVariableDeclarationFix(variable).asIntention();
  }

  @Override
  public @NotNull IntentionAction createNavigateToAlreadyDeclaredVariableFix(@NotNull PsiVariable variable) {
    return new NavigateToAlreadyDeclaredVariableFix(variable).asIntention();
  }

  @Override
  public @NotNull IntentionAction createNavigateToDuplicateElementFix(@NotNull NavigatablePsiElement element) {
    return new NavigateToDuplicateElementFix(element).asIntention();
  }

  @Override
  public @NotNull IntentionAction createConvertToStringLiteralAction() {
    return new ConvertToStringLiteralAction().asIntention();
  }

  @Override
  public @NotNull IntentionAction createDeleteReturnFix(@NotNull PsiMethod method, @NotNull PsiReturnStatement returnStatement) {
    return new DeleteReturnFix(method, returnStatement).asIntention();
  }

  @Override
  public @NotNull IntentionAction createDeleteCatchFix(@NotNull PsiParameter parameter) {
    return new DeleteCatchFix(parameter).asIntention();
  }

  @Override
  public @NotNull IntentionAction createDeleteMultiCatchFix(@NotNull PsiTypeElement element) {
    return new DeleteMultiCatchFix(element).asIntention();
  }

  @Override
  public @NotNull IntentionAction createConvertSwitchToIfIntention(@NotNull PsiSwitchStatement statement) {
    return new ConvertSwitchToIfIntention(statement).asIntention();
  }

  @Override
  public @NotNull IntentionAction createNegationBroadScopeFix(@NotNull PsiPrefixExpression expr) {
    return new NegationBroadScopeFix(expr).asIntention();
  }

  @Override
  public @NotNull IntentionAction createCreateFieldFromUsageFix(@NotNull PsiReferenceExpression place) {
    return new CreateFieldFromUsageFix(place);
  }

  @Override
  public @NotNull IntentionAction createReplaceWithListAccessFix(@NotNull PsiArrayAccessExpression expression) {
    return new ReplaceWithListAccessFix(expression).asIntention();
  }

  @Override
  public @NotNull IntentionAction createAddNewArrayExpressionFix(@NotNull PsiArrayInitializerExpression expression) {
    return new AddNewArrayExpressionFix(expression).asIntention();
  }

  @Override
  public @NotNull IntentionAction createMoveCatchUpFix(@NotNull PsiCatchSection section, @NotNull PsiCatchSection section1) {
    return new MoveCatchUpFix(section, section1).asIntention();
  }

  @Override
  public @NotNull IntentionAction createRenameWrongRefFix(@NotNull PsiReferenceExpression ref) {
    return new RenameWrongRefFix(ref);
  }

  @Override
  public @NotNull IntentionAction createRemoveQualifierFix(@NotNull PsiExpression qualifier,
                                                           @NotNull PsiReferenceExpression expression,
                                                           @NotNull PsiClass resolved) {
    return new RemoveQualifierFix(expression, resolved).asIntention();
  }

  @Override
  public @NotNull IntentionAction createRemoveParameterListFix(@NotNull PsiMethod parent) {
    return new RemoveParameterListFix(parent).asIntention();
  }

  @Override
  public @NotNull IntentionAction createShowModulePropertiesFix(@NotNull PsiElement element) {
    return new ShowModulePropertiesFix(element);
  }
  @Override
  public @NotNull IntentionAction createShowModulePropertiesFix(@NotNull Module module) {
    return new ShowModulePropertiesFix(module);
  }

  @Override
  public @NotNull IntentionAction createIncreaseLanguageLevelFix(@NotNull LanguageLevel level) {
    return new IncreaseLanguageLevelFix(level);
  }

  @Override
  public @NotNull IntentionAction createUpgradeSdkFor(@NotNull LanguageLevel level) {
    return new UpgradeSdkFix(level);
  }

  @Override
  public @NotNull IntentionAction createChangeParameterClassFix(@NotNull PsiClass aClass, @NotNull PsiClassType type) {
    return new ChangeParameterClassFix(aClass, type);
  }

  @Override
  public @NotNull IntentionAction createReplaceInaccessibleFieldWithGetterSetterFix(@NotNull PsiReferenceExpression element,
                                                                                    @NotNull PsiMethod getter,
                                                                                    boolean isSetter) {
    return new ReplaceInaccessibleFieldWithGetterSetterFix(element, getter, isSetter).asIntention();
  }

  @Override
  public @NotNull IntentionAction createSurroundWithArrayFix(@Nullable PsiCall methodCall, @Nullable PsiExpression expression) {
    return new SurroundWithArrayFix(methodCall, expression).asIntention();
  }

  @Override
  public @NotNull IntentionAction createImplementAbstractClassMethodsFix(@NotNull PsiElement elementToHighlight) {
    return new ImplementAbstractClassMethodsFix(elementToHighlight);
  }

  @Override
  public @NotNull IntentionAction createMoveClassToSeparateFileFix(@NotNull PsiClass aClass) {
    return new MoveClassToSeparateFileFix(aClass).asIntention();
  }

  @Override
  public @NotNull IntentionAction createRenameFileFix(@NotNull String newName) {
    return new RenameFileFix(newName);
  }

  @Override
  public @Nullable IntentionAction createRenameFix(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (file == null) return null;
    if(!element.isPhysical()) return null;
    ProblemDescriptor descriptor = new ProblemDescriptorBase(element,
                                         element,
                                         "",
                                         null,
                                         ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                         false,
                                         null,
                                         true,
                                         false);
    return QuickFixWrapper.wrap(descriptor, new RenameFix());
  }

  @Override
  public @NotNull LocalQuickFixAndIntentionActionOnPsiElement createRenameElementFix(@NotNull PsiNamedElement element) {
    return new RenameElementFix(element);
  }

  @Override
  public @NotNull LocalQuickFixAndIntentionActionOnPsiElement createRenameElementFix(@NotNull PsiNamedElement element, @NotNull String newName) {
    return new RenameElementFix(element, newName);
  }

  @Override
  public @NotNull IntentionAction createChangeExtendsToImplementsFix(@NotNull PsiClass aClass, @NotNull PsiClassType classToExtendFrom) {
    return new ChangeExtendsToImplementsFix(aClass, classToExtendFrom).asIntention();
  }

  @Override
  public @NotNull IntentionAction createCreateConstructorMatchingSuperFix(@NotNull PsiClass aClass) {
    return new CreateConstructorMatchingSuperFix(aClass);
  }

  @Override
  public @NotNull IntentionAction createRemoveNewQualifierFix(@NotNull PsiNewExpression expression, PsiClass aClass) {
    return new RemoveNewQualifierFix(expression, aClass).asIntention();
  }

  @Override
  public @NotNull IntentionAction createSuperMethodReturnFix(@NotNull PsiMethod superMethod, @NotNull PsiType superMethodType) {
    return new MethodReturnTypeFix(superMethod, superMethodType, false, false, true);
  }

  @Override
  public @NotNull IntentionAction createInsertNewFix(@NotNull PsiMethodCallExpression call, @NotNull PsiClass aClass) {
    return new InsertNewFix(call, aClass).asIntention();
  }

  @Override
  public @NotNull IntentionAction createAddMethodBodyFix(@NotNull PsiMethod method) {
    return new AddMethodBodyFix(method).asIntention();
  }

  @Override
  public @NotNull IntentionAction createAddMethodBodyFix(@NotNull PsiMethod method, @NotNull @Nls String text) {
    return new AddMethodBodyFix(method, text).asIntention();
  }

  @Override
  public @NotNull IntentionAction createDeleteMethodBodyFix(@NotNull PsiMethod method) {
    return new DeleteMethodBodyFix(method).asIntention();
  }

  @Override
  public @NotNull IntentionAction createInsertSuperFix(@NotNull PsiMethod constructor) {
    return new InsertSuperFix(constructor).asIntention();
  }

  @Override
  public @NotNull IntentionAction createInsertThisFix(@NotNull PsiMethod constructor) {
    return new InsertThisFix(constructor).asIntention();
  }

  @Override
  public @NotNull IntentionAction createChangeMethodSignatureFromUsageFix(@NotNull PsiMethod targetMethod,
                                                                          PsiExpression @NotNull [] expressions,
                                                                          @NotNull PsiSubstitutor substitutor,
                                                                          @NotNull PsiElement context,
                                                                          boolean changeAllUsages,
                                                                          int minUsagesNumberToShowDialog) {
    return new ChangeMethodSignatureFromUsageFix(targetMethod, expressions, substitutor, context, changeAllUsages, minUsagesNumberToShowDialog);
  }

  @Override
  public @NotNull IntentionAction createChangeMethodSignatureFromUsageReverseOrderFix(@NotNull PsiMethod targetMethod,
                                                                                      PsiExpression @NotNull [] expressions,
                                                                                      @NotNull PsiSubstitutor substitutor,
                                                                                      @NotNull PsiElement context,
                                                                                      boolean changeAllUsages,
                                                                                      int minUsagesNumberToShowDialog) {
    return new ChangeMethodSignatureFromUsageReverseOrderFix(targetMethod,
                                                             expressions,
                                                             substitutor,
                                                             context,
                                                             changeAllUsages,
                                                             minUsagesNumberToShowDialog);
  }

  @Override
  public @NotNull List<IntentionAction> createCreateMethodFromUsageFixes(@NotNull PsiMethodCallExpression call) {
    return CreateMethodFromUsage.generateActions(call);
  }

  @Override
  public @NotNull IntentionAction createCreateMethodFromUsageFix(@NotNull PsiMethodReferenceExpression methodReferenceExpression) {
    return new CreateMethodFromMethodReferenceFix(methodReferenceExpression);
  }

  @Override
  public @NotNull List<IntentionAction> createCreateConstructorFromCallExpressionFixes(@NotNull PsiMethodCallExpression call) {
    return CreateConstructorFromUsage.generateConstructorActions(call);
  }

  @Override
  public @NotNull IntentionAction createReplaceWithTypePatternFix(@NotNull PsiReferenceExpression exprToReplace,
                                                                  @NotNull PsiClass resolvedExprClass,
                                                                  @NotNull String patternVarName) {
    return new ReplaceWithTypePatternFix(exprToReplace, resolvedExprClass, patternVarName).asIntention();
  }

  @Override
  public @NotNull IntentionAction createStaticImportMethodFix(@NotNull PsiMethodCallExpression call) {
    return new StaticImportMethodFix(call.getContainingFile(), call);
  }

  @Override
  public @NotNull IntentionAction createQualifyStaticMethodCallFix(@NotNull PsiMethodCallExpression call) {
    return new QualifyStaticMethodCallFix(call.getContainingFile(), call);
  }

  @Override
  public @NotNull IntentionAction createReplaceAddAllArrayToCollectionFix(@NotNull PsiMethodCallExpression call) {
    return new ReplaceAddAllArrayToCollectionFix(call).asIntention();
  }

  @Override
  public @NotNull List<IntentionAction> createCreateConstructorFromUsageFixes(@NotNull PsiConstructorCall call) {
    return CreateConstructorFromUsage.generateConstructorActions(call);
  }

  @Override
  public @NotNull List<IntentionAction> getVariableTypeFromCallFixes(@NotNull PsiMethodCallExpression call, @NotNull PsiExpressionList list) {
    return VariableTypeFromCallFix.getQuickFixActions(call, list);
  }

  @Override
  public @NotNull IntentionAction createAddReturnFix(@NotNull PsiParameterListOwner methodOrLambda) {
    return new AddReturnFix(methodOrLambda).asIntention();
  }

  @Override
  public @NotNull IntentionAction createAddVariableInitializerFix(@NotNull PsiVariable variable) {
    return new AddVariableInitializerFix(variable).asIntention();
  }

  @Override
  public @NotNull IntentionAction createDeferFinalAssignmentFix(@NotNull PsiVariable variable, @NotNull PsiReferenceExpression expression) {
    return new DeferFinalAssignmentFix(variable, expression).asIntention();
  }

  @Override
  public @NotNull IntentionAction createVariableAccessFromInnerClassFix(@NotNull PsiVariable variable, @NotNull PsiElement scope) {
    return new VariableAccessFromInnerClassFix(variable, scope);
  }

  @Override
  public @NotNull IntentionAction createCreateConstructorParameterFromFieldFix(@NotNull PsiField field) {
    return new CreateConstructorParameterFromFieldFix(field).asIntention();
  }

  @Override
  public @NotNull IntentionAction createInitializeFinalFieldInConstructorFix(@NotNull PsiField field) {
    return new InitializeFinalFieldInConstructorFix(field).asIntention();
  }

  @Override
  public @NotNull IntentionAction createChangeClassSignatureFromUsageFix(@NotNull PsiClass owner, @NotNull PsiReferenceParameterList parameterList) {
    return new ChangeClassSignatureFromUsageFix(owner, parameterList);
  }

  @Override
  public @NotNull IntentionAction createReplacePrimitiveWithBoxedTypeAction(@NotNull PsiTypeElement element,
                                                                            @NotNull String typeName,
                                                                            @NotNull String boxedTypeName) {
    return new ReplacePrimitiveWithBoxedTypeAction(element, typeName, boxedTypeName).asIntention();
  }

  @Override
  public @Nullable IntentionAction createReplacePrimitiveWithBoxedTypeAction(@NotNull PsiType operandType, @NotNull PsiTypeElement checkTypeElement) {
    PsiPrimitiveType primitiveType = ObjectUtils.tryCast(checkTypeElement.getType(), PsiPrimitiveType.class);
    if (primitiveType == null) return null;
    PsiClassType boxedType = primitiveType.getBoxedType(checkTypeElement);
    if (boxedType == null || !TypeConversionUtil.areTypesConvertible(operandType, boxedType)) return null;
    if (primitiveType.getBoxedTypeName() == null) return null;
    return createReplacePrimitiveWithBoxedTypeAction(checkTypeElement, primitiveType.getPresentableText(),
                                                     primitiveType.getBoxedTypeName());
  }

  @Override
  public @NotNull IntentionAction createMakeVarargParameterLastFix(@NotNull PsiVariable parameter) {
    return new MakeVarargParameterLastFix(parameter).asIntention();
  }

  @Override
  public @NotNull IntentionAction createMakeReceiverParameterFirstFix(@NotNull PsiReceiverParameter parameter) {
    return new MakeReceiverParameterFirstFix(parameter).asIntention();
  }

  @Override
  public @NotNull IntentionAction createMoveBoundClassToFrontFix(@NotNull PsiTypeParameter aClass, @NotNull PsiClassType type) {
    return new MoveBoundClassToFrontFix(aClass, type).asIntention();
  }

  @Override
  public void registerPullAsAbstractUpFixes(@NotNull PsiMethod method, @NotNull List<? super IntentionAction> registrar) {
    PullAsAbstractUpFix.registerQuickFix(method, registrar);
  }

  @Override
  public @NotNull IntentionAction createCreateAnnotationMethodFromUsageFix(@NotNull PsiNameValuePair pair) {
    return new CreateAnnotationMethodFromUsageFix(pair);
  }

  @Override
  public @NotNull IntentionAction createOptimizeImportsFix(final boolean onTheFly, @NotNull PsiFile file) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    VirtualFile virtualFile = file.getVirtualFile();
    boolean isInContent = virtualFile != null && (ModuleUtilCore.projectContainsFile(file.getProject(), virtualFile, false) || ScratchUtil.isScratch(virtualFile));
    return new OptimizeImportsFix(onTheFly, isInContent, virtualFile == null ? ThreeState.UNSURE : SilentChangeVetoer.extensionsAllowToChangeFileSilently(file.getProject(), virtualFile));
  }

  private static final class OptimizeImportsFix implements IntentionAction {
    private final boolean myOnTheFly;
    private final boolean myInContent;
    private final ThreeState extensionsAllowToChangeFileSilently;

    private OptimizeImportsFix(boolean onTheFly, boolean isInContent, @NotNull ThreeState extensionsAllowToChangeFileSilently) {
      this.extensionsAllowToChangeFileSilently = extensionsAllowToChangeFileSilently;
      ApplicationManager.getApplication().assertIsNonDispatchThread();
      myOnTheFly = onTheFly;
      myInContent = isInContent;
    }

    @Override
    public @NotNull String getText() {
      return QuickFixBundle.message("optimize.imports.fix");
    }

    @Override
    public @NotNull String getFamilyName() {
      return QuickFixBundle.message("optimize.imports.fix");
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      if (!(file instanceof PsiJavaFile)) {
        return false;
      }
      if (ApplicationManager.getApplication().isDispatchThread() && myOnTheFly && !timeToOptimizeImports(file, myInContent, extensionsAllowToChangeFileSilently)) {
        return false;
      }
      VirtualFile virtualFile = file.getViewProvider().getVirtualFile();
      return myInContent ||
             ScratchUtil.isScratch(virtualFile) ||
             virtualFile.getFileSystem() instanceof NonPhysicalFileSystem;
    }

    @Override
    public void invoke(final @NotNull Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
      invokeOnTheFlyImportOptimizer(file);
    }

    @Override
    public boolean startInWriteAction() {
      return true;
    }
  }

  @Override
  public @NotNull IntentionAction createSafeDeleteUnusedParameterInHierarchyFix(@NotNull PsiParameter parameter,
                                                                                boolean excludingHierarchy) {
    if (excludingHierarchy) {
      InspectionToolWrapper<?, ?> toolWrapper = Objects.requireNonNull(InspectionProfileManager.getInstance(parameter.getProject())
                                                                         .getCurrentProfile().getInspectionTool("unused", parameter));
      InspectionProfileEntry tool = toolWrapper.getTool();
      return new UpdateInspectionOptionFix(tool, "members.myCheckParameterExcludingHierarchy",
        JavaErrorBundle.message("parameter.excluding.hierarchy.disable.text"), false).asIntention();
    }
    else {
      return new SafeDeleteFix(parameter);
    }
  }

  @Override
  public @NotNull IntentionAction createAddToDependencyInjectionAnnotationsFix(@NotNull Project project,
                                                                               @NotNull String qualifiedName) {
    return EntryPointsManagerBase.createAddEntryPointAnnotation(qualifiedName).asIntention();
  }

  @Override
  public @NotNull IntentionAction createAddToImplicitlyWrittenFieldsFix(@NotNull Project project, final @NotNull String qualifiedName) {
    return EntryPointsManagerBase.createAddImplicitWriteAnnotation(qualifiedName).asIntention();
  }

  @Override
  public @NotNull IntentionAction createCreateGetterOrSetterFix(boolean createGetter, boolean createSetter, @NotNull PsiField field) {
    if (createGetter && createSetter) {
      return new CreateGetterOrSetterFix.CreateGetterAndSetterFix(field).asIntention();
    }
    if (createGetter) {
      return new CreateGetterOrSetterFix.CreateGetterFix(field).asIntention();
    }
    if (createSetter) {
      return new CreateGetterOrSetterFix.CreateSetterFix(field).asIntention();
    }
    throw new IllegalArgumentException();
  }

  @Override
  public @NotNull IntentionAction createRenameToIgnoredFix(@NotNull PsiVariable namedElement, boolean useElementNameAsSuffix) {
    return RenameToIgnoredFix.createRenameToIgnoreFix(namedElement, useElementNameAsSuffix).asIntention();
  }

  @Override
  public @NotNull IntentionAction createEnableOptimizeImportsOnTheFlyFix() {
    return new EnableOptimizeImportsOnTheFlyFix().asIntention();
  }

  @Override
  public @NotNull LocalQuickFixAndIntentionActionOnPsiElement createDeleteFix(@NotNull PsiElement element) {
    return LocalQuickFixAndIntentionActionOnPsiElement.from(new DeleteElementFix(element), element);
  }

  @Override
  public @NotNull IntentionAction createDeleteFix(@NotNull PsiElement @NotNull ... elements) {
    return new DeleteElementFix.DeleteMultiFix(elements).asIntention();
  }

  @Override
  public @NotNull LocalQuickFixAndIntentionActionOnPsiElement createDeleteFix(@NotNull PsiElement element, @Nls @NotNull String text) {
    return LocalQuickFixAndIntentionActionOnPsiElement.from(new DeleteElementFix(element, text), element);
  }

  @Override
  public @NotNull IntentionAction createSafeDeleteFix(@NotNull PsiElement element) {
    return new SafeDeleteFix(element);
  }

  @Override
  public @NotNull ModCommandAction createDeletePrivateMethodFix(@NotNull PsiMethod method) {
    return new DeletePrivateMethodFix(method);
  }

  @Override
  public @NotNull List<@NotNull LocalQuickFix> registerOrderEntryFixes(@NotNull PsiReference reference, @NotNull List<? super IntentionAction> registrar) {
    return OrderEntryFix.registerFixes(reference, registrar);
  }

  private static void invokeOnTheFlyImportOptimizer(@NotNull PsiFile file) {
    final Project project = file.getProject();
    final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document == null) return;

    String beforeText = file.getText();
    long oldStamp = document.getModificationStamp();
    DocumentUtil.writeInRunUndoTransparentAction(() -> new OptimizeImportsProcessor(project, file).run());
    if (oldStamp != document.getModificationStamp()) {
      String afterText = file.getText();
      if (Comparing.strEqual(beforeText, afterText)) {
        LOG.error("Import optimizer hasn't optimized any imports",
                  new Throwable(file.getViewProvider().getVirtualFile().getPath()),
                  CoreAttachmentFactory.createAttachment(file.getViewProvider().getVirtualFile()));
      }
    }
  }

  @Override
  public @NotNull IntentionAction createAddMissingRequiredAnnotationParametersFix(final @NotNull PsiAnnotation annotation,
                                                                                  final PsiMethod @NotNull [] annotationMethods,
                                                                                  final @NotNull Collection<String> missedElements) {
    return new AddMissingRequiredAnnotationParametersFix(annotation, annotationMethods, missedElements).asIntention();
  }

  @Override
  public @NotNull IntentionAction createSurroundWithQuotesAnnotationParameterValueFix(@NotNull PsiAnnotationMemberValue value,
                                                                                      @NotNull PsiType expectedType) {
    return new SurroundWithQuotesAnnotationParameterValueFix(value, expectedType).asIntention();
  }

  @Override
  public @NotNull IntentionAction addMethodQualifierFix(@NotNull PsiMethodCallExpression methodCall) {
    return new AddMethodQualifierFix(methodCall).asIntention();
  }

  @Override
  public @NotNull IntentionAction createWrapWithOptionalFix(@Nullable PsiType type, @NotNull PsiExpression expression) {
    return WrapObjectWithOptionalOfNullableFix.createFix(type, expression);
  }

  @Override
  public @Nullable IntentionAction createNotIterableForEachLoopFix(@NotNull PsiExpression expression) {
    final PsiElement parent = expression.getParent();
    if (parent instanceof PsiForeachStatement forEach) {
      final PsiType type = expression.getType();
      if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_ITERATOR)) {
        return new ReplaceIteratorForEachLoopWithIteratorForLoopFix(forEach).asIntention();
      }
    }
    return null;
  }

  @Override
  public @NotNull List<IntentionAction> createAddAnnotationAttributeNameFixes(@NotNull PsiNameValuePair pair) {
    return AddAnnotationAttributeNameFix.createFixes(pair);
  }

  private static boolean timeToOptimizeImports(@NotNull PsiFile file, boolean isInContent, @NotNull ThreeState extensionsAllowToChangeFileSilently) {
    ThreadingAssertions.assertEventDispatchThread();
    if (!CodeInsightWorkspaceSettings.getInstance(file.getProject()).isOptimizeImportsOnTheFly()) {
      return false;
    }

    DaemonCodeAnalyzerEx codeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(file.getProject());
    // don't optimize out imports in JSP since it can be included in other JSP
    if (!codeAnalyzer.isHighlightingAvailable(file) || !(file instanceof PsiJavaFile) || file instanceof ServerPageFile) return false;

    if (!codeAnalyzer.isErrorAnalyzingFinished(file)) return false;
    boolean errors = containsErrorsPreventingOptimize(file);

    return !errors && DaemonListeners.canChangeFileSilently(file, isInContent, extensionsAllowToChangeFileSilently);
  }

  private static boolean containsErrorsPreventingOptimize(@NotNull PsiFile file) {
    Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    if (document == null) return true;
    // ignore unresolved imports errors
    PsiImportList importList = ((PsiJavaFile)file).getImportList();
    final TextRange importsRange = importList == null ? TextRange.EMPTY_RANGE : importList.getTextRange();
    //noinspection UnnecessaryLocalVariable
    boolean hasErrorsBesideUnresolvedImports = !DaemonCodeAnalyzerEx
      .processHighlights(document, file.getProject(), HighlightSeverity.ERROR, 0, document.getTextLength(), error -> {
        if (error.type.isInspectionHighlightInfoType()) return true;
        int infoStart = error.getActualStartOffset();
        int infoEnd = error.getActualEndOffset();

        return importsRange.containsRange(infoStart, infoEnd) && error.type.equals(HighlightInfoType.WRONG_REF);
      });

    return hasErrorsBesideUnresolvedImports;
  }

  @Override
  public @NotNull IntentionAction createCollectionToArrayFix(@NotNull PsiExpression collectionExpression,
                                                             @NotNull PsiExpression expressionToReplace,
                                                             @NotNull PsiArrayType arrayType) {
    return new ConvertCollectionToArrayFix(collectionExpression, expressionToReplace, arrayType).asIntention();
  }

  @Override
  public @NotNull IntentionAction createInsertMethodCallFix(@NotNull PsiMethodCallExpression call, @NotNull PsiMethod method) {
    return new InsertMethodCallFix(call, method).asIntention();
  }

  @Override
  public @NotNull LocalQuickFixAndIntentionActionOnPsiElement createAccessStaticViaInstanceFix(@NotNull PsiReferenceExpression methodRef,
                                                                                               @NotNull JavaResolveResult result) {
    return LocalQuickFixAndIntentionActionOnPsiElement.from(new AccessStaticViaInstanceFix(methodRef, result), methodRef);
  }

  @Override
  public @NotNull IntentionAction createWrapWithAdapterFix(@Nullable PsiType type, @NotNull PsiExpression expression) {
    return new WrapWithAdapterMethodCallFix(type, expression, null);
  }

  @Override
  public @NotNull IntentionAction createDeleteSideEffectAwareFix(@NotNull PsiExpressionStatement statement) {
    return new DeleteSideEffectsAwareFix(statement, statement.getExpression()).asIntention();
  }

  @Override
  public @Nullable IntentionAction createCreateClassInPackageInModuleFix(@NotNull Module module, @Nullable String packageName) {
    return CreateClassInPackageInModuleFix.createFix(module, packageName);
  }

  @Override
  public @NotNull IntentionAction createPushDownMethodFix() {
    return new RunRefactoringAction(JavaRefactoringActionHandlerFactory.getInstance().createPushDownHandler(),
                                    JavaBundle.message("push.method.down.command.name")) {
      @Override
      public @NotNull Priority getPriority() {
        return Priority.NORMAL;
      }
    };
  }

  @Override
  public @NotNull IntentionAction createSameErasureButDifferentMethodsFix(@NotNull PsiMethod method, @NotNull PsiMethod superMethod) {
    return new SameErasureButDifferentMethodsFix(method, superMethod).asIntention();
  }

  @Override
  public @NotNull IntentionAction createAddMissingEnumBranchesFix(@NotNull PsiSwitchBlock switchBlock, @NotNull Set<String> missingCases) {
    return new CreateEnumMissingSwitchBranchesFix(switchBlock, missingCases).asIntention();
  }

  @Override
  public @NotNull IntentionAction createAddMissingSealedClassBranchesFix(@NotNull PsiSwitchBlock switchBlock,
                                                                         @NotNull Set<String> missingCases,
                                                                         @NotNull List<String> allNames) {
    return new CreateSealedClassMissingSwitchBranchesFix(switchBlock, missingCases, allNames).asIntention();
  }

  @Override
  public @Nullable IntentionAction createAddMissingSealedClassBranchesFixWithNull(@NotNull PsiSwitchBlock switchBlock,
                                                                                  @NotNull Set<String> missingCases,
                                                                                  @NotNull List<String> allNames) {
    PsiBasedModCommandAction<PsiSwitchBlock> withNull =
      CreateSealedClassMissingSwitchBranchesFix.createWithNull(switchBlock, missingCases, allNames);
    if (withNull == null) {
      return null;
    }
    return withNull.asIntention();
  }

  @Override
  public @Nullable IntentionAction createAddMissingBooleanPrimitiveBranchesFix(@NotNull PsiSwitchBlock block) {
    CreateMissingBooleanPrimitiveBranchesFix fix = CreateMissingBooleanPrimitiveBranchesFix.createFix(block);
    if (fix == null) return null;
    return fix.asIntention();
  }

  @Override
  public @Nullable IntentionAction createAddMissingBooleanPrimitiveBranchesFixWithNull(@NotNull PsiSwitchBlock block) {
    @Nullable PsiBasedModCommandAction<PsiSwitchBlock> fix = CreateMissingBooleanPrimitiveBranchesFix.createWithNull(block);
    if (fix == null) return null;
    return fix.asIntention();
  }

  @Override
  public @Nullable IntentionAction createAddMissingRecordClassBranchesFix(@NotNull PsiSwitchBlock switchBlock,
                                                                          @NotNull PsiClass selectorType,
                                                                          @NotNull Map<PsiType, Set<List<PsiType>>> branches,
                                                                          @NotNull List<? extends PsiCaseLabelElement> elements) {
    CreateMissingDeconstructionRecordClassBranchesFix fix =
      CreateMissingDeconstructionRecordClassBranchesFix.create(switchBlock, selectorType, branches, elements);
    return fix == null ? null : fix.asIntention();
  }

  @Override
  public @NotNull IntentionAction createAddSwitchDefaultFix(@NotNull PsiSwitchBlock switchBlock, @IntentionName String message) {
    return new CreateDefaultBranchFix(switchBlock, message).asIntention();
  }

  @Override
  public @Nullable IntentionAction createCollapseAnnotationsFix(@NotNull PsiAnnotation annotation) {
    return CollapseAnnotationsFix.from(annotation);
  }

  @Override
  public @NotNull IntentionAction createChangeModifierFix() {
    return new ChangeModifierIntention(true);
  }

  @Override
  public @NotNull IntentionAction createWrapSwitchRuleStatementsIntoBlockFix(@NotNull PsiSwitchLabeledRuleStatement rule) {
    return new WrapSwitchRuleStatementsIntoBlockFix(rule).asIntention();
  }

  @Override
  public @NotNull IntentionAction createAddParameterListFix(@NotNull PsiMethod method) {
    return new AddParameterListFix(method).asIntention();
  }

  @Override
  public @NotNull IntentionAction createAddEmptyRecordHeaderFix(@NotNull PsiClass psiClass) {
    return new AddEmptyRecordHeaderFix(psiClass).asIntention();
  }

  @Override
  public @NotNull IntentionAction createCreateFieldFromParameterFix(@NotNull PsiParameter parameter) {
    return new CreateFieldFromParameterAction(parameter).asIntention();
  }

  @Override
  public @NotNull IntentionAction createAssignFieldFromParameterFix(@NotNull PsiParameter parameter) {
    return new AssignFieldFromParameterAction(parameter).asIntention();
  }

  @Override
  public @NotNull IntentionAction createFillPermitsListFix(@NotNull PsiIdentifier classIdentifier) {
    return new FillPermitsListFix(classIdentifier).asIntention();
  }

  @Override
  public @NotNull IntentionAction createAddToPermitsListFix(@NotNull PsiClass subClass,
                                                            @NotNull PsiClass superClass) {
    return new AddToPermitsListFix(subClass, superClass).asIntention();
  }

  @Override
  public @NotNull IntentionAction createMoveClassToPackageFix(@NotNull PsiClass classToMove, @NotNull String packageName) {
    return new MoveToPackageFix(classToMove.getContainingFile(), packageName);
  }

  @Override
  public @NotNull List<IntentionAction> createExtendSealedClassFixes(@NotNull PsiJavaCodeReferenceElement subclassRef,
                                                                     @NotNull PsiClass parentClass,
                                                                     @NotNull PsiClass subClass) {
    ModCommandAction fix = ImplementOrExtendFix.createFix(subClass, parentClass);
    return fix == null ? List.of() : List.of(fix.asIntention());
  }

  @Override
  public @NotNull IntentionAction createSealClassFromPermitsListFix(@NotNull PsiClass classFromPermitsList) {
    return new SealClassFromPermitsListAction(classFromPermitsList).asIntention();
  }

  @Override
  public @NotNull IntentionAction createRemoveDuplicateExtendsAction(@NotNull String className) {
    return new UnimplementInterfaceAction.RemoveDuplicateExtendFix(className).asIntention();
  }

  @Override
  public @NotNull IntentionAction createReceiverParameterTypeFix(@NotNull PsiReceiverParameter parameter, @NotNull PsiType newType) {
    return new ReceiverParameterTypeFix(parameter, newType).asIntention();
  }

  private static final class ReceiverParameterTypeFix extends SetVariableTypeFix {
    private ReceiverParameterTypeFix(@NotNull PsiReceiverParameter receiverParameter, @NotNull PsiType enclosingClassType) {
      super(receiverParameter, enclosingClassType);
    }

    @Override
    protected @NotNull String getText() {
      return QuickFixBundle.message("fix.receiver.parameter.type.text");
    }

    @Override
    public @NotNull String getFamilyName() {
      return QuickFixBundle.message("fix.receiver.parameter.type.family");
    }
  }

  @Override
  public @NotNull IntentionAction createConvertInterfaceToClassFix(@NotNull PsiClass aClass) {
    return new ConvertInterfaceToClassIntention(aClass).asIntention();
  }

  @Override
  public @Nullable IntentionAction createUnwrapArrayInitializerMemberValueAction(@NotNull PsiArrayInitializerMemberValue arrayValue) {
    UnwrapArrayInitializerMemberValueAction fix = UnwrapArrayInitializerMemberValueAction.createFix(arrayValue);
    return fix == null ? null : fix.asIntention();
  }

  @Override
  public @NotNull IntentionAction createIntroduceVariableAction(@NotNull PsiExpression expression) {
    return new IntroduceVariableErrorFixAction(expression);
  }

  @Override
  public @NotNull IntentionAction createInsertReturnFix(@NotNull PsiExpression expression) {
    return new ConvertExpressionToReturnFix(expression).asIntention();
  }

  @Override
  public @NotNull IntentionAction createIterateFix(@NotNull PsiExpression expression) {
    return new IterateOverIterableIntention(expression);
  }

  @Override
  public @NotNull IntentionAction createDeleteSwitchLabelFix(@NotNull PsiCaseLabelElement labelElement) {
    return new DeleteSwitchLabelFix(labelElement, false).asIntention();
  }

  @Override
  public @NotNull IntentionAction createDeleteDefaultFix(@NotNull PsiFile file, @NotNull PsiElement defaultElement) {
    return new UnnecessaryDefaultInspection.DeleteDefaultFix().asIntention();
  }


  @Override
  public @NotNull IntentionAction createAddAnnotationTargetFix(@NotNull PsiAnnotation annotation,
                                                               @NotNull PsiAnnotation.TargetType target) {
    return new AddAnnotationTargetFix(annotation, target).asIntention();
  }

  @Override
  public @Nullable IntentionAction createMergeDuplicateAttributesFix(@NotNull PsiNameValuePair pair) {
    final PsiReference reference = pair.getReference();
    if (reference == null) return null;
    final PsiMethod resolved = ObjectUtils.tryCast(reference.resolve(), PsiMethod.class);
    if (resolved == null) return null;
    final PsiType returnType = resolved.getReturnType();
    if (!(returnType instanceof PsiArrayType)) return null;
    return new MergeDuplicateAttributesFix(pair).asIntention();
  }

  @Override
  public @NotNull IntentionAction createMoveSwitchBranchUpFix(@NotNull PsiCaseLabelElement moveBeforeLabel,
                                                              @NotNull PsiCaseLabelElement labelElement) {
    return new MoveSwitchBranchUpFix(moveBeforeLabel, labelElement).asIntention();
  }

  @Override
  public @NotNull IntentionAction createSimplifyBooleanFix(@NotNull PsiExpression expression, boolean value) {
    return new SimplifyBooleanExpressionFix(expression, value).asIntention();
  }

  @Override
  public @NotNull IntentionAction createSetVariableTypeFix(@NotNull PsiVariable variable, @NotNull PsiType type) {
    return new SetVariableTypeFix(variable, type).asIntention();
  }

  @Override
  public @NotNull IntentionAction createReceiverParameterNameFix(@NotNull PsiReceiverParameter parameter, @NotNull String newName) {
    return new ReceiverParameterNameFix(parameter, newName).asIntention();
  }

  @Override
  public @NotNull IntentionAction createRemoveRedundantLambdaParameterTypesFix(@NotNull PsiLambdaExpression lambdaExpression,
                                                                               @IntentionName String message) {
    return new RemoveRedundantLambdaParameterTypesFix(lambdaExpression, message).asIntention();
  }

  @Override
  public @NotNull IntentionAction createConvertAnonymousToInnerAction(@NotNull PsiAnonymousClass anonymousClass) {
    return new MoveAnonymousOrLocalToInnerFix(anonymousClass);
  }

  @Override
  public @NotNull IntentionAction createConvertLocalToInnerAction(@NotNull PsiClass localClass) {
    return new MoveAnonymousOrLocalToInnerFix(localClass);
  }

  private static final class RemoveRedundantLambdaParameterTypesFix extends RemoveRedundantParameterTypesFix {
    private final @IntentionName String myMessage;

    private RemoveRedundantLambdaParameterTypesFix(@NotNull PsiLambdaExpression lambdaExpression, @IntentionName String message) {
      super(lambdaExpression);
      myMessage = message;
    }

    @Override
    protected @NotNull Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiLambdaExpression element) {
      return Presentation.of(myMessage);
    }
  }

  @Override
  public @NotNull IntentionAction createSplitSwitchBranchWithSeveralCaseValuesAction() {
    return new SplitSwitchBranchWithSeveralCaseValuesAction().asIntention();
  }

  @Override
  public @Nullable IntentionAction createMakeVariableEffectivelyFinalFix(@NotNull PsiVariable variable) {
    return MakeVarEffectivelyFinalFix.createFix(variable);
  }

  @Override
  public @NotNull IntentionAction createDeleteFix(@NotNull PsiElement @NotNull [] elements, @NotNull @Nls String text) {
    return new DeleteElementFix.DeleteMultiFix(elements, text).asIntention();
  }

  @Override
  public @NotNull ModCommandAction createReplaceCaseDefaultWithDefaultFix(@NotNull PsiCaseLabelElementList list){
    return new ReplaceCaseDefaultWithDefaultFix(list);
  }

  @Override
  public @NotNull ModCommandAction createReverseCaseDefaultNullFixFix(@NotNull PsiCaseLabelElementList list){
    return new ReverseCaseDefaultNullFix(list);
  }

  @Override
  public @NotNull IntentionAction createAddMainMethodFix(@NotNull PsiImplicitClass implicitClass) {
    return new AddMainMethodFix(implicitClass).asIntention();
  }
}
