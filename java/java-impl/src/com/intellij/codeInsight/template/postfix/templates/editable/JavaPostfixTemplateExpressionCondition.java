// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates.editable;

import com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public interface JavaPostfixTemplateExpressionCondition extends PostfixTemplateExpressionCondition<PsiExpression> {

  class JavaPostfixTemplateExpressionFqnCondition implements JavaPostfixTemplateExpressionCondition {
    public static final @NonNls String ID = "fqn";
    public static final @NonNls String FQN_ATTR = "fqn";

    private final @NlsSafe String myFqn;

    public JavaPostfixTemplateExpressionFqnCondition(@NotNull String fqn) {
      myFqn = fqn;
    }

    public @NlsSafe String getFqn() {
      return myFqn;
    }

    @Override
    public boolean value(@NotNull PsiExpression element) {
      PsiType type = element.getType();
      return type != null && InheritanceUtil.isInheritor(type, myFqn);
    }

    @NotNull
    @Override
    public String getId() {
      return ID;
    }

    @Override
    public @NotNull @Nls String getPresentableName() {
      return myFqn;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      JavaPostfixTemplateExpressionFqnCondition condition = (JavaPostfixTemplateExpressionFqnCondition)o;
      return Objects.equals(myFqn, condition.myFqn);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myFqn);
    }

    @Override
    public void serializeTo(@NotNull Element element) {
      JavaPostfixTemplateExpressionCondition.super.serializeTo(element);
      element.setAttribute(FQN_ATTR, getFqn());
    }
  }

  class JavaPostfixTemplateVoidExpressionCondition implements JavaPostfixTemplateExpressionCondition {
    public static final @NonNls String ID = "void";

    @Override
    public boolean value(@NotNull PsiExpression element) {
      PsiType type = element.getType();
      return type != null && PsiType.VOID.equals(type);
    }

    @NotNull
    @Override
    public String getId() {
      return ID;
    }

    @Override
    public @NotNull @Nls String getPresentableName() {
      return JavaBundle.message("postfix.template.condition.void.name");
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
      return getClass().hashCode();
    }
  }


  class JavaPostfixTemplateNonVoidExpressionCondition implements JavaPostfixTemplateExpressionCondition {
    public static final @NonNls String ID = "non void";

    @Override
    public boolean value(@NotNull PsiExpression element) {
      return JavaPostfixTemplatesUtils.isNonVoid(element.getType());
    }

    @NotNull
    @Override
    public String getId() {
      return ID;
    }

    @Override
    public @NotNull @Nls String getPresentableName() {
      return JavaBundle.message("postfix.template.condition.non.void.name");
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
      return getClass().hashCode();
    }
  }

  class JavaPostfixTemplateBooleanExpressionCondition implements JavaPostfixTemplateExpressionCondition {
    public static final @NonNls String ID = "boolean";

    @Override
    public boolean value(@NotNull PsiExpression element) {
      return JavaPostfixTemplatesUtils.isBoolean(element.getType());
    }

    @NotNull
    @Override
    public String getId() {
      return ID;
    }

    @Override
    public @NotNull @Nls String getPresentableName() {
      return JavaBundle.message("postfix.template.condition.boolean.name");
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
      return getClass().hashCode();
    }
  }

  class JavaPostfixTemplateNumberExpressionCondition implements JavaPostfixTemplateExpressionCondition {
    public static final @NonNls String ID = "number";

    @Override
    public boolean value(@NotNull PsiExpression element) {
      return JavaPostfixTemplatesUtils.isNumber(element.getType());
    }

    @NotNull
    @Override
    public String getId() {
      return ID;
    }

    @Override
    public @NotNull @Nls String getPresentableName() {
      return JavaBundle.message("postfix.template.condition.number.name");
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
      return getClass().hashCode();
    }
  }

  class JavaPostfixTemplateNotPrimitiveTypeExpressionCondition implements JavaPostfixTemplateExpressionCondition {
    public static final @NonNls String ID = "notPrimitive";

    @Override
    public boolean value(@NotNull PsiExpression element) {
      return JavaPostfixTemplatesUtils.isNotPrimitiveTypeExpression(element);
    }

    @NotNull
    @Override
    public String getId() {
      return ID;
    }

    @Override
    public @NotNull @Nls String getPresentableName() {
      return JavaBundle.message("postfix.template.condition.not.primitive.type.name");
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
      return getClass().hashCode();
    }
  }

  class JavaPostfixTemplateArrayExpressionCondition implements JavaPostfixTemplateExpressionCondition {
    public static final @NonNls String ID = "array";

    @Override
    public boolean value(@NotNull PsiExpression element) {
      return JavaPostfixTemplatesUtils.isArray(element.getType());
    }

    @NotNull
    @Override
    public String getId() {
      return ID;
    }

    @Override
    public @NotNull @Nls String getPresentableName() {
      return JavaBundle.message("postfix.template.condition.array.name");
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
      return getClass().hashCode();
    }
  }
}
