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
package com.intellij.codeInsight.intention;

import com.intellij.psi.*;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public abstract class QuickFixFactory implements ApplicationComponent {
  public static QuickFixFactory getInstance() {
    return ApplicationManager.getApplication().getComponent(QuickFixFactory.class);
  }

  public abstract IntentionAction createModifierListFix(PsiModifierList modifierList,
                                                        String modifier,
                                                        boolean shouldHave,
                                                        final boolean showContainingClass);
  public abstract IntentionAction createMethodReturnFix(PsiMethod method, PsiType toReturn, boolean fixWholeHierarchy);

  public abstract IntentionAction createAddMethodFix(@NotNull PsiMethod method, @NotNull PsiClass toClass);
  public abstract IntentionAction createAddMethodFix(@NotNull String methodText, @NotNull PsiClass toClass, String... exceptions);

  public abstract IntentionAction createImplementMethodsFix(@NotNull PsiClass aClass);
  public abstract IntentionAction createMethodThrowsFix(@NotNull PsiMethod method, @NotNull PsiClassType exceptionClass, boolean shouldThrow, boolean showContainingClass);
  public abstract IntentionAction createAddDefaultConstructorFix(@NotNull PsiClass aClass);
  public abstract IntentionAction createMethodParameterTypeFix(@NotNull PsiMethod method, int index, @NotNull PsiType newType, boolean fixWholeHierarchy);
  public abstract IntentionAction createMakeClassInterfaceFix(@NotNull PsiClass aClass);
  public abstract IntentionAction createMakeClassInterfaceFix(@NotNull PsiClass aClass, final boolean makeInterface);
  public abstract IntentionAction createExtendsListFix(@NotNull PsiClass aClass, @NotNull PsiClassType typeToExtendFrom, boolean toAdd);
  public abstract IntentionAction createRemoveUnusedParameterFix(PsiParameter parameter);
}
