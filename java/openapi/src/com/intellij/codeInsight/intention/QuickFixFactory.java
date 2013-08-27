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
package com.intellij.codeInsight.intention;

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyMemberType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
}
