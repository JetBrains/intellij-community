// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.lang.jvm.actions.JvmElementActionsFactory;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PropertyMemberType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author cdr
 */
public abstract class QuickFixFactory {
  public static QuickFixFactory getInstance() {
    return ServiceManager.getService(QuickFixFactory.class);
  }

  /**
   * Consider to use
   * {@link QuickFixFactory#createModifierListFix(PsiModifierListOwner, String, boolean, boolean)} for java only fix or
   * {@link JvmElementActionsFactory#createChangeModifierActions(com.intellij.lang.jvm.JvmModifiersOwner, com.intellij.lang.jvm.actions.ChangeModifierRequest)}
   * for jvm languages transparent fix
   *
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
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createAddMethodFix(@NotNull PsiMethod method, @NotNull PsiClass toClass);

  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createAddMethodFix(@NotNull String methodText,
                                                                                 @NotNull PsiClass toClass,
                                                                                 String @NotNull ... exceptions);

  /**
   * @param psiElement psiClass or enum constant without class initializer
   */
  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createImplementMethodsFix(@NotNull PsiElement psiElement);

  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createAssignmentToComparisonFix(@NotNull PsiAssignmentExpression expr);

  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createImplementMethodsFix(@NotNull PsiClass psiElement);

  @NotNull
  public abstract LocalQuickFixOnPsiElement createMethodThrowsFix(@NotNull PsiMethod method,
                                                                  @NotNull PsiClassType exceptionClass,
                                                                  boolean shouldThrow,
                                                                  boolean showContainingClass);

  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createAddDefaultConstructorFix(@NotNull PsiClass aClass);

  @Nullable
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createAddConstructorFix(@NotNull PsiClass aClass,
                                                                                      @PsiModifier.ModifierConstant @NotNull String modifier);

  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createMethodParameterTypeFix(@NotNull PsiMethod method,
                                                                                           int index,
                                                                                           @NotNull PsiType newType,
                                                                                           boolean fixWholeHierarchy);

  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createMakeClassInterfaceFix(@NotNull PsiClass aClass,
                                                                                          final boolean makeInterface);

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
  public abstract IntentionAction createWrapExpressionFix(@NotNull PsiType type, @NotNull PsiExpression expression);

  @NotNull
  public abstract IntentionAction createReuseVariableDeclarationFix(@NotNull PsiLocalVariable variable);

  @NotNull
  public abstract IntentionAction createNavigateToAlreadyDeclaredVariableFix(@NotNull PsiVariable variable);

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
  public abstract IntentionAction createChangeParameterClassFix(@NotNull PsiClass aClass, @NotNull PsiClassType type);

  @NotNull
  public abstract IntentionAction createReplaceInaccessibleFieldWithGetterSetterFix(@NotNull PsiElement element,
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
  public abstract IntentionAction createRemoveTypeArgumentsFix(@NotNull PsiElement variable);

  @NotNull
  public abstract IntentionAction createChangeClassSignatureFromUsageFix(@NotNull PsiClass owner,
                                                                         @NotNull PsiReferenceParameterList parameterList);

  @NotNull
  public abstract IntentionAction createReplacePrimitiveWithBoxedTypeAction(@NotNull PsiTypeElement element,
                                                                            @NotNull String typeName,
                                                                            @NotNull String boxedTypeName);

  @NotNull
  public abstract IntentionAction createMakeVarargParameterLastFix(@NotNull PsiParameter parameter);

  @NotNull
  public abstract IntentionAction createMoveBoundClassToFrontFix(@NotNull PsiClass aClass, @NotNull PsiClassType type);

  public abstract void registerPullAsAbstractUpFixes(@NotNull PsiMethod method, @NotNull QuickFixActionRegistrar registrar);

  @NotNull
  public abstract IntentionAction createCreateAnnotationMethodFromUsageFix(@NotNull PsiNameValuePair pair);

  @NotNull
  public abstract IntentionAction createOptimizeImportsFix(boolean onTheFly);

  public abstract void registerFixesForUnusedParameter(@NotNull PsiParameter parameter, @NotNull Object highlightInfo);

  /**
   * @deprecated Use {@link #createAddToDependencyInjectionAnnotationsFix(Project, String)} instead
   */
  @Deprecated
  @NotNull
  public IntentionAction createAddToDependencyInjectionAnnotationsFix(@NotNull Project project,
                                                                      @NotNull String qualifiedName,
                                                                      @NotNull String element) {
    return createAddToDependencyInjectionAnnotationsFix(project, qualifiedName);
  }

  @NotNull
  public abstract IntentionAction createAddToDependencyInjectionAnnotationsFix(@NotNull Project project, @NotNull String qualifiedName);

  @NotNull
  public abstract IntentionAction createAddToImplicitlyWrittenFieldsFix(@NotNull Project project, @NotNull String qualifiedName);

  @NotNull
  public abstract IntentionAction createCreateGetterOrSetterFix(boolean createGetter, boolean createSetter, @NotNull PsiField field);

  @NotNull
  public abstract IntentionAction createRenameToIgnoredFix(@NotNull PsiNamedElement namedElement);

  @NotNull
  public abstract IntentionAction createEnableOptimizeImportsOnTheFlyFix();

  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createDeleteFix(@NotNull PsiElement element);

  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createDeleteFix(@NotNull PsiElement element, @NotNull @Nls String text);

  @NotNull
  public abstract IntentionAction createDeleteSideEffectAwareFix(@NotNull PsiExpressionStatement statement);

  @NotNull
  public abstract IntentionAction createSafeDeleteFix(@NotNull PsiElement element);

  @Nullable
  public abstract List<LocalQuickFix> registerOrderEntryFixes(@NotNull QuickFixActionRegistrar registrar, @NotNull PsiReference reference);

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
  public IntentionAction createSameErasureButDifferentMethodsFix(@NotNull PsiMethod method, @NotNull PsiMethod superMethod) {
    throw new AbstractMethodError();
  }

  @NotNull
  public abstract IntentionAction createAddMissingEnumBranchesFix(@NotNull PsiSwitchBlock switchBlock, @NotNull Set<String> missingCases);

  @NotNull
  public abstract IntentionAction createAddSwitchDefaultFix(@NotNull PsiSwitchBlock switchBlock, @Nullable String message);

  @Nullable
  public abstract IntentionAction createCollapseAnnotationsFix(@NotNull PsiAnnotation annotation);

  @NotNull
  public abstract IntentionAction createChangeModifierFix();

  @NotNull
  public abstract IntentionAction createWrapSwitchRuleStatementsIntoBlockFix(PsiSwitchLabeledRuleStatement rule);
  
  @NotNull
  public abstract IntentionAction createAddParameterListFix(PsiMethod method);

  @NotNull
  public abstract IntentionAction createAddEmptyRecordHeaderFix(PsiClass record);
}