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
import java.util.List;

/**
 * @author cdr
 */
public abstract class QuickFixFactory {
  public static QuickFixFactory getInstance() {
    return ServiceManager.getService(QuickFixFactory.class);
  }

  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createModifierListFix(@NotNull PsiModifierList modifierList,
                                                                                    @PsiModifier.ModifierConstant @NotNull String modifier,
                                                                                    boolean shouldHave,
                                                                                    final boolean showContainingClass);
  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createModifierListFix(@NotNull PsiModifierListOwner owner,
                                                                                    @PsiModifier.ModifierConstant @NotNull String modifier,
                                                                                    boolean shouldHave,
                                                                                    final boolean showContainingClass);
  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createMethodReturnFix(@NotNull PsiMethod method, @NotNull PsiType toReturn, boolean fixWholeHierarchy);
  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createAddMethodFix(@NotNull PsiMethod method, @NotNull PsiClass toClass);
  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createAddMethodFix(@NotNull String methodText, @NotNull PsiClass toClass, @NotNull String... exceptions);

  /**
   * @param psiElement psiClass or enum constant without class initializer
   */
  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createImplementMethodsFix(@NotNull PsiElement psiElement);
  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createAssignmentToComparisonFix(PsiAssignmentExpression expr);
  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createImplementMethodsFix(@NotNull PsiClass psiElement);
  @NotNull
  public abstract LocalQuickFixOnPsiElement createMethodThrowsFix(@NotNull PsiMethod method, @NotNull PsiClassType exceptionClass, boolean shouldThrow, boolean showContainingClass);
  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createAddDefaultConstructorFix(@NotNull PsiClass aClass);
  @Nullable
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createAddConstructorFix(@NotNull PsiClass aClass, @PsiModifier.ModifierConstant @NotNull String modifier);
  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createMethodParameterTypeFix(@NotNull PsiMethod method, int index, @NotNull PsiType newType, boolean fixWholeHierarchy);
  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createMakeClassInterfaceFix(@NotNull PsiClass aClass);
  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createMakeClassInterfaceFix(@NotNull PsiClass aClass, final boolean makeInterface);
  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createExtendsListFix(@NotNull PsiClass aClass, @NotNull PsiClassType typeToExtendFrom, boolean toAdd);
  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createRemoveUnusedParameterFix(@NotNull PsiParameter parameter);
  @NotNull
  public abstract IntentionAction createRemoveUnusedVariableFix(@NotNull PsiVariable variable);

  @Nullable
  public abstract IntentionAction createCreateClassOrPackageFix(@NotNull PsiElement context, @NotNull String qualifiedName, final boolean createClass, final String superClass);
  @Nullable
  public abstract IntentionAction createCreateClassOrInterfaceFix(@NotNull PsiElement context, @NotNull String qualifiedName, final boolean createClass, final String superClass);
  @NotNull
  public abstract IntentionAction createCreateFieldOrPropertyFix(@NotNull PsiClass aClass, @NotNull String name, @NotNull PsiType type, @NotNull PropertyMemberType targetMember, @NotNull PsiAnnotation... annotations);

  @NotNull
  public abstract IntentionAction createSetupJDKFix();

  @NotNull public abstract IntentionAction createAddExceptionToCatchFix();

  @NotNull public abstract IntentionAction createAddExceptionToThrowsFix(@NotNull PsiElement element);

  @NotNull public abstract IntentionAction createAddExceptionFromFieldInitializerToConstructorThrowsFix(@NotNull PsiElement element);

  @NotNull public abstract IntentionAction createSurroundWithTryCatchFix(@NotNull PsiElement element);

  @NotNull public abstract IntentionAction createGeneralizeCatchFix(@NotNull PsiElement element, @NotNull PsiClassType type);

  @NotNull public abstract IntentionAction createChangeToAppendFix(@NotNull IElementType sign, @NotNull PsiType type, @NotNull PsiAssignmentExpression assignment);

  @NotNull public abstract IntentionAction createAddTypeCastFix(@NotNull PsiType type, @NotNull PsiExpression expression);

