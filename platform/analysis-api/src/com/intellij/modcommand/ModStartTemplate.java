// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.codeInsight.template.Expression;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * A command to start template editing in the editor. In batch mode, or if the corresponding editor cannot be opened,
 * fields will just be initialized with expression default values.
 *
 * @param file                   file where to edit template.
 * @param fields                 template fields
 * @param templateFinishFunction
 */
public record ModStartTemplate(@NotNull VirtualFile file, @NotNull List<@NotNull TemplateField> fields,
                               @NotNull Function<? super @NotNull PsiFile, ? extends @NotNull ModCommand> templateFinishFunction) 
  implements ModCommand {

  /**
   * Template field
   */
  public sealed interface TemplateField {
    /**
     * @return field range inside the file
     */
    @NotNull TextRange range();

    /**
     * @param range new range
     * @return an equivalent template field but with updated range
     */
    @NotNull TemplateField withRange(@NotNull TextRange range);
  }

  /**
   * Expression-based template field
   * 
   * @param range field range inside the file
   * @param varName variable name for the field (optional)
   * @param expression expression for the field
   */
  public record ExpressionField(@NotNull TextRange range, @Nullable String varName, @NotNull Expression expression) implements TemplateField {
    @Override
    @NotNull
    public TemplateField withRange(@NotNull TextRange range) {
      return new ExpressionField(range, varName, expression);
    }
  }
  
  public record DependantVariableField(@NotNull TextRange range, @NotNull String varName,
                                       @NotNull String dependantVariableName,
                                       boolean alwaysStopAt) implements TemplateField {
    @Override
    public @NotNull TemplateField withRange(@NotNull TextRange range) {
      return new DependantVariableField(range, varName, dependantVariableName, alwaysStopAt);
    }
  }
}
