/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class QuickFixFactoryImpl extends QuickFixFactory {
  public IntentionAction createModifierListFix(PsiModifierList modifierList,
                                               String modifier,
                                               boolean shouldHave,
                                               final boolean showContainingClass) {
    return new ModifierFix(modifierList, modifier, shouldHave,showContainingClass);
  }

  public IntentionAction createMethodReturnFix(PsiMethod method, PsiType toReturn, boolean fixWholeHierarchy) {
    return new MethodReturnFix(method, toReturn, fixWholeHierarchy);
  }

  public IntentionAction createAddMethodFix(@NotNull PsiMethod method, @NotNull PsiClass toClass) {
    return new AddMethodFix(method, toClass);
  }

  public IntentionAction createAddMethodFix(@NotNull String methodText, @NotNull PsiClass toClass, String... exceptions) {
    return new AddMethodFix(methodText, toClass, exceptions);
  }

  public IntentionAction createImplementMethodsFix(PsiClass aClass) {
    return new ImplementMethodsFix(aClass);
  }

  public IntentionAction createMethodThrowsFix(@NotNull PsiMethod method,
                                               @NotNull PsiClassType exceptionClass,
                                               boolean shouldThrow,
                                               boolean showContainingClass) {
    return new MethodThrowsFix(method, exceptionClass, shouldThrow, showContainingClass);
  }

  public IntentionAction createAddDefaultConstructorFix(@NotNull PsiClass aClass) {
    return new AddDefaultConstructorFix(aClass);
  }

  public IntentionAction createMethodParameterTypeFix(@NotNull PsiMethod method,
                                                      int index,
                                                      @NotNull PsiType newType,
                                                      boolean fixWholeHierarchy) {
    return new MethodParameterFix(method, newType, index, fixWholeHierarchy);
  }

  public IntentionAction createMakeClassInterfaceFix(@NotNull PsiClass aClass) {
    return new MakeClassInterfaceFix(aClass, true);
  }

  public IntentionAction createMakeClassInterfaceFix(@NotNull PsiClass aClass, final boolean makeInterface) {
    return new MakeClassInterfaceFix(aClass, makeInterface);
  }

  public IntentionAction createExtendsListFix(@NotNull PsiClass aClass, @NotNull PsiClassType typeToExtendFrom, boolean toAdd) {
    return new ExtendsListFix(aClass, typeToExtendFrom, toAdd);
  }

  public IntentionAction createRemoveUnusedParameterFix(PsiParameter parameter) {
    return new RemoveUnusedParameterFix(parameter);
  }

  @NonNls
  public String getComponentName() {
    return "QuickFixFactory";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }
}