  @NotNull public abstract IntentionAction createWrapExpressionFix(@NotNull PsiType type, @NotNull PsiExpression expression);

  @NotNull public abstract IntentionAction createReuseVariableDeclarationFix(@NotNull PsiLocalVariable variable);
  @NotNull public abstract IntentionAction createNavigateToAlreadyDeclaredVariableFix(@NotNull PsiLocalVariable variable);

  @NotNull public abstract IntentionAction createConvertToStringLiteralAction();

  @NotNull public abstract IntentionAction createDeleteCatchFix(@NotNull PsiParameter parameter);

  @NotNull public abstract IntentionAction createDeleteMultiCatchFix(@NotNull PsiTypeElement element);

  @NotNull public abstract IntentionAction createConvertSwitchToIfIntention(@NotNull PsiSwitchStatement statement);

  @NotNull public abstract IntentionAction createNegationBroadScopeFix(@NotNull PsiPrefixExpression expr);

  @NotNull public abstract IntentionAction createCreateFieldFromUsageFix(@NotNull PsiReferenceExpression place);

  @NotNull public abstract IntentionAction createReplaceWithListAccessFix(@NotNull PsiArrayAccessExpression expression);

  @NotNull public abstract IntentionAction createAddNewArrayExpressionFix(@NotNull PsiArrayInitializerExpression expression);

  @NotNull public abstract IntentionAction createMoveCatchUpFix(@NotNull PsiCatchSection section, @NotNull PsiCatchSection section1);

  @NotNull public abstract IntentionAction createRenameWrongRefFix(@NotNull PsiReferenceExpression ref);

  @NotNull public abstract IntentionAction createRemoveQualifierFix(@NotNull PsiExpression qualifier, @NotNull PsiReferenceExpression expression, @NotNull PsiClass resolved);

  @NotNull public abstract IntentionAction createRemoveParameterListFix(@NotNull PsiMethod parent);

  @NotNull public abstract IntentionAction createShowModulePropertiesFix(@NotNull PsiElement element);
  @NotNull public abstract IntentionAction createShowModulePropertiesFix(@NotNull Module module);

  @NotNull public abstract IntentionAction createIncreaseLanguageLevelFix(@NotNull LanguageLevel level);

  @NotNull public abstract IntentionAction createChangeParameterClassFix(@NotNull PsiClass aClass, @NotNull PsiClassType type);

  @NotNull public abstract IntentionAction createReplaceInaccessibleFieldWithGetterSetterFix(@NotNull PsiElement element, @NotNull PsiMethod getter, boolean isSetter);

  @NotNull public abstract IntentionAction createSurroundWithArrayFix(@Nullable PsiCall methodCall, @Nullable PsiExpression expression);

  @NotNull public abstract IntentionAction createImplementAbstractClassMethodsFix(@NotNull PsiElement elementToHighlight);

  @NotNull public abstract IntentionAction createMoveClassToSeparateFileFix(@NotNull PsiClass aClass);

  @NotNull public abstract IntentionAction createRenameFileFix(@NotNull String newName);

  @NotNull public abstract LocalQuickFixAndIntentionActionOnPsiElement createRenameElementFix(@NotNull PsiNamedElement element);
  @NotNull public abstract LocalQuickFixAndIntentionActionOnPsiElement createRenameElementFix(@NotNull PsiNamedElement element, @NotNull String newName);

  @NotNull public abstract IntentionAction createChangeExtendsToImplementsFix(@NotNull PsiClass aClass, @NotNull PsiClassType classToExtendFrom);

  @NotNull public abstract IntentionAction createCreateConstructorMatchingSuperFix(@NotNull PsiClass aClass);

  @NotNull public abstract IntentionAction createRemoveNewQualifierFix(@NotNull PsiNewExpression expression, @Nullable PsiClass aClass);

  @NotNull public abstract IntentionAction createSuperMethodReturnFix(@NotNull PsiMethod superMethod, @NotNull PsiType superMethodType);

