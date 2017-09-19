/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
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

@SuppressWarnings("UnusedDeclaration") // upsource
public class EmptyQuickFixFactory extends QuickFixFactory {
  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createModifierListFix(@NotNull PsiModifierList psiModifierList, @PsiModifier.ModifierConstant @NotNull String s, boolean b, boolean b2) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createModifierListFix(@NotNull PsiModifierListOwner psiModifierListOwner, @PsiModifier.ModifierConstant @NotNull String s, boolean b, boolean b2) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createMethodReturnFix(@NotNull PsiMethod psiMethod, @NotNull PsiType psiType, boolean b) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createAddMethodFix(@NotNull PsiMethod psiMethod, @NotNull PsiClass psiClass) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createAddMethodFix(@NotNull String s, @NotNull PsiClass psiClass, @NotNull String... strings) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createImplementMethodsFix(@NotNull PsiElement psiElement) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createAssignmentToComparisonFix(PsiAssignmentExpression expr) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createImplementMethodsFix(@NotNull PsiClass psiClass) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public LocalQuickFixOnPsiElement createMethodThrowsFix(@NotNull PsiMethod psiMethod, @NotNull PsiClassType psiClassType, boolean b, boolean b2) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createAddDefaultConstructorFix(@NotNull PsiClass psiClass) {
    return QuickFixes.EMPTY_FIX;
  }

  @Nullable
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createAddConstructorFix(@NotNull PsiClass psiClass, @PsiModifier.ModifierConstant @NotNull String s) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createMethodParameterTypeFix(@NotNull PsiMethod psiMethod, int i, @NotNull PsiType psiType, boolean b) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createMakeClassInterfaceFix(@NotNull PsiClass psiClass) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createMakeClassInterfaceFix(@NotNull PsiClass psiClass, boolean b) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createExtendsListFix(@NotNull PsiClass psiClass, @NotNull PsiClassType psiClassType, boolean b) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createRemoveUnusedParameterFix(@NotNull PsiParameter psiParameter) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createRemoveUnusedVariableFix(@NotNull PsiVariable psiVariable) {
    return QuickFixes.EMPTY_FIX;
  }

  @Nullable
  @Override
  public IntentionAction createCreateClassOrPackageFix(@NotNull PsiElement psiElement, @NotNull String s, boolean b, String s2) {
    return QuickFixes.EMPTY_FIX;
  }

