// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

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
   * Add a new expression field
   *
   * @param element element to replace with an expression
   * @param rangeInElement range of the element to replace with an expression
   * @param expression expression to use
   * @return this builder
   */
  @NotNull ModTemplateBuilder field(@NotNull PsiElement element, @NotNull TextRange rangeInElement, @NotNull String varName,
                                    @NotNull Expression expression);

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

  /**
   * Add a new dependent variable field covering {@code rangeInElement} of {@code element}.
   * Useful when the mirrored value lives inside a PSI element.
   *
   * @param element element whose range contains the mirrored region
   * @param rangeInElement range of the element to treat as the field range
   * @param varName variable name
   * @param dependantVariableName dependant variable name
   * @param alwaysStopAt whether to always stop at this field
   * @return this builder
   */
  @ApiStatus.Experimental
  @NotNull ModTemplateBuilder field(@NotNull PsiElement element, @NotNull TextRange rangeInElement,
                                    @NotNull String varName, @NotNull String dependantVariableName,
                                    boolean alwaysStopAt);

  /**
   * Same as {@link #field(PsiElement, TextRange, String, String, boolean)} with alwaysStopAt=false, but with an explicit
   * default-value expression string used when the main expression evaluates to {@code null} or
   * empty. Useful for macro-call dependencies like {@code rightSideType()} whose XML template
   * carries a separate literal fallback (e.g. {@code "java.util.Iterator"}).
   *
   * @param defaultValue default-value expression string (quoted string literals are treated as
   *                     constants by the macro parser); {@code null} means reuse
   *                     {@code dependantVariableName} as the default.
   */
  @ApiStatus.Experimental
  @NotNull ModTemplateBuilder field(@NotNull PsiElement element, @NotNull TextRange rangeInElement,
                                    @NotNull String varName, @NotNull String dependantVariableName,
                                    @Nullable String defaultValue);

  /**
   * Add a finish position to the template. The caret will be moved to a given position after the template is finished
   * 
   * @param offset finish position (offset within the file)
   * @return this builder
   */
  @NotNull ModTemplateBuilder finishAt(int offset);

  /**
   * Marks template as required making the action non-available for non-interactive execution.
   * @return this builder
   */
  @NotNull ModTemplateBuilder required();

  @NotNull ModTemplateBuilder onTemplateFinished(@NotNull Function<? super @NotNull PsiFile, ? extends @NotNull ModCommand> templateFinishFunction);
}
