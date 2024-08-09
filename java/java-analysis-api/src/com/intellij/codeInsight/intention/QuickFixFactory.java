// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.lang.jvm.actions.JvmElementActionsFactory;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PropertyMemberType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class QuickFixFactory {
  public static QuickFixFactory getInstance() {
    return ApplicationManager.getApplication().getService(QuickFixFactory.class);
  }

  /**
   * Consider to use
   * {@link QuickFixFactory#createModifierListFix(PsiModifierListOwner, String, boolean, boolean)} for java only fix or
   * {@link JvmElementActionsFactory#createChangeModifierActions(com.intellij.lang.jvm.JvmModifiersOwner, com.intellij.lang.jvm.actions.ChangeModifierRequest)}
   * for jvm languages transparent fix
   * <p>
   * Usage of this method might be unsafe in case of fixing java multi variable declaration modifier list
   */
  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createModifierListFix(@NotNull PsiModifierList modifierList,
                                                                                    @PsiModifier.ModifierConstant @NotNull String modifier,
                                                                                    boolean shouldHave,
                                                                                    final boolean showContainingClass);

  /**
   * @see JvmElementActionsFactory#createChangeModifierActions(com.intellij.lang.jvm.JvmModifiersOwner, com.intellij.lang.jvm.actions.ChangeModifierRequest
   * for jvm language transparent fix
   */
  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createModifierListFix(@NotNull PsiModifierListOwner owner,
                                                                                    @PsiModifier.ModifierConstant @NotNull String modifier,
                                                                                    boolean shouldHave,
                                                                                    final boolean showContainingClass);

  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createMethodReturnFix(@NotNull PsiMethod method,
                                                                                    @NotNull PsiType toReturn,
                                                                                    boolean fixWholeHierarchy);

  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createMethodReturnFix(@NotNull PsiMethod method,
                                                                                    @NotNull PsiType toReturn,
                                                                                    boolean fixWholeHierarchy,
                                                                                    boolean suggestSuperTypes);

  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createAnnotationMethodReturnFix(@NotNull PsiMethod method,
                                                                                              @NotNull PsiType toReturn,
                                                                                              boolean fromDefaultValue);

  /**
   * @param psiElement psiClass or enum constant without class initializer
   */
  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createImplementMethodsFix(@NotNull PsiElement psiElement);

  @NotNull
  public abstract IntentionAction createAssignmentToComparisonFix(@NotNull PsiAssignmentExpression expr);

  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createImplementMethodsFix(@NotNull PsiClass psiElement);

  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createMethodThrowsFix(@NotNull PsiMethod method,
                                                                  @NotNull PsiClassType exceptionClass,
                                                                  boolean shouldThrow,
                                                                  boolean showContainingClass);

  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createAddDefaultConstructorFix(@NotNull PsiClass aClass);

  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createMethodParameterTypeFix(@NotNull PsiMethod method,
                                                                                           int index,
                                                                                           @NotNull PsiType newType,
                                                                                           boolean fixWholeHierarchy);

  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createExtendsListFix(@NotNull PsiClass aClass,
                                                                                   @NotNull PsiClassType typeToExtendFrom,
                                                                                   boolean toAdd);

  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createRemoveUnusedParameterFix(@NotNull PsiParameter parameter);

  @NotNull
  public abstract IntentionAction createRemoveUnusedVariableFix(@NotNull PsiVariable variable);

  @Nullable
  public abstract IntentionAction createCreateClassOrPackageFix(@NotNull PsiElement context,
                                                                @NotNull String qualifiedName,
                                                                final boolean createClass,
                                                                final String superClass);

  @Nullable
  public abstract IntentionAction createCreateClassOrInterfaceFix(@NotNull PsiElement context,
                                                                  @NotNull String qualifiedName,
                                                                  final boolean createClass,
                                                                  final String superClass);

  @NotNull
  public abstract IntentionAction createCreateFieldOrPropertyFix(@NotNull PsiClass aClass,
                                                                 @NotNull String name,
                                                                 @NotNull PsiType type,
                                                                 @NotNull PropertyMemberType targetMember,
                                                                 PsiAnnotation @NotNull ... annotations);

  @NotNull
  public abstract IntentionAction createAddExceptionToCatchFix();

  @NotNull
  public abstract IntentionAction createAddExceptionToThrowsFix(@NotNull PsiElement element);

  @NotNull
  public abstract IntentionAction createAddExceptionFromFieldInitializerToConstructorThrowsFix(@NotNull PsiElement element);

  @NotNull
  public abstract IntentionAction createSurroundWithTryCatchFix(@NotNull PsiElement element);

  @NotNull
  public abstract IntentionAction createAddExceptionToExistingCatch(@NotNull PsiElement element);

  @NotNull
  public abstract IntentionAction createChangeToAppendFix(@NotNull IElementType sign,
                                                          @NotNull PsiType type,
                                                          @NotNull PsiAssignmentExpression assignment);

  @NotNull
  public abstract IntentionAction createAddTypeCastFix(@NotNull PsiType type, @NotNull PsiExpression expression);

  @NotNull
  public abstract IntentionAction createReuseVariableDeclarationFix(@NotNull PsiLocalVariable variable);

  @NotNull
  public abstract IntentionAction createNavigateToAlreadyDeclaredVariableFix(@NotNull PsiVariable variable);

  @NotNull
  public abstract IntentionAction createNavigateToDuplicateElementFix(@NotNull NavigatablePsiElement element);

  @NotNull
  public abstract IntentionAction createConvertToStringLiteralAction();

  /**
   * Provides fix to remove return statement or return value in case when return statement is not last statement in block.
   *
   * @param method method with return statement
   * @param returnStatement statement to remove
   */
  @NotNull
  public abstract IntentionAction createDeleteReturnFix(@NotNull PsiMethod method, @NotNull PsiReturnStatement returnStatement);

  @NotNull
  public abstract IntentionAction createDeleteCatchFix(@NotNull PsiParameter parameter);

  @NotNull
  public abstract IntentionAction createDeleteMultiCatchFix(@NotNull PsiTypeElement element);

  @NotNull
  public abstract IntentionAction createConvertSwitchToIfIntention(@NotNull PsiSwitchStatement statement);

  @NotNull
  public abstract IntentionAction createNegationBroadScopeFix(@NotNull PsiPrefixExpression expr);

  @NotNull
  public abstract IntentionAction createCreateFieldFromUsageFix(@NotNull PsiReferenceExpression place);

  @NotNull
  public abstract IntentionAction createReplaceWithListAccessFix(@NotNull PsiArrayAccessExpression expression);

  @NotNull
  public abstract IntentionAction createAddNewArrayExpressionFix(@NotNull PsiArrayInitializerExpression expression);

  @NotNull
  public abstract IntentionAction createMoveCatchUpFix(@NotNull PsiCatchSection section, @NotNull PsiCatchSection section1);

  @NotNull
  public abstract IntentionAction createRenameWrongRefFix(@NotNull PsiReferenceExpression ref);

  @NotNull
  public abstract IntentionAction createRemoveQualifierFix(@NotNull PsiExpression qualifier,
                                                           @NotNull PsiReferenceExpression expression,
                                                           @NotNull PsiClass resolved);

  @NotNull
  public abstract IntentionAction createRemoveParameterListFix(@NotNull PsiMethod parent);

  @NotNull
  public abstract IntentionAction createShowModulePropertiesFix(@NotNull PsiElement element);

  @NotNull
  public abstract IntentionAction createShowModulePropertiesFix(@NotNull Module module);

  @NotNull
  public abstract IntentionAction createIncreaseLanguageLevelFix(@NotNull LanguageLevel level);

  @NotNull
  public abstract IntentionAction createUpgradeSdkFor(@NotNull LanguageLevel level);

  @NotNull
  public abstract IntentionAction createChangeParameterClassFix(@NotNull PsiClass aClass, @NotNull PsiClassType type);

  @NotNull
  public abstract IntentionAction createReplaceInaccessibleFieldWithGetterSetterFix(@NotNull PsiReferenceExpression element,
                                                                                    @NotNull PsiMethod getter,
                                                                                    boolean isSetter);

  @NotNull
  public abstract IntentionAction createSurroundWithArrayFix(@Nullable PsiCall methodCall, @Nullable PsiExpression expression);

  @NotNull
  public abstract IntentionAction createImplementAbstractClassMethodsFix(@NotNull PsiElement elementToHighlight);

  @NotNull
  public abstract IntentionAction createMoveClassToSeparateFileFix(@NotNull PsiClass aClass);

  @NotNull
  public abstract IntentionAction createRenameFileFix(@NotNull String newName);

  @Nullable
  public abstract IntentionAction createRenameFix(@NotNull PsiElement element);

  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createRenameElementFix(@NotNull PsiNamedElement element);

  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createRenameElementFix(@NotNull PsiNamedElement element,
                                                                                     @NotNull String newName);

  @NotNull
  public abstract IntentionAction createChangeExtendsToImplementsFix(@NotNull PsiClass aClass, @NotNull PsiClassType classToExtendFrom);

  @NotNull
  public abstract IntentionAction createCreateConstructorMatchingSuperFix(@NotNull PsiClass aClass);

  @NotNull
  public abstract IntentionAction createRemoveNewQualifierFix(@NotNull PsiNewExpression expression, @Nullable PsiClass aClass);

  @NotNull
  public abstract IntentionAction createSuperMethodReturnFix(@NotNull PsiMethod superMethod, @NotNull PsiType superMethodType);

  @NotNull
  public abstract IntentionAction createInsertNewFix(@NotNull PsiMethodCallExpression call, @NotNull PsiClass aClass);

  @NotNull
  public abstract IntentionAction createAddMethodBodyFix(@NotNull PsiMethod method);

  @NotNull
  public abstract IntentionAction createAddMethodBodyFix(@NotNull PsiMethod method, @NotNull @Nls String text);

  @NotNull
  public abstract IntentionAction createDeleteMethodBodyFix(@NotNull PsiMethod method);

  @NotNull
  public abstract IntentionAction createInsertSuperFix(@NotNull PsiMethod constructor);

  @NotNull
  public abstract IntentionAction createInsertThisFix(@NotNull PsiMethod constructor);

  @NotNull
  public abstract IntentionAction createChangeMethodSignatureFromUsageFix(@NotNull PsiMethod targetMethod,
                                                                          PsiExpression @NotNull [] expressions,
                                                                          @NotNull PsiSubstitutor substitutor,
                                                                          @NotNull PsiElement context,
                                                                          boolean changeAllUsages, int minUsagesNumberToShowDialog);

  @NotNull
  public abstract IntentionAction createChangeMethodSignatureFromUsageReverseOrderFix(@NotNull PsiMethod targetMethod,
                                                                                      PsiExpression @NotNull [] expressions,
                                                                                      @NotNull PsiSubstitutor substitutor,
                                                                                      @NotNull PsiElement context,
                                                                                      boolean changeAllUsages,
                                                                                      int minUsagesNumberToShowDialog);

  @NotNull
  public List<IntentionAction> createCreateMethodFromUsageFixes(@NotNull PsiMethodCallExpression call) {
    return Collections.emptyList();
  }

  @NotNull
  public abstract IntentionAction createCreateMethodFromUsageFix(@NotNull PsiMethodReferenceExpression methodReferenceExpression);

  @NotNull
  public List<IntentionAction> createCreateConstructorFromCallExpressionFixes(@NotNull PsiMethodCallExpression call) {
    return Collections.emptyList();
  }

  @NotNull
  public abstract IntentionAction createReplaceWithTypePatternFix(@NotNull PsiReferenceExpression exprToReplace,
                                                                  @NotNull PsiClass resolvedExprClass,
                                                                  @NotNull String patternVarName);

  @NotNull
  public abstract IntentionAction createStaticImportMethodFix(@NotNull PsiMethodCallExpression call);

  @NotNull
  public abstract IntentionAction createQualifyStaticMethodCallFix(@NotNull PsiMethodCallExpression call);

  @NotNull
  public abstract IntentionAction createReplaceAddAllArrayToCollectionFix(@NotNull PsiMethodCallExpression call);

  @NotNull
  public List<IntentionAction> createCreateConstructorFromUsageFixes(@NotNull PsiConstructorCall call) {
    return Collections.emptyList();
  }

  @NotNull
  public abstract List<IntentionAction> getVariableTypeFromCallFixes(@NotNull PsiMethodCallExpression call,
                                                                     @NotNull PsiExpressionList list);

  @NotNull
  public abstract IntentionAction createAddReturnFix(@NotNull PsiParameterListOwner methodOrLambda);

  @NotNull
  public abstract IntentionAction createAddVariableInitializerFix(@NotNull PsiVariable variable);

  @NotNull
  public abstract IntentionAction createDeferFinalAssignmentFix(@NotNull PsiVariable variable, @NotNull PsiReferenceExpression expression);

  @NotNull
  public abstract IntentionAction createVariableAccessFromInnerClassFix(@NotNull PsiVariable variable, @NotNull PsiElement scope);

  @NotNull
  public abstract IntentionAction createCreateConstructorParameterFromFieldFix(@NotNull PsiField field);

  @NotNull
  public abstract IntentionAction createInitializeFinalFieldInConstructorFix(@NotNull PsiField field);

  @NotNull
  public abstract IntentionAction createChangeClassSignatureFromUsageFix(@NotNull PsiClass owner,
                                                                         @NotNull PsiReferenceParameterList parameterList);

  @NotNull
  public abstract IntentionAction createReplacePrimitiveWithBoxedTypeAction(@NotNull PsiTypeElement element,
                                                                            @NotNull String typeName,
                                                                            @NotNull String boxedTypeName);

  @Nullable
  public abstract IntentionAction createReplacePrimitiveWithBoxedTypeAction(@NotNull PsiType operandType,
                                                                            @NotNull PsiTypeElement checkTypeElement);

  @NotNull
  public abstract IntentionAction createMakeVarargParameterLastFix(@NotNull PsiVariable parameter);

  @NotNull
  public abstract IntentionAction createMakeReceiverParameterFirstFix(@NotNull PsiReceiverParameter parameter);

  @NotNull
  public abstract IntentionAction createMoveBoundClassToFrontFix(@NotNull PsiTypeParameter aClass, @NotNull PsiClassType type);

  public abstract void registerPullAsAbstractUpFixes(@NotNull PsiMethod method, @NotNull List<? super IntentionAction> registrar);

  @NotNull
  public abstract IntentionAction createCreateAnnotationMethodFromUsageFix(@NotNull PsiNameValuePair pair);

  @NotNull
  public abstract IntentionAction createOptimizeImportsFix(boolean onTheFly, @NotNull PsiFile file);

  @NotNull
  public abstract IntentionAction createSafeDeleteUnusedParameterInHierarchyFix(@NotNull PsiParameter parameter, boolean excludingHierarchy);

  @NotNull
  public abstract IntentionAction createAddToDependencyInjectionAnnotationsFix(@NotNull Project project, @NotNull String qualifiedName);

  @NotNull
  public abstract IntentionAction createAddToImplicitlyWrittenFieldsFix(@NotNull Project project, @NotNull String qualifiedName);

  @NotNull
  public abstract IntentionAction createCreateGetterOrSetterFix(boolean createGetter, boolean createSetter, @NotNull PsiField field);

  @NotNull
  public abstract IntentionAction createRenameToIgnoredFix(@NotNull PsiVariable namedElement, boolean useElementNameAsSuffix);

  @NotNull
  public abstract IntentionAction createEnableOptimizeImportsOnTheFlyFix();

  @NotNull
  public abstract IntentionAction createDeleteFix(@NotNull PsiElement @NotNull ... elements);

  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createDeleteFix(@NotNull PsiElement element);

  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createDeleteFix(@NotNull PsiElement element, @NotNull @Nls String text);

  @NotNull
  public abstract IntentionAction createDeleteSideEffectAwareFix(@NotNull PsiExpressionStatement statement);

  @NotNull
  public abstract IntentionAction createSafeDeleteFix(@NotNull PsiElement element);

  /**
   * @param method method to delete
   * @return a fix to remove private method, possibly along with called methods unused elsewhere
   */
  public abstract @NotNull ModCommandAction createDeletePrivateMethodFix(@NotNull PsiMethod method);

  public abstract @NotNull List<@NotNull LocalQuickFix> registerOrderEntryFixes(@NotNull PsiReference reference,
                                                                                @NotNull List<? super IntentionAction> registrar);

  @NotNull
  public abstract IntentionAction createAddMissingRequiredAnnotationParametersFix(@NotNull PsiAnnotation annotation,
                                                                                  PsiMethod @NotNull [] annotationMethods,
                                                                                  @NotNull Collection<String> missedElements);

  @NotNull
  public abstract IntentionAction createSurroundWithQuotesAnnotationParameterValueFix(@NotNull PsiAnnotationMemberValue value,
                                                                                      @NotNull PsiType expectedType);

  @NotNull
  public abstract IntentionAction addMethodQualifierFix(@NotNull PsiMethodCallExpression methodCall);

  @NotNull
  public abstract IntentionAction createWrapWithOptionalFix(@Nullable PsiType type, @NotNull PsiExpression expression);

  @Nullable
  public abstract IntentionAction createNotIterableForEachLoopFix(@NotNull PsiExpression expression);

  @NotNull
  public abstract List<IntentionAction> createAddAnnotationAttributeNameFixes(@NotNull PsiNameValuePair pair);

  @NotNull
  public abstract IntentionAction createCollectionToArrayFix(@NotNull PsiExpression collectionExpression,
                                                             @NotNull PsiExpression expressionToReplace,
                                                             @NotNull PsiArrayType arrayType);

  @NotNull
  public abstract IntentionAction createInsertMethodCallFix(@NotNull PsiMethodCallExpression call, @NotNull PsiMethod method);

  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createAccessStaticViaInstanceFix(@NotNull PsiReferenceExpression methodRef,
                                                                                               @NotNull JavaResolveResult result);

  @NotNull
  public abstract IntentionAction createWrapWithAdapterFix(@Nullable PsiType type, @NotNull PsiExpression expression);

  @Nullable
  public abstract IntentionAction createCreateClassInPackageInModuleFix(@NotNull Module module, @Nullable String packageName);

  @NotNull
  public abstract IntentionAction createPushDownMethodFix();

  @NotNull
  public abstract IntentionAction createSameErasureButDifferentMethodsFix(@NotNull PsiMethod method, @NotNull PsiMethod superMethod);

  @NotNull
  public abstract IntentionAction createAddMissingEnumBranchesFix(@NotNull PsiSwitchBlock switchBlock, @NotNull Set<String> missingCases);

  @NotNull
  public abstract IntentionAction createAddMissingSealedClassBranchesFix(@NotNull PsiSwitchBlock switchBlock,
                                                                         @NotNull Set<String> missingCases,
                                                                         @NotNull List<String> allNames);

  @Nullable
  public abstract IntentionAction createAddMissingRecordClassBranchesFix(@NotNull PsiSwitchBlock switchBlock,
                                                                         @NotNull PsiClass selectorType,
                                                                         @NotNull Map<PsiType, Set<List<PsiType>>> branches,
                                                                         @NotNull List<? extends PsiCaseLabelElement> elements);

  @Nullable
  public abstract IntentionAction createAddMissingSealedClassBranchesFixWithNull(@NotNull PsiSwitchBlock switchBlock,
                                                                                           @NotNull Set<String> missingCases,
                                                                                           @NotNull List<String> allNames);

  @Nullable
  public abstract IntentionAction createAddMissingBooleanPrimitiveBranchesFix(@NotNull PsiSwitchBlock block);

  @Nullable
  public abstract IntentionAction createAddMissingBooleanPrimitiveBranchesFixWithNull(@NotNull PsiSwitchBlock block);

  @NotNull
  public abstract IntentionAction createAddSwitchDefaultFix(@NotNull PsiSwitchBlock switchBlock, @Nullable String message);

  @Nullable
  public abstract IntentionAction createCollapseAnnotationsFix(@NotNull PsiAnnotation annotation);

  @NotNull
  public abstract IntentionAction createChangeModifierFix();

  @NotNull
  public abstract IntentionAction createWrapSwitchRuleStatementsIntoBlockFix(@NotNull PsiSwitchLabeledRuleStatement rule);

  @NotNull
  public abstract IntentionAction createAddParameterListFix(@NotNull PsiMethod method);

  @NotNull
  public abstract IntentionAction createAddEmptyRecordHeaderFix(@NotNull PsiClass record);

  @NotNull
  public abstract IntentionAction createCreateFieldFromParameterFix(@NotNull PsiParameter parameter);
  @NotNull
  public abstract IntentionAction createAssignFieldFromParameterFix(@NotNull PsiParameter parameter);

  @NotNull
  public abstract IntentionAction createFillPermitsListFix(@NotNull PsiIdentifier classIdentifier);

  /**
   * @param subClass class that should be added to the parent permits list
   * @param superClass sealed parent class from subclasses' extends / implements clause
   */
  @NotNull
  public abstract IntentionAction createAddToPermitsListFix(@NotNull PsiClass subClass, @NotNull PsiClass superClass);

  @NotNull
  public abstract IntentionAction createMoveClassToPackageFix(@NotNull PsiClass classToMove, @NotNull String packageName);

  /**
   * Provides fixes to make class extend sealed class and
   * possibly mark extending class with one of sealed subclass modifiers (final, sealed, non-sealed)
   *
   * @param subclassRef reference in permits list of a parent class
   */
  public abstract @NotNull List<IntentionAction> createExtendSealedClassFixes(@NotNull PsiJavaCodeReferenceElement subclassRef,
                                                                              @NotNull PsiClass parentClass, @NotNull PsiClass subClass);

  public abstract @NotNull IntentionAction createSealClassFromPermitsListFix(@NotNull PsiClass classFromPermitsList);

  public abstract @NotNull IntentionAction createRemoveDuplicateExtendsAction(@NotNull String className);

  /**
   * Creates a fix that changes the type of the receiver parameter
   *
   * @param parameter receiver parameter to change type for
   * @param type      new type of the receiver parameter
   *                  <p>
   *                  In an instance method the type of the receiver parameter must be
   *                  the class or interface in which the method is declared.
   *                  <p>
   *                  In an inner class's constructor the type of the receiver parameter
   *                  must be the class or interface which is the immediately enclosing
   *                  type declaration of the inner class.
   * @return a new fix
   */
  public abstract @NotNull IntentionAction createReceiverParameterTypeFix(@NotNull PsiReceiverParameter parameter,
                                                                          @NotNull PsiType type);

  public abstract @NotNull IntentionAction createConvertInterfaceToClassFix(@NotNull PsiClass aClass);

  @Nullable
  public abstract IntentionAction createUnwrapArrayInitializerMemberValueAction(@NotNull PsiArrayInitializerMemberValue arrayValue);

  public abstract @NotNull IntentionAction createIntroduceVariableAction(@NotNull PsiExpression expression);

  public abstract @NotNull IntentionAction createInsertReturnFix(@NotNull PsiExpression expression);

  public abstract @NotNull IntentionAction createIterateFix(@NotNull PsiExpression expression);

  public abstract @NotNull IntentionAction createDeleteSwitchLabelFix(@NotNull PsiCaseLabelElement labelElement);

  public abstract @NotNull IntentionAction createDeleteDefaultFix(@NotNull PsiFile file, @NotNull PsiElement defaultElement);

  public abstract @NotNull IntentionAction createAddAnnotationTargetFix(@NotNull PsiAnnotation annotation, PsiAnnotation.TargetType target);

  @Nullable
  public abstract IntentionAction createMergeDuplicateAttributesFix(@NotNull PsiNameValuePair pair);

  @NotNull
  public abstract IntentionAction createMoveSwitchBranchUpFix(@NotNull PsiCaseLabelElement moveBeforeLabel,
                                                              @NotNull PsiCaseLabelElement labelElement);

  @NotNull
  public abstract IntentionAction createSimplifyBooleanFix(@NotNull PsiExpression expression, boolean value);

  /**
   * Creates a fix that sets explicit variable type
   *
   * @param variable variable to update
   * @param type type to set
   * @return a new fix
   */
  public abstract @NotNull IntentionAction createSetVariableTypeFix(@NotNull PsiVariable variable, @NotNull PsiType type);

  /**
   * Creates a fix that changes the name of the receiver parameter
   *
   * @param parameter receiver parameter to change name for
   * @param newName   new name of the receiver parameter
   *                  <p>
   *                  In an instance method the name of the receiver parameter must be {@code this}.
   *                  <p>
   *                  In an inner class's constructor the name of the receiver parameter must be <i>Identifier</i>.{@code this}
   *                  where <i>Identifier</i> is the simple name of the class or interface which is the immediately enclosing type
   *                  declaration of the inner class.
   * @return a new fix
   */
  public abstract @NotNull IntentionAction createReceiverParameterNameFix(@NotNull PsiReceiverParameter parameter,
                                                                          @NotNull String newName);

  /**
   * Creates a fix that removes lambda parameter types when possible
   *
   * @param lambdaExpression lambda expression to process
   * @param message          the text to show in the quick-fix popup.
   * @return a new fix
   */
  public abstract @NotNull IntentionAction createRemoveRedundantLambdaParameterTypesFix(@NotNull PsiLambdaExpression lambdaExpression,
                                                                                        @IntentionName String message);

  /**
   * @param anonymousClass class to convert
   * @return a fix that converts an anonymous class to an inner class
   */
  public abstract @NotNull IntentionAction createConvertAnonymousToInnerAction(@NotNull PsiAnonymousClass anonymousClass);

  /**
   * @param localClass class to convert
   * @return a fix that converts a local class to an inner class
   */
  public abstract @NotNull IntentionAction createConvertLocalToInnerAction(@NotNull PsiClass localClass);

  public abstract @NotNull IntentionAction createSplitSwitchBranchWithSeveralCaseValuesAction();

  /**
   * @param variable variable to make an effectively final
   * @return a fix that refactors code to make variable effectively final when possible. Null, if it cannot create such a fix.
   */
  public abstract @Nullable IntentionAction createMakeVariableEffectivelyFinalFix(@NotNull PsiVariable variable);

  /**
   * @param elements elements to delete
   * @param text     the text to show in the intention popup
   * @return a fix that deletes the elements
   */
  @NotNull
  public abstract IntentionAction createDeleteFix(@NotNull PsiElement @NotNull [] elements, @NotNull @Nls String text);

  @NotNull
  public abstract ModCommandAction createReplaceCaseDefaultWithDefaultFix(@NotNull PsiCaseLabelElementList list);


  @NotNull
  public abstract ModCommandAction createReverseCaseDefaultNullFixFix(@NotNull PsiCaseLabelElementList list);

  @ApiStatus.Experimental
  @NotNull
  public abstract IntentionAction createAddMainMethodFix(@NotNull PsiImplicitClass implicitClass);
}