  @Nullable
  @Override
  public IntentionAction createCreateClassOrInterfaceFix(@NotNull PsiElement psiElement, @NotNull String s, boolean b, String s2) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createCreateFieldOrPropertyFix(@NotNull PsiClass psiClass, @NotNull String s, @NotNull PsiType psiType, @NotNull PropertyMemberType propertyMemberType, @NotNull PsiAnnotation... psiAnnotations) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createSetupJDKFix() {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createAddExceptionToCatchFix() {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createAddExceptionToThrowsFix(@NotNull PsiElement psiElement) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createAddExceptionFromFieldInitializerToConstructorThrowsFix(@NotNull PsiElement element) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createSurroundWithTryCatchFix(@NotNull PsiElement psiElement) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createGeneralizeCatchFix(@NotNull PsiElement psiElement, @NotNull PsiClassType psiClassType) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createChangeToAppendFix(@NotNull IElementType iElementType, @NotNull PsiType psiType, @NotNull PsiAssignmentExpression psiAssignmentExpression) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createAddTypeCastFix(@NotNull PsiType psiType, @NotNull PsiExpression psiExpression) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createWrapExpressionFix(@NotNull PsiType psiType, @NotNull PsiExpression psiExpression) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createReuseVariableDeclarationFix(@NotNull PsiLocalVariable psiLocalVariable) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createNavigateToAlreadyDeclaredVariableFix(@NotNull PsiLocalVariable variable) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createConvertToStringLiteralAction() {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createDeleteCatchFix(@NotNull PsiParameter psiParameter) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createDeleteMultiCatchFix(@NotNull PsiTypeElement psiTypeElement) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createConvertSwitchToIfIntention(@NotNull PsiSwitchStatement psiSwitchStatement) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createNegationBroadScopeFix(@NotNull PsiPrefixExpression psiPrefixExpression) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createCreateFieldFromUsageFix(@NotNull PsiReferenceExpression psiReferenceExpression) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createReplaceWithListAccessFix(@NotNull PsiArrayAccessExpression psiArrayAccessExpression) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createAddNewArrayExpressionFix(@NotNull PsiArrayInitializerExpression psiArrayInitializerExpression) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createMoveCatchUpFix(@NotNull PsiCatchSection psiCatchSection, @NotNull PsiCatchSection psiCatchSection2) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createRenameWrongRefFix(@NotNull PsiReferenceExpression psiReferenceExpression) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createRemoveQualifierFix(@NotNull PsiExpression psiExpression, @NotNull PsiReferenceExpression psiReferenceExpression, @NotNull PsiClass psiClass) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createRemoveParameterListFix(@NotNull PsiMethod psiMethod) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createShowModulePropertiesFix(@NotNull PsiElement psiElement) {
    return QuickFixes.EMPTY_ACTION;
  }

  @NotNull
  @Override
  public IntentionAction createIncreaseLanguageLevelFix(@NotNull LanguageLevel languageLevel) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createChangeParameterClassFix(@NotNull PsiClass psiClass, @NotNull PsiClassType psiClassType) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createReplaceInaccessibleFieldWithGetterSetterFix(@NotNull PsiElement psiElement, @NotNull PsiMethod psiMethod, boolean b) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createSurroundWithArrayFix(@Nullable PsiCall psiCall, @Nullable PsiExpression psiExpression) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createImplementAbstractClassMethodsFix(@NotNull PsiElement psiElement) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createMoveClassToSeparateFileFix(@NotNull PsiClass psiClass) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createRenameFileFix(@NotNull String s) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createRenameElementFix(@NotNull PsiNamedElement psiNamedElement) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createRenameElementFix(@NotNull PsiNamedElement psiNamedElement, @NotNull String s) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createChangeExtendsToImplementsFix(@NotNull PsiClass psiClass, @NotNull PsiClassType psiClassType) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createCreateConstructorMatchingSuperFix(@NotNull PsiClass psiClass) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createRemoveNewQualifierFix(@NotNull PsiNewExpression psiNewExpression, @Nullable PsiClass psiClass) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createSuperMethodReturnFix(@NotNull PsiMethod psiMethod, @NotNull PsiType psiType) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createInsertNewFix(@NotNull PsiMethodCallExpression psiMethodCallExpression, @NotNull PsiClass psiClass) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createAddMethodBodyFix(@NotNull PsiMethod psiMethod) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createDeleteMethodBodyFix(@NotNull PsiMethod psiMethod) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createInsertThisFix(@NotNull PsiMethod constructor) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createInsertSuperFix(@NotNull PsiMethod psiMethod) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createChangeMethodSignatureFromUsageFix(@NotNull PsiMethod psiMethod, @NotNull PsiExpression[] psiExpressions, @NotNull PsiSubstitutor psiSubstitutor, @NotNull PsiElement psiElement, boolean b, int i) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createChangeMethodSignatureFromUsageReverseOrderFix(@NotNull PsiMethod psiMethod, @NotNull PsiExpression[] psiExpressions, @NotNull PsiSubstitutor psiSubstitutor, @NotNull PsiElement psiElement, boolean b, int i) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createCreateMethodFromUsageFix(@NotNull PsiMethodCallExpression psiMethodCallExpression) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createCreateAbstractMethodFromUsageFix(@NotNull PsiMethodCallExpression psiMethodCallExpression) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createCreatePropertyFromUsageFix(@NotNull PsiMethodCallExpression psiMethodCallExpression) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createCreateConstructorFromSuperFix(@NotNull PsiMethodCallExpression psiMethodCallExpression) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createCreateConstructorFromThisFix(@NotNull PsiMethodCallExpression psiMethodCallExpression) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createCreateGetterSetterPropertyFromUsageFix(@NotNull PsiMethodCallExpression psiMethodCallExpression) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createStaticImportMethodFix(@NotNull PsiMethodCallExpression psiMethodCallExpression) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createQualifyStaticMethodCallFix(@NotNull PsiMethodCallExpression call) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createReplaceAddAllArrayToCollectionFix(@NotNull PsiMethodCallExpression psiMethodCallExpression) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createCreateConstructorFromCallFix(@NotNull PsiConstructorCall psiConstructorCall) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public List<IntentionAction> getVariableTypeFromCallFixes(@NotNull PsiMethodCallExpression psiMethodCallExpression, @NotNull PsiExpressionList psiExpressionList) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public IntentionAction createAddReturnFix(@NotNull PsiMethod psiMethod) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createAddVariableInitializerFix(@NotNull PsiVariable psiVariable) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createDeferFinalAssignmentFix(@NotNull PsiVariable psiVariable, @NotNull PsiReferenceExpression psiReferenceExpression) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createCreateConstructorParameterFromFieldFix(@NotNull PsiField psiField) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createInitializeFinalFieldInConstructorFix(@NotNull PsiField psiField) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createRemoveTypeArgumentsFix(@NotNull PsiElement psiElement) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createChangeClassSignatureFromUsageFix(@NotNull PsiClass psiClass, @NotNull PsiReferenceParameterList psiReferenceParameterList) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createReplacePrimitiveWithBoxedTypeAction(@NotNull PsiTypeElement psiTypeElement, @NotNull String s, @NotNull String s2) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createMakeVarargParameterLastFix(@NotNull PsiParameter psiParameter) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createMoveBoundClassToFrontFix(@NotNull PsiClass psiClass, @NotNull PsiClassType psiClassType) {
    return QuickFixes.EMPTY_FIX;
  }

  @Override
  public void registerPullAsAbstractUpFixes(@NotNull PsiMethod psiMethod, @NotNull QuickFixActionRegistrar quickFixActionRegistrar) {
  }

  @NotNull
  @Override
  public IntentionAction createCreateAnnotationMethodFromUsageFix(@NotNull PsiNameValuePair psiNameValuePair) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createOptimizeImportsFix(boolean onTheFly) {
    return QuickFixes.EMPTY_FIX;
  }

  @Override
  public void registerFixesForUnusedParameter(@NotNull PsiParameter psiParameter, @NotNull Object o) {

  }

  @NotNull
  @Override
  public IntentionAction createAddToDependencyInjectionAnnotationsFix(@NotNull Project project, @NotNull String s, @NotNull String s2) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createAddToImplicitlyWrittenFieldsFix(Project project, @NotNull String qualifiedName) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createCreateGetterOrSetterFix(boolean b, boolean b2, @NotNull PsiField psiField) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createRenameToIgnoredFix(@NotNull PsiNamedElement psiNamedElement) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createEnableOptimizeImportsOnTheFlyFix() {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createDeleteFix(@NotNull PsiElement element) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createDeleteFix(@NotNull PsiElement element, @Nls @NotNull String text) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createSafeDeleteFix(@NotNull PsiElement psiElement) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createCreateMethodFromUsageFix(PsiMethodReferenceExpression methodReferenceExpression) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createVariableAccessFromInnerClassFix(@NotNull PsiVariable variable, @NotNull PsiElement scope) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createShowModulePropertiesFix(@NotNull Module module) {
    return QuickFixes.EMPTY_ACTION;
  }

  @Nullable
  @Override
  public List<LocalQuickFix> registerOrderEntryFixes(@NotNull QuickFixActionRegistrar registrar, @NotNull PsiReference reference) {
    return null;
  }

  @NotNull
  @Override
  public IntentionAction createAddMissingRequiredAnnotationParametersFix(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiMethod[] psiMethods, @NotNull Collection<String> strings) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createSurroundWithQuotesAnnotationParameterValueFix(@NotNull PsiAnnotationMemberValue value,
                                                                             @NotNull PsiType expectedType) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction addMethodQualifierFix(@NotNull PsiMethodCallExpression methodCall) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createWrapWithOptionalFix(@Nullable PsiType type, @NotNull PsiExpression expression) {
    return QuickFixes.EMPTY_FIX;
  }

  @Nullable
  @Override
  public IntentionAction createNotIterableForEachLoopFix(@NotNull PsiExpression expression) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public List<IntentionAction> createAddAnnotationAttributeNameFixes(@NotNull PsiNameValuePair pair) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public IntentionAction createCollectionToArrayFix(@NotNull PsiExpression collectionExpression,
                                                    @NotNull PsiExpression expressionToReplace,
                                                    @NotNull PsiArrayType arrayType) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createInsertMethodCallFix(@NotNull PsiMethodCallExpression call, PsiMethod method) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createAccessStaticViaInstanceFix(PsiReferenceExpression methodRef,
                                                                                      JavaResolveResult result) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createWrapWithAdapterFix(@Nullable PsiType type, @NotNull PsiExpression expression) {
    return QuickFixes.EMPTY_FIX;
  }

  @NotNull
  @Override
  public IntentionAction createDeleteSideEffectAwareFix(@NotNull PsiExpressionStatement statement) {
    return QuickFixes.EMPTY_FIX;
  }
}
