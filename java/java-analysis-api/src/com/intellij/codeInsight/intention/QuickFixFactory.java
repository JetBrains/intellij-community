// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.*;

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
  public abstract @NotNull LocalQuickFixAndIntentionActionOnPsiElement createModifierListFix(@NotNull PsiModifierList modifierList,
                                                                                    @PsiModifier.ModifierConstant @NotNull String modifier,
                                                                                    boolean shouldHave,
                                                                                    final boolean showContainingClass);

  /**
   * @see JvmElementActionsFactory#createChangeModifierActions(com.intellij.lang.jvm.JvmModifiersOwner, com.intellij.lang.jvm.actions.ChangeModifierRequest
   * for jvm language transparent fix
   */
  public abstract @NotNull LocalQuickFixAndIntentionActionOnPsiElement createModifierListFix(@NotNull PsiModifierListOwner owner,
                                                                                    @PsiModifier.ModifierConstant @NotNull String modifier,
                                                                                    boolean shouldHave,
                                                                                    final boolean showContainingClass);

  public abstract @NotNull LocalQuickFixAndIntentionActionOnPsiElement createMethodReturnFix(@NotNull PsiMethod method,
                                                                                             @NotNull PsiType toReturn,
                                                                                             boolean fixWholeHierarchy);

  public abstract @NotNull LocalQuickFixAndIntentionActionOnPsiElement createMethodReturnFix(@NotNull PsiMethod method,
                                                                                             @NotNull PsiType toReturn,
                                                                                             boolean fixWholeHierarchy,
                                                                                             boolean suggestSuperTypes);

  public abstract @NotNull LocalQuickFixAndIntentionActionOnPsiElement createAnnotationMethodReturnFix(@NotNull PsiMethod method,
                                                                                                       @NotNull PsiType toReturn,
                                                                                                       boolean fromDefaultValue);

  /**
   * @param psiElement psiClass or enum constant without class initializer
   */
  public abstract @NotNull LocalQuickFixAndIntentionActionOnPsiElement createImplementMethodsFix(@NotNull PsiElement psiElement);

  public abstract @NotNull IntentionAction createAssignmentToComparisonFix(@NotNull PsiAssignmentExpression expr);

  public abstract @NotNull LocalQuickFixAndIntentionActionOnPsiElement createImplementMethodsFix(@NotNull PsiClass psiElement);

  public abstract @NotNull LocalQuickFixAndIntentionActionOnPsiElement createMethodThrowsFix(@NotNull PsiMethod method,
                                                                                             @NotNull PsiClassType exceptionClass,
                                                                                             boolean shouldThrow,
                                                                                             boolean showContainingClass);

  public abstract @NotNull LocalQuickFixAndIntentionActionOnPsiElement createAddDefaultConstructorFix(@NotNull PsiClass aClass);

  public abstract @NotNull LocalQuickFixAndIntentionActionOnPsiElement createMethodParameterTypeFix(@NotNull PsiMethod method,
                                                                                                    int index,
                                                                                                    @NotNull PsiType newType,
                                                                                                    boolean fixWholeHierarchy);

  public abstract @NotNull ModCommandAction createExtendsListFix(@NotNull PsiClass aClass,
                                                                 @NotNull PsiClassType typeToExtendFrom,
                                                                 boolean toAdd);

  public abstract @NotNull LocalQuickFixAndIntentionActionOnPsiElement createRemoveUnusedParameterFix(@NotNull PsiParameter parameter);

  public abstract @NotNull IntentionAction createRemoveUnusedVariableFix(@NotNull PsiVariable variable);

  public abstract @Nullable IntentionAction createCreateClassOrPackageFix(@NotNull PsiElement context,
                                                                          @NotNull String qualifiedName,
                                                                          final boolean createClass,
                                                                          final String superClass);

  public abstract @Nullable IntentionAction createCreateClassOrInterfaceFix(@NotNull PsiElement context,
                                                                            @NotNull String qualifiedName,
                                                                            final boolean createClass,
                                                                            final String superClass);

  public abstract @NotNull IntentionAction createCreateFieldOrPropertyFix(@NotNull PsiClass aClass,
                                                                          @NotNull String name,
                                                                          @NotNull PsiType type,
                                                                          @NotNull PropertyMemberType targetMember,
                                                                          PsiAnnotation @NotNull ... annotations);

  public abstract @NotNull IntentionAction createAddExceptionToCatchFix();

  public abstract @NotNull IntentionAction createAddExceptionToThrowsFix(@NotNull PsiElement element);
  
  public abstract @NotNull IntentionAction createAddExceptionToThrowsFix(@NotNull PsiElement element, 
                                                                         @NotNull Collection<PsiClassType> exceptionsToAdd);

  public abstract @NotNull IntentionAction createAddExceptionFromFieldInitializerToConstructorThrowsFix(@NotNull PsiElement element);

  public abstract @NotNull IntentionAction createSurroundWithTryCatchFix(@NotNull PsiElement element);

  public abstract @NotNull IntentionAction createAddExceptionToExistingCatch(@NotNull PsiElement element);

  public abstract @NotNull IntentionAction createChangeToAppendFix(@NotNull IElementType sign,
                                                                   @NotNull PsiType type,
                                                                   @NotNull PsiAssignmentExpression assignment);

  public abstract @NotNull IntentionAction createAddTypeCastFix(@NotNull PsiType type, @NotNull PsiExpression expression);

  public abstract @NotNull IntentionAction createReuseVariableDeclarationFix(@NotNull PsiLocalVariable variable);

  public abstract @NotNull IntentionAction createNavigateToAlreadyDeclaredVariableFix(@NotNull PsiVariable variable);

  public abstract @NotNull IntentionAction createNavigateToDuplicateElementFix(@NotNull NavigatablePsiElement element);

  public abstract @NotNull IntentionAction createConvertToStringLiteralAction();

  /**
   * Provides fix to remove return statement or return value in case when return statement is not last statement in block.
   *
   * @param method method with return statement
   * @param returnStatement statement to remove
   */
  public abstract @NotNull IntentionAction createDeleteReturnFix(@NotNull PsiMethod method, @NotNull PsiReturnStatement returnStatement);

  public abstract @NotNull IntentionAction createDeleteCatchFix(@NotNull PsiParameter parameter);

  public abstract @NotNull IntentionAction createDeleteMultiCatchFix(@NotNull PsiTypeElement element);

  public abstract @NotNull IntentionAction createConvertSwitchToIfIntention(@NotNull PsiSwitchStatement statement);

  public abstract @NotNull IntentionAction createNegationBroadScopeFix(@NotNull PsiPrefixExpression expr);

  public abstract @NotNull IntentionAction createCreateFieldFromUsageFix(@NotNull PsiReferenceExpression place);

  public abstract @NotNull IntentionAction createReplaceWithListAccessFix(@NotNull PsiArrayAccessExpression expression);

  public abstract @NotNull IntentionAction createAddNewArrayExpressionFix(@NotNull PsiArrayInitializerExpression expression);

  public abstract @NotNull IntentionAction createMoveCatchUpFix(@NotNull PsiCatchSection section, @NotNull PsiCatchSection section1);

  public abstract @NotNull IntentionAction createRenameWrongRefFix(@NotNull PsiReferenceExpression ref);

  public abstract @NotNull IntentionAction createRemoveQualifierFix(@NotNull PsiExpression qualifier,
                                                                    @NotNull PsiReferenceExpression expression,
                                                                    @NotNull PsiClass resolved);

  public abstract @NotNull IntentionAction createRemoveParameterListFix(@NotNull PsiMethod parent);

  public abstract @NotNull IntentionAction createShowModulePropertiesFix(@NotNull PsiElement element);

  public abstract @NotNull IntentionAction createShowModulePropertiesFix(@NotNull Module module);

  public abstract @NotNull IntentionAction createIncreaseLanguageLevelFix(@NotNull LanguageLevel level);

  public abstract @NotNull IntentionAction createUpgradeSdkFor(@NotNull LanguageLevel level);

  public abstract @NotNull IntentionAction createChangeParameterClassFix(@NotNull PsiClass aClass, @NotNull PsiClassType type);

  public abstract @NotNull IntentionAction createReplaceInaccessibleFieldWithGetterSetterFix(@NotNull PsiReferenceExpression element,
                                                                                             @NotNull PsiMethod getter,
                                                                                             boolean isSetter);

  public abstract @NotNull IntentionAction createSurroundWithArrayFix(@Nullable PsiCall methodCall, @Nullable PsiExpression expression);

  public abstract @NotNull IntentionAction createImplementAbstractClassMethodsFix(@NotNull PsiElement elementToHighlight);

  public abstract @NotNull IntentionAction createMoveClassToSeparateFileFix(@NotNull PsiClass aClass);

  public abstract @NotNull IntentionAction createRenameFileFix(@NotNull String newName);

  public abstract @Nullable IntentionAction createRenameFix(@NotNull PsiElement element);

  public abstract @NotNull LocalQuickFixAndIntentionActionOnPsiElement createRenameElementFix(@NotNull PsiNamedElement element);

  public abstract @NotNull LocalQuickFixAndIntentionActionOnPsiElement createRenameElementFix(@NotNull PsiNamedElement element,
                                                                                              @NotNull String newName);

  public abstract @NotNull IntentionAction createChangeExtendsToImplementsFix(@NotNull PsiClass aClass, @NotNull PsiClassType classToExtendFrom);

  public abstract @NotNull IntentionAction createCreateConstructorMatchingSuperFix(@NotNull PsiClass aClass);

  public abstract @NotNull IntentionAction createRemoveNewQualifierFix(@NotNull PsiNewExpression expression, @Nullable PsiClass aClass);

  public abstract @NotNull IntentionAction createSuperMethodReturnFix(@NotNull PsiMethod superMethod, @NotNull PsiType superMethodType);

  public abstract @NotNull IntentionAction createInsertNewFix(@NotNull PsiMethodCallExpression call, @NotNull PsiClass aClass);

  public abstract @NotNull IntentionAction createAddMethodBodyFix(@NotNull PsiMethod method);

  public abstract @NotNull IntentionAction createAddMethodBodyFix(@NotNull PsiMethod method, @NotNull @Nls String text);

  public abstract @NotNull IntentionAction createDeleteMethodBodyFix(@NotNull PsiMethod method);

  public abstract @NotNull IntentionAction createInsertSuperFix(@NotNull PsiMethod constructor);

  public abstract @NotNull IntentionAction createInsertThisFix(@NotNull PsiMethod constructor);

  public abstract @NotNull IntentionAction createChangeMethodSignatureFromUsageFix(@NotNull PsiMethod targetMethod,
                                                                                   PsiExpression @NotNull [] expressions,
                                                                                   @NotNull PsiSubstitutor substitutor,
                                                                                   @NotNull PsiElement context,
                                                                                   boolean changeAllUsages, int minUsagesNumberToShowDialog);

  public abstract @NotNull IntentionAction createChangeMethodSignatureFromUsageReverseOrderFix(@NotNull PsiMethod targetMethod,
                                                                                               PsiExpression @NotNull [] expressions,
                                                                                               @NotNull PsiSubstitutor substitutor,
                                                                                               @NotNull PsiElement context,
                                                                                               boolean changeAllUsages,
                                                                                               int minUsagesNumberToShowDialog);

  public @NotNull List<IntentionAction> createCreateMethodFromUsageFixes(@NotNull PsiMethodCallExpression call) {
    return Collections.emptyList();
  }

  public abstract @NotNull IntentionAction createCreateMethodFromUsageFix(@NotNull PsiMethodReferenceExpression methodReferenceExpression);

  public @NotNull List<IntentionAction> createCreateConstructorFromCallExpressionFixes(@NotNull PsiMethodCallExpression call) {
    return Collections.emptyList();
  }

  public abstract @NotNull IntentionAction createReplaceWithTypePatternFix(@NotNull PsiReferenceExpression exprToReplace,
                                                                           @NotNull PsiClass resolvedExprClass,
                                                                           @NotNull String patternVarName);

  public abstract @NotNull IntentionAction createStaticImportMethodFix(@NotNull PsiMethodCallExpression call);

  public abstract @NotNull IntentionAction createQualifyStaticMethodCallFix(@NotNull PsiMethodCallExpression call);

  public abstract @NotNull IntentionAction createReplaceAddAllArrayToCollectionFix(@NotNull PsiMethodCallExpression call);

  public @NotNull List<IntentionAction> createCreateConstructorFromUsageFixes(@NotNull PsiConstructorCall call) {
    return Collections.emptyList();
  }

  public abstract @NotNull List<IntentionAction> getVariableTypeFromCallFixes(@NotNull PsiMethodCallExpression call,
                                                                              @NotNull PsiExpressionList list);

  public abstract @NotNull IntentionAction createAddReturnFix(@NotNull PsiParameterListOwner methodOrLambda);

  public abstract @NotNull IntentionAction createAddVariableInitializerFix(@NotNull PsiVariable variable);

  public abstract @NotNull IntentionAction createDeferFinalAssignmentFix(@NotNull PsiVariable variable, @NotNull PsiReferenceExpression expression);

  public abstract @NotNull IntentionAction createVariableAccessFromInnerClassFix(@NotNull PsiVariable variable, @NotNull PsiElement scope);

  public abstract @NotNull IntentionAction createCreateConstructorParameterFromFieldFix(@NotNull PsiField field);

  public abstract @NotNull IntentionAction createInitializeFinalFieldInConstructorFix(@NotNull PsiField field);

  public abstract @NotNull IntentionAction createChangeClassSignatureFromUsageFix(@NotNull PsiClass owner,
                                                                                  @NotNull PsiReferenceParameterList parameterList);

  public abstract @NotNull IntentionAction createReplacePrimitiveWithBoxedTypeAction(@NotNull PsiTypeElement element,
                                                                                     @NotNull String typeName,
                                                                                     @NotNull String boxedTypeName);

  public abstract @Nullable IntentionAction createReplacePrimitiveWithBoxedTypeAction(@NotNull PsiType operandType,
                                                                                      @NotNull PsiTypeElement checkTypeElement);

  public abstract @NotNull IntentionAction createMakeVarargParameterLastFix(@NotNull PsiVariable parameter);

  public abstract @NotNull IntentionAction createMakeReceiverParameterFirstFix(@NotNull PsiReceiverParameter parameter);

  public abstract @NotNull IntentionAction createMoveBoundClassToFrontFix(@NotNull PsiTypeParameter aClass, @NotNull PsiClassType type);

  public abstract void registerPullAsAbstractUpFixes(@NotNull PsiMethod method, @NotNull List<? super IntentionAction> registrar);

  public abstract @NotNull IntentionAction createCreateAnnotationMethodFromUsageFix(@NotNull PsiNameValuePair pair);

  public abstract @NotNull ModCommandAction createOptimizeImportsFix(boolean fixOnTheFly, @NotNull PsiFile file);

  public abstract @NotNull IntentionAction createSafeDeleteUnusedParameterInHierarchyFix(@NotNull PsiParameter parameter, boolean excludingHierarchy);

  public abstract @NotNull IntentionAction createAddToDependencyInjectionAnnotationsFix(@NotNull Project project, @NotNull String qualifiedName);

  public abstract @NotNull IntentionAction createAddToImplicitlyWrittenFieldsFix(@NotNull Project project, @NotNull String qualifiedName);

  public abstract @NotNull IntentionAction createCreateGetterOrSetterFix(boolean createGetter, boolean createSetter, @NotNull PsiField field);

  public abstract @NotNull IntentionAction createRenameToIgnoredFix(@NotNull PsiVariable namedElement, boolean useElementNameAsSuffix);

  public abstract @NotNull IntentionAction createEnableOptimizeImportsOnTheFlyFix();

  public abstract @NotNull IntentionAction createDeleteFix(@NotNull PsiElement @NotNull ... elements);

  public abstract @NotNull LocalQuickFixAndIntentionActionOnPsiElement createDeleteFix(@NotNull PsiElement element);

  public abstract @NotNull LocalQuickFixAndIntentionActionOnPsiElement createDeleteFix(@NotNull PsiElement element, @NotNull @Nls String text);

  public abstract @NotNull IntentionAction createDeleteSideEffectAwareFix(@NotNull PsiExpressionStatement statement);

  public abstract @NotNull IntentionAction createSafeDeleteFix(@NotNull PsiElement element);

  /**
   * @param method method to delete
   * @return a fix to remove private method, possibly along with called methods unused elsewhere
   */
  public abstract @NotNull ModCommandAction createDeletePrivateMethodFix(@NotNull PsiMethod method);

  public abstract @NotNull List<@NotNull LocalQuickFix> registerOrderEntryFixes(@NotNull PsiReference reference,
                                                                                @NotNull List<? super IntentionAction> registrar);

  /**
   * @param annotationMethods unused, could be empty array
   */
  public abstract @NotNull IntentionAction createAddMissingRequiredAnnotationParametersFix(@NotNull PsiAnnotation annotation,
                                                                                           PsiMethod @NotNull [] annotationMethods,
                                                                                           @NotNull Collection<String> missedElements);

  public abstract @NotNull IntentionAction createSurroundWithQuotesAnnotationParameterValueFix(@NotNull PsiAnnotationMemberValue value,
                                                                                               @NotNull PsiType expectedType);

  public abstract @NotNull IntentionAction addMethodQualifierFix(@NotNull PsiMethodCallExpression methodCall);

  public abstract @NotNull IntentionAction createWrapWithOptionalFix(@Nullable PsiType type, @NotNull PsiExpression expression);

  public abstract @Nullable IntentionAction createNotIterableForEachLoopFix(@NotNull PsiExpression expression);

  public abstract @NotNull @Unmodifiable List<IntentionAction> createAddAnnotationAttributeNameFixes(@NotNull PsiNameValuePair pair);

  public abstract @NotNull IntentionAction createCollectionToArrayFix(@NotNull PsiExpression collectionExpression,
                                                                      @NotNull PsiExpression expressionToReplace,
                                                                      @NotNull PsiArrayType arrayType);

  public abstract @NotNull IntentionAction createInsertMethodCallFix(@NotNull PsiMethodCallExpression call, @NotNull PsiMethod method);

  public abstract @NotNull LocalQuickFixAndIntentionActionOnPsiElement createAccessStaticViaInstanceFix(@NotNull PsiReferenceExpression methodRef,
                                                                                                        @NotNull JavaResolveResult result);

  public abstract @NotNull ModCommandAction createWrapWithAdapterFix(@Nullable PsiType type, @NotNull PsiExpression expression);

  public abstract @Nullable IntentionAction createCreateClassInPackageInModuleFix(@NotNull Module module, @Nullable String packageName);

  public abstract @NotNull IntentionAction createPushDownMethodFix();

  public abstract @NotNull IntentionAction createSameErasureButDifferentMethodsFix(@NotNull PsiMethod method, @NotNull PsiMethod superMethod);

  public abstract @NotNull IntentionAction createAddMissingEnumBranchesFix(@NotNull PsiSwitchBlock switchBlock, @NotNull Set<String> missingCases);

  public abstract @NotNull IntentionAction createAddMissingSealedClassBranchesFix(@NotNull PsiSwitchBlock switchBlock,
                                                                                  @NotNull @Unmodifiable Set<String> missingCases,
                                                                                  @NotNull List<String> allNames);

  public abstract @Nullable IntentionAction createAddMissingRecordClassBranchesFix(@NotNull PsiSwitchBlock switchBlock,
                                                                                   @NotNull PsiClass selectorType,
                                                                                   @NotNull Map<PsiType, Set<List<PsiType>>> branches,
                                                                                   @NotNull List<? extends PsiCaseLabelElement> elements);

  public abstract @Nullable IntentionAction createAddMissingSealedClassBranchesFixWithNull(@NotNull PsiSwitchBlock switchBlock,
                                                                                           @NotNull Set<String> missingCases,
                                                                                           @NotNull List<String> allNames);

  public abstract @Nullable IntentionAction createAddMissingBooleanPrimitiveBranchesFix(@NotNull PsiSwitchBlock block);

  public abstract @Nullable IntentionAction createAddMissingBooleanPrimitiveBranchesFixWithNull(@NotNull PsiSwitchBlock block);

  public abstract @NotNull IntentionAction createAddSwitchDefaultFix(@NotNull PsiSwitchBlock switchBlock, @Nullable String message);

  public abstract @Nullable IntentionAction createCollapseAnnotationsFix(@NotNull PsiAnnotation annotation);

  public abstract @NotNull IntentionAction createChangeModifierFix();

  public abstract @NotNull IntentionAction createWrapSwitchRuleStatementsIntoBlockFix(@NotNull PsiSwitchLabeledRuleStatement rule);

  public abstract @NotNull IntentionAction createAddParameterListFix(@NotNull PsiMethod method);

  public abstract @NotNull IntentionAction createAddEmptyRecordHeaderFix(@NotNull PsiClass record);

  public abstract @NotNull IntentionAction createCreateFieldFromParameterFix(@NotNull PsiParameter parameter);
  public abstract @NotNull IntentionAction createAssignFieldFromParameterFix(@NotNull PsiParameter parameter);

  public abstract @NotNull IntentionAction createFillPermitsListFix(@NotNull PsiIdentifier classIdentifier);

  /**
   * @param subClass class that should be added to the parent permits list
   * @param superClass sealed parent class from subclasses' extends / implements clause
   */
  public abstract @NotNull IntentionAction createAddToPermitsListFix(@NotNull PsiClass subClass, @NotNull PsiClass superClass);

  public abstract @NotNull IntentionAction createMoveClassToPackageFix(@NotNull PsiClass classToMove, @NotNull String packageName);

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

  public abstract @Nullable IntentionAction createUnwrapArrayInitializerMemberValueAction(@NotNull PsiArrayInitializerMemberValue arrayValue);

  public abstract @NotNull IntentionAction createIntroduceVariableAction(@NotNull PsiExpression expression);

  public abstract @NotNull IntentionAction createInsertReturnFix(@NotNull PsiExpression expression);

  public abstract @NotNull IntentionAction createIterateFix(@NotNull PsiExpression expression);

  public abstract @NotNull IntentionAction createDeleteSwitchLabelFix(@NotNull PsiCaseLabelElement labelElement);

  /**
   * @param file ignored, can be null
   * @param defaultElement switch default element
   * @return fix to delete default element
   */
  public abstract @NotNull IntentionAction createDeleteDefaultFix(@Nullable PsiFile file, @NotNull PsiElement defaultElement);

  public abstract @NotNull IntentionAction createAddAnnotationTargetFix(@NotNull PsiAnnotation annotation, PsiAnnotation.TargetType target);

  public abstract @Nullable IntentionAction createMergeDuplicateAttributesFix(@NotNull PsiNameValuePair pair);

  public abstract @NotNull IntentionAction createMoveSwitchBranchUpFix(@NotNull PsiCaseLabelElement moveBeforeLabel,
                                                                       @NotNull PsiCaseLabelElement labelElement);

  public abstract @NotNull IntentionAction createSimplifyBooleanFix(@NotNull PsiExpression expression, boolean value);

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
  public abstract @NotNull IntentionAction createDeleteFix(@NotNull PsiElement @NotNull [] elements, @NotNull @Nls String text);

  public abstract @NotNull ModCommandAction createReplaceCaseDefaultWithDefaultFix(@NotNull PsiCaseLabelElementList list);


  public abstract @NotNull ModCommandAction createReverseCaseDefaultNullFixFix(@NotNull PsiCaseLabelElementList list);

  @ApiStatus.Experimental
  public abstract @NotNull IntentionAction createAddMainMethodFix(@NotNull PsiImplicitClass implicitClass);

  public abstract @NotNull ModCommandAction createReplaceOnDemandImport(@NotNull PsiImportModuleStatement importModuleStatement, @NotNull @Nls String text);
}