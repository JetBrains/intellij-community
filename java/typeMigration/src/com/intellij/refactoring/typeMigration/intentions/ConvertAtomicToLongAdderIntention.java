// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration.intentions;

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.typeMigration.TypeMigrationBundle;
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
public final class ConvertAtomicToLongAdderIntention extends BaseElementAtCaretIntentionAction {

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final PsiVariable variable = getVariable(element);
    if (variable != null) {
      final PsiType longAdder =
        JavaPsiFacade.getElementFactory(project).createTypeFromText(LongAdderConversionRule.JAVA_UTIL_CONCURRENT_ATOMIC_LONG_ADDER, element);
      TypeMigrationVariableTypeFixProvider.runTypeMigrationOnVariable(variable, longAdder, null, false, false);
    }
  }

  @Override
  public boolean isAvailable(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement element) {
    if (!element.isValid() || !PsiUtil.isLanguageLevel8OrHigher(element)) return false;
    final PsiVariable variable = getVariable(element);
    return variable != null;
  }

  private static PsiVariable getVariable(PsiElement element) {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiLocalVariable) && !(parent instanceof PsiField)) {
      return null;
    }
    final PsiVariable var = (PsiVariable)element.getParent();
    final PsiType type = var.getType();
    if (!type.isValid()) return null;
    final PsiClass aClass = PsiTypesUtil.getPsiClass(type);
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
    return false;
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
    return TypeMigrationBundle.message("convert.to.longadder.family.name");
  }
}
