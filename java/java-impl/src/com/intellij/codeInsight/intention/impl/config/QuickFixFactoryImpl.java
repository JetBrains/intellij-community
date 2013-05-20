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

import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.daemon.quickFix.CreateClassOrPackageFix;
import com.intellij.codeInsight.daemon.quickFix.CreateFieldOrPropertyFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyMemberType;
import com.intellij.psi.util.ClassKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author cdr
 */
public class QuickFixFactoryImpl extends QuickFixFactory {
  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createModifierListFix(@NotNull PsiModifierList modifierList,
                                                                           @NotNull String modifier,
                                                                           boolean shouldHave,
                                                                           boolean showContainingClass) {
    return new ModifierFix(modifierList, modifier, shouldHave,showContainingClass);
  }

  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createModifierListFix(@NotNull PsiModifierListOwner owner,
                                                                           @NotNull final String modifier,
                                                                           final boolean shouldHave,
                                                                           final boolean showContainingClass) {
    return new ModifierFix(owner, modifier, shouldHave, showContainingClass);
  }

  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createMethodReturnFix(@NotNull PsiMethod method,
                                                                           @NotNull PsiType toReturn,
                                                                           boolean fixWholeHierarchy) {
    return new MethodReturnTypeFix(method, toReturn, fixWholeHierarchy);
  }

  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createAddMethodFix(@NotNull PsiMethod method, @NotNull PsiClass toClass) {
    return new AddMethodFix(method, toClass);
  }

  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createAddMethodFix(@NotNull String methodText,
                                                                        @NotNull PsiClass toClass,
                                                                        String... exceptions) {
    return new AddMethodFix(methodText, toClass, exceptions);
  }

  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createImplementMethodsFix(@NotNull PsiClass aClass) {
    return new ImplementMethodsFix(aClass);
  }

  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createImplementMethodsFix(@NotNull PsiElement psiElement) {
    return new ImplementMethodsFix(psiElement);
  }

  @Override
  public LocalQuickFixOnPsiElement createMethodThrowsFix(@NotNull PsiMethod method,
                                                         @NotNull PsiClassType exceptionClass,
                                                         boolean shouldThrow,
                                                         boolean showContainingClass) {
    return new MethodThrowsFix(method, exceptionClass, shouldThrow, showContainingClass);
  }

  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createAddDefaultConstructorFix(@NotNull PsiClass aClass) {
    return new AddDefaultConstructorFix(aClass);
  }

  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createAddConstructorFix(@NotNull PsiClass aClass, String modifier) {
    return aClass.getName() != null ? new AddDefaultConstructorFix(aClass, modifier) : null;
  }

  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createMethodParameterTypeFix(@NotNull PsiMethod method,
                                                                                  int index,
                                                                                  @NotNull PsiType newType,
                                                                                  boolean fixWholeHierarchy) {
    return new MethodParameterFix(method, newType, index, fixWholeHierarchy);
  }

  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createMakeClassInterfaceFix(@NotNull PsiClass aClass) {
    return new MakeClassInterfaceFix(aClass, true);
  }

  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createMakeClassInterfaceFix(@NotNull PsiClass aClass, final boolean makeInterface) {
    return new MakeClassInterfaceFix(aClass, makeInterface);
  }

  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createExtendsListFix(@NotNull PsiClass aClass,
                                                                          @NotNull PsiClassType typeToExtendFrom,
                                                                          boolean toAdd) {
    return new ExtendsListFix(aClass, typeToExtendFrom, toAdd);
  }

  @Override
  public LocalQuickFixAndIntentionActionOnPsiElement createRemoveUnusedParameterFix(@NotNull PsiParameter parameter) {
    return new RemoveUnusedParameterFix(parameter);
  }

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

  @Override
  public IntentionAction createCreateFieldOrPropertyFix(final PsiClass aClass, final String name, final PsiType type, final PropertyMemberType targetMember, final PsiAnnotation... annotations) {
    return new CreateFieldOrPropertyFix(aClass, name, type, targetMember, annotations);
  }

}
