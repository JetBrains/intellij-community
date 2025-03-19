// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

/**
 * @author Bas Leijdekkers
 */
public final class AssertMessageNotStringInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean onlyWarnOnBoolean = true;

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    final PsiType type = (PsiType)infos[0];
    return InspectionGadgetsBundle.message("assert.message.of.type.boolean.problem.descriptor", type.getPresentableText());
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("onlyWarnOnBoolean", InspectionGadgetsBundle.message("assert.message.not.string.only.warn.boolean.option")));
  }

  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.ASSERTIONS);
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
