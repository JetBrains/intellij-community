// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * A builder to create a template. Should be used together with {@link ModPsiUpdater}.
 */
public interface ModTemplateBuilder {
  /**
   * Add a new expression field
   * 
   * @param element element to replace with an expression
   * @param expression expression to use
   * @return this builder
   */
  @NotNull ModTemplateBuilder field(@NotNull PsiElement element, @NotNull Expression expression);

  /**
   * Add a new expression field
   * 
   * @param element element to replace with an expression
   * @param expression expression to use
   * @return this builder
   */
  @NotNull ModTemplateBuilder field(@NotNull PsiElement element, @NotNull String varName, @NotNull Expression expression);

  /**
   * Add a new simple text field
   *
   * @param element element to replace with a constant text
   * @param value text to display by default
   * @return this builder
   */
  default @NotNull ModTemplateBuilder field(@NotNull PsiElement element, @NotNull String value) {
    return field(element, new ConstantNode(value));
  }

  /**
   * Add a new dependent variable field
   * 
   * @param element element to replace with a field
   * @param varName variable name
   * @param dependantVariableName dependant variable name
   * @param alwaysStopAt whether to always stop at this field
   * @return this builder
   */
  @NotNull ModTemplateBuilder field(@NotNull PsiElement element, @NotNull String varName, @NotNull String dependantVariableName, 
                                    boolean alwaysStopAt);
}
