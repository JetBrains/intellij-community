/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.impl.analysis.IncreaseLanguageLevelFix;
import com.intellij.codeInsight.daemon.impl.quickfix.ReplacePrimitiveWithBoxedTypeAction;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.daemon.quickFix.CreateClassOrPackageFix;
import com.intellij.codeInsight.daemon.quickFix.CreateFieldOrPropertyFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PropertyMemberType;
import com.intellij.psi.util.ClassKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author cdr
 */
public class QuickFixFactoryImpl extends QuickFixFactory {
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
  public LocalQuickFixAndIntentionActionOnPsiElement createAddMethodFix(@NotNull PsiMethod method, @NotNull PsiClass toClass) {
    return new AddMethodFix(method, toClass);
  }

  @NotNull
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createAddMethodFix(@NotNull String methodText,
                                                                        @NotNull PsiClass toClass,
                                                                        @NotNull String... exceptions) {
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
  public LocalQuickFixOnPsiElement createMethodThrowsFix(@NotNull PsiMethod method,
                                                         @NotNull PsiClassType exceptionClass,
                                                         boolean shouldThrow,
                                                         boolean showContainingClass) {
    return new MethodThrowsFix(method, exceptionClass, shouldThrow, showContainingClass);
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
  public LocalQuickFixAndIntentionActionOnPsiElement createMakeClassInterfaceFix(@NotNull PsiClass aClass) {
    return new MakeClassInterfaceFix(aClass, true);
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
  public IntentionAction createCreateFieldOrPropertyFix(@NotNull final PsiClass aClass, @NotNull final String name, @NotNull final PsiType type, @NotNull final PropertyMemberType targetMember, @NotNull final PsiAnnotation... annotations) {
    return new CreateFieldOrPropertyFix(aClass, name, type, targetMember, annotations);
  }

  @NotNull
  @Override
  public IntentionAction createSetupJDKFix() {
    return SetupJDKFix.getInstance();
  }

  @NotNull
  @Override
  public IntentionAction createAddExceptionToCatchFix() {
    return new AddExceptionToCatchFix();
  }

  @NotNull
  @Override
  public IntentionAction createAddExceptionToThrowsFix(@NotNull PsiElement element) {
    return new AddExceptionToThrowsFix(element);
  }

  @NotNull
  @Override
  public IntentionAction createSurroundWithTryCatchFix(@NotNull PsiElement element) {
    return new SurroundWithTryCatchFix(element);
  }

  @NotNull
  @Override
  public IntentionAction createGeneralizeCatchFix(@NotNull PsiElement element, @NotNull PsiClassType type) {
    return new GeneralizeCatchFix(element, type);
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
  public IntentionAction createConvertToStringLiteralAction() {
    return new ConvertToStringLiteralAction();
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
  public IntentionAction createRenameElementFix(@NotNull PsiNamedElement element) {
    return new RenameElementFix(element);
  }

  @NotNull
  @Override
  public IntentionAction createRenameElementFix(@NotNull PsiNamedElement element, @NotNull String newName) {
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
  public IntentionAction createChangeMethodSignatureFromUsageFix(@NotNull PsiMethod targetMethod,
                                                                 @NotNull PsiExpression[] expressions,
                                                                 @NotNull PsiSubstitutor substitutor,
                                                                 @NotNull PsiElement context,
                                                                 boolean changeAllUsages,
                                                                 int minUsagesNumberToShowDialog) {
    return new ChangeMethodSignatureFromUsageFix(targetMethod, expressions, substitutor, context, changeAllUsages, minUsagesNumberToShowDialog);
  }

  @NotNull
  @Override
  public IntentionAction createChangeMethodSignatureFromUsageReverseOrderFix(@NotNull PsiMethod targetMethod,
                                                                             @NotNull PsiExpression[] expressions,
                                                                             @NotNull PsiSubstitutor substitutor,
                                                                             @NotNull PsiElement context,
                                                                             boolean changeAllUsages,
                                                                             int minUsagesNumberToShowDialog) {
    return new ChangeMethodSignatureFromUsageReverseOrderFix(targetMethod, expressions, substitutor, context, changeAllUsages, minUsagesNumberToShowDialog);
  }

  @NotNull
  @Override
  public IntentionAction createCreateMethodFromUsageFix(@NotNull PsiMethodCallExpression call) {
    return new CreateMethodFromUsageFix(call);
  }

  @NotNull
  @Override
  public IntentionAction createCreateAbstractMethodFromUsageFix(@NotNull PsiMethodCallExpression call) {
    return new CreateAbstractMethodFromUsageFix(call);
  }

  @NotNull
  @Override
  public IntentionAction createCreatePropertyFromUsageFix(@NotNull PsiMethodCallExpression call) {
    return new CreatePropertyFromUsageFix(call);
  }

  @NotNull
  @Override
  public IntentionAction createCreateConstructorFromSuperFix(@NotNull PsiMethodCallExpression call) {
    return new CreateConstructorFromSuperFix(call);
  }

  @NotNull
  @Override
  public IntentionAction createCreateConstructorFromThisFix(@NotNull PsiMethodCallExpression call) {
    return new CreateConstructorFromThisFix(call);
  }

  @NotNull
  @Override
  public IntentionAction createCreateGetterSetterPropertyFromUsageFix(@NotNull PsiMethodCallExpression call) {
    return new CreateGetterSetterPropertyFromUsageFix(call);
  }

  @NotNull
  @Override
  public IntentionAction createStaticImportMethodFix(@NotNull PsiMethodCallExpression call) {
    return new StaticImportMethodFix(call);
  }

  @NotNull
  @Override
  public IntentionAction createReplaceAddAllArrayToCollectionFix(@NotNull PsiMethodCallExpression call) {
    return new ReplaceAddAllArrayToCollectionFix(call);
  }

  @NotNull
  @Override
  public IntentionAction createCreateConstructorFromCallFix(@NotNull PsiConstructorCall call) {
    return new CreateConstructorFromCallFix(call);
  }

  @NotNull
  @Override
  public List<IntentionAction> getVariableTypeFromCallFixes(@NotNull PsiMethodCallExpression call, @NotNull PsiExpressionList list) {
    return VariableTypeFromCallFix.getQuickFixActions(call, list);
  }

  @NotNull
  @Override
  public IntentionAction createAddReturnFix(@NotNull PsiMethod method) {
    return new AddReturnFix(method);
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
  public IntentionAction createVariableAccessFromInnerClassFix(@NotNull PsiVariable variable, @NotNull PsiClass aClass) {
    return new VariableAccessFromInnerClassFix(variable, aClass);
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

  @Override
  public IntentionAction createCreateAnnotationMethodFromUsageFix(PsiNameValuePair pair) {
    return new CreateAnnotationMethodFromUsageFix(pair);
  }

}