  @NotNull public abstract IntentionAction createInsertNewFix(@NotNull PsiMethodCallExpression call, @NotNull PsiClass aClass);

  @NotNull public abstract IntentionAction createAddMethodBodyFix(@NotNull PsiMethod method);

  @NotNull public abstract IntentionAction createDeleteMethodBodyFix(@NotNull PsiMethod method);

  @NotNull public abstract IntentionAction createInsertSuperFix(@NotNull PsiMethod constructor);

  @NotNull public abstract IntentionAction createInsertThisFix(@NotNull PsiMethod constructor);
  
  @NotNull public abstract IntentionAction createChangeMethodSignatureFromUsageFix(@NotNull PsiMethod targetMethod,
                                      @NotNull PsiExpression[] expressions,
                                      @NotNull PsiSubstitutor substitutor,
                                      @NotNull PsiElement context,
                                      boolean changeAllUsages, int minUsagesNumberToShowDialog);

  @NotNull public abstract IntentionAction createChangeMethodSignatureFromUsageReverseOrderFix(@NotNull PsiMethod targetMethod,
                                                  @NotNull PsiExpression[] expressions,
                                                  @NotNull PsiSubstitutor substitutor,
                                                  @NotNull PsiElement context,
                                                  boolean changeAllUsages,
                                                  int minUsagesNumberToShowDialog);

  @NotNull public abstract IntentionAction createCreateMethodFromUsageFix(@NotNull PsiMethodCallExpression call);
  @NotNull public abstract IntentionAction createCreateMethodFromUsageFix(PsiMethodReferenceExpression methodReferenceExpression);

  @NotNull public abstract IntentionAction createCreateAbstractMethodFromUsageFix(@NotNull PsiMethodCallExpression call);

  @NotNull public abstract IntentionAction createCreatePropertyFromUsageFix(@NotNull PsiMethodCallExpression call);

  @NotNull public abstract IntentionAction createCreateConstructorFromSuperFix(@NotNull PsiMethodCallExpression call);

  @NotNull public abstract IntentionAction createCreateConstructorFromThisFix(@NotNull PsiMethodCallExpression call);

  @NotNull public abstract IntentionAction createCreateGetterSetterPropertyFromUsageFix(@NotNull PsiMethodCallExpression call);

  @NotNull public abstract IntentionAction createStaticImportMethodFix(@NotNull PsiMethodCallExpression call);
  @NotNull public abstract IntentionAction createQualifyStaticMethodCallFix(@NotNull PsiMethodCallExpression call);

  @NotNull public abstract IntentionAction createReplaceAddAllArrayToCollectionFix(@NotNull PsiMethodCallExpression call);

  @NotNull public abstract IntentionAction createCreateConstructorFromCallFix(@NotNull PsiConstructorCall call);

  @NotNull
  public abstract List<IntentionAction> getVariableTypeFromCallFixes(@NotNull PsiMethodCallExpression call, @NotNull PsiExpressionList list);

  @NotNull public abstract IntentionAction createAddReturnFix(@NotNull PsiMethod method);

  @NotNull public abstract IntentionAction createAddVariableInitializerFix(@NotNull PsiVariable variable);

  @NotNull public abstract IntentionAction createDeferFinalAssignmentFix(@NotNull PsiVariable variable, @NotNull PsiReferenceExpression expression);

  @NotNull public abstract IntentionAction createVariableAccessFromInnerClassFix(@NotNull PsiVariable variable, @NotNull PsiElement scope);

  @NotNull public abstract IntentionAction createCreateConstructorParameterFromFieldFix(@NotNull PsiField field);

  @NotNull public abstract IntentionAction createInitializeFinalFieldInConstructorFix(@NotNull PsiField field);

  @NotNull public abstract IntentionAction createRemoveTypeArgumentsFix(@NotNull PsiElement variable);

  @NotNull public abstract IntentionAction createChangeClassSignatureFromUsageFix(@NotNull PsiClass owner, @NotNull PsiReferenceParameterList parameterList);

