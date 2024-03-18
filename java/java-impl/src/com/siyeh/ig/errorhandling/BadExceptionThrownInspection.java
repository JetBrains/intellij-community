/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiThrowStatement;
import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ui.ExternalizableStringSet;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.codeInspection.options.OptPane.stringList;

public final class BadExceptionThrownInspection extends BaseInspection {
  @SuppressWarnings("PublicField")
  public String exceptionsString = "";

  @SuppressWarnings("PublicField")
  public final ExternalizableStringSet exceptions =
    new ExternalizableStringSet(
      CommonClassNames.JAVA_LANG_THROWABLE,
      "java.lang.Exception",
      "java.lang.Error",
      "java.lang.RuntimeException",
      "java.lang.NullPointerException",
      "java.lang.ClassCastException",
      "java.lang.ArrayIndexOutOfBoundsException"
    );

  public BadExceptionThrownInspection() {
    if (!exceptionsString.isEmpty()) {
      exceptions.clear();
      final List<String> strings =
        StringUtil.split(exceptionsString, ",");
      exceptions.addAll(strings);
      exceptionsString = "";
    }
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      stringList("exceptions", InspectionGadgetsBundle.message("choose.exception.label"),
                 new JavaClassValidator().withSuperClass(CommonClassNames.JAVA_LANG_THROWABLE)
                  .withTitle(InspectionGadgetsBundle.message("choose.exception.class"))));
  }

  @Override
  @NotNull
  public String getID() {
    return "ProhibitedExceptionThrown";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiType type = (PsiType)infos[0];
    final String exceptionName = type.getPresentableText();
    return InspectionGadgetsBundle.message(
      "bad.exception.thrown.problem.descriptor", exceptionName);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BadExceptionThrownVisitor();
  }

  private class BadExceptionThrownVisitor extends BaseInspectionVisitor {

    @Override
    public void visitThrowStatement(@NotNull PsiThrowStatement statement) {
      super.visitThrowStatement(statement);
      final PsiExpression exception = statement.getException();
      if (exception == null) {
        return;
      }
      final PsiType type = exception.getType();
      if (type == null) {
        return;
      }
      final String text = type.getCanonicalText();
      if (exceptions.contains(text)) {
        registerStatementError(statement, type);
      }
    }
  }
}