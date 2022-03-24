// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.reference;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UExpression;

import java.util.List;

/**
 * A node in the reference graph corresponding to lambda expressions and method references.
 */
public interface RefFunctionalExpression extends RefJavaElement, RefOverridable {
  /**
   * @return list of parameters of corresponding functional expression
   */
  @NotNull
  List<RefParameter> getParameters();

  /**
   * @return expression, which this node is based on
   */
  @Override
  @Nullable
  UExpression getUastElement();

  /**
   * @return true, if the lambda expression's body is empty, false if not.
   * Note that method reference always contains a body.
   */
  boolean hasEmptyBody();
}