  @NotNull public abstract IntentionAction createReplacePrimitiveWithBoxedTypeAction(@NotNull PsiTypeElement element, @NotNull String typeName, @NotNull String boxedTypeName);

  @NotNull public abstract IntentionAction createMakeVarargParameterLastFix(@NotNull PsiParameter parameter);

  @NotNull public abstract IntentionAction createMoveBoundClassToFrontFix(@NotNull PsiClass aClass, @NotNull PsiClassType type);

  public abstract void registerPullAsAbstractUpFixes(@NotNull PsiMethod method, @NotNull QuickFixActionRegistrar registrar);

  @NotNull
  public abstract IntentionAction createCreateAnnotationMethodFromUsageFix(@NotNull PsiNameValuePair pair);

  @NotNull
  public abstract IntentionAction createOptimizeImportsFix(boolean onTheFly);

  public abstract void registerFixesForUnusedParameter(@NotNull PsiParameter parameter, @NotNull Object highlightInfo);

  @NotNull
  public abstract IntentionAction createAddToDependencyInjectionAnnotationsFix(@NotNull Project project, @NotNull String qualifiedName, @NotNull String element);

  @NotNull
  public abstract IntentionAction createAddToImplicitlyWrittenFieldsFix(Project project, @NotNull String qualifiedName);

  @NotNull
  public abstract IntentionAction createCreateGetterOrSetterFix(boolean createGetter, boolean createSetter, @NotNull PsiField field);

  @NotNull
  public abstract IntentionAction createRenameToIgnoredFix(@NotNull PsiNamedElement namedElement);

  @NotNull
  public abstract IntentionAction createEnableOptimizeImportsOnTheFlyFix();

  @NotNull public abstract LocalQuickFixAndIntentionActionOnPsiElement createDeleteFix(@NotNull PsiElement element);
  @NotNull public abstract LocalQuickFixAndIntentionActionOnPsiElement createDeleteFix(@NotNull PsiElement element, @NotNull @Nls String text);

  @NotNull
  public abstract IntentionAction createDeleteSideEffectAwareFix(@NotNull PsiExpressionStatement statement);

  @NotNull
  public abstract IntentionAction createSafeDeleteFix(@NotNull PsiElement element);

  @Nullable
  public abstract List<LocalQuickFix> registerOrderEntryFixes(@NotNull QuickFixActionRegistrar registrar, @NotNull PsiReference reference);

  @NotNull
  public abstract IntentionAction createAddMissingRequiredAnnotationParametersFix(@NotNull PsiAnnotation annotation,
                                                                                  @NotNull PsiMethod[] annotationMethods,
                                                                                  @NotNull Collection<String> missedElements);
  @NotNull
  public abstract IntentionAction createSurroundWithQuotesAnnotationParameterValueFix(@NotNull PsiAnnotationMemberValue value, @NotNull PsiType expectedType);

  @NotNull
  public abstract IntentionAction addMethodQualifierFix(@NotNull PsiMethodCallExpression methodCall);

  @NotNull
  public abstract IntentionAction createWrapWithOptionalFix(@Nullable PsiType type, @NotNull PsiExpression expression);

  @Nullable
  public abstract IntentionAction createNotIterableForEachLoopFix(@NotNull PsiExpression expression);

  @NotNull
  public abstract List<IntentionAction> createAddAnnotationAttributeNameFixes(@NotNull PsiNameValuePair pair);

  @NotNull
  public abstract IntentionAction createCollectionToArrayFix(@NotNull PsiExpression collectionExpression, @NotNull PsiArrayType arrayType);

  @NotNull
  public abstract IntentionAction createInsertMethodCallFix(@NotNull PsiMethodCallExpression call, PsiMethod method);

  @NotNull
  public abstract LocalQuickFixAndIntentionActionOnPsiElement createAccessStaticViaInstanceFix(PsiReferenceExpression methodRef, JavaResolveResult result);

  @NotNull
  public abstract IntentionAction createWrapWithAdapterFix(@Nullable PsiType type, @NotNull PsiExpression expression);
}