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
package com.siyeh.ig.style;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

/**
 * @author Bas Leijdekkers
 */
public final class AssertMessageNotStringInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean onlyWarnOnBoolean = true;

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiType type = (PsiType)infos[0];
    return InspectionGadgetsBundle.message("assert.message.of.type.boolean.problem.descriptor", type.getPresentableText());
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("onlyWarnOnBoolean", InspectionGadgetsBundle.message("assert.message.not.string.only.warn.boolean.option")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssertMessageNotStringVisitor();
  }

  private class AssertMessageNotStringVisitor extends BaseInspectionVisitor {

    @Override
    public void visitAssertStatement(@NotNull PsiAssertStatement statement) {
      super.visitAssertStatement(statement);
      final PsiExpression assertDescription = statement.getAssertDescription();
      if (assertDescription == null) {
        return;
      }
      final PsiType type = assertDescription.getType();
      if (onlyWarnOnBoolean) {
        if (PsiTypes.booleanType().equals(type)) {
          registerError(assertDescription, type);
          return;
        }
        final PsiClassType javaLangBoolean = PsiTypes.booleanType().getBoxedType(statement);
        if (javaLangBoolean != null && javaLangBoolean.equals(type)) {
          registerError(assertDescription, type);
        }
      }
      else {
        if (type != null && !type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
          registerError(assertDescription, type);
        }
      }
    }
  }
}
