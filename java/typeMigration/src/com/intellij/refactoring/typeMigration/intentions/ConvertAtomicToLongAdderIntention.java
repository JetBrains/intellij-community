/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.refactoring.typeMigration.intentions;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.typeMigration.TypeMigrationVariableTypeFixProvider;
import com.intellij.refactoring.typeMigration.rules.LongAdderConversionRule;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Dmitry Batkovich
 */
public class ConvertAtomicToLongAdderIntention extends PsiElementBaseIntentionAction {

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final PsiVariable variable = getVariable(element);
    if (variable != null) {
      final PsiType longAdder =
        JavaPsiFacade.getElementFactory(project).createTypeFromText(LongAdderConversionRule.JAVA_UTIL_CONCURRENT_ATOMIC_LONG_ADDER, element);
      TypeMigrationVariableTypeFixProvider.runTypeMigrationOnVariable(variable, longAdder, null, false);
    }
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!PsiUtil.isLanguageLevel8OrHigher(element)) return false;
    final PsiVariable variable = getVariable(element);
    return variable != null;
  }

  private static PsiVariable getVariable(PsiElement element) {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiLocalVariable) && !(parent instanceof PsiField)) {
      return null;
    }
    final PsiVariable var = (PsiVariable)element.getParent();
    final PsiClass aClass = PsiTypesUtil.getPsiClass(var.getType());
    if (aClass == null ||
        !(AtomicLong.class.getName().equals(aClass.getQualifiedName()) ||
          AtomicInteger.class.getName().equals(aClass.getQualifiedName()))) {
      return null;
    }
    final PsiExpression initializer = var.getInitializer();
    if (initializer != null) {
      if (initializer instanceof PsiNewExpression) {
        return LongAdderConversionRule.getParametersCount((PsiCallExpression)initializer) == -1 ? null : var;
      } else {
        return null;
      }
    }
    return var;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return "Convert variable to \'" + LongAdderConversionRule.JAVA_UTIL_CONCURRENT_ATOMIC_LONG_ADDER + "\'";
  }
}
