// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates.editable;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public interface PostfixTemplateExpressionCondition<T extends PsiElement> extends Condition<T> {

  @NonNls String ID_ATTR = "id";

  /**
   * @return presentable name for postfix editor dialog
   */
  @NotNull @Nls String getPresentableName();


  /**
   * @return id for serialization
   */
  @NotNull @NonNls String getId();

  boolean equals(Object o);

  int hashCode();

  default void serializeTo(@NotNull Element element) {
    element.setAttribute(ID_ATTR, getId());
  }

  @Override
  boolean value(@NotNull T t);
}
