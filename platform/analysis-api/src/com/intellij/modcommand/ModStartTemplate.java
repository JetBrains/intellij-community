// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.codeInsight.template.Expression;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * A command to start template editing in the editor. In batch mode, or if the corresponding editor cannot be opened,
 * fields will just be initialized with expression default values.
 *
 * @param file                   file where to edit template.
 * @param fields                 template fields
 * @param optional               if true, the template can be skipped by non-interactive executors, or executors 
 *                               that don't support templates.
 *                               If false, then it cannot be skipped, so the whole action should not be executed
 *                               if it's unable to display the template, as default behavior is not satisfactory. 
 * @param templateFinishFunction
 */
public record ModStartTemplate(@NotNull VirtualFile file, @NotNull List<@NotNull TemplateField> fields,
                               boolean optional,
                               @NotNull Function<? super @NotNull PsiFile, ? extends @NotNull ModCommand> templateFinishFunction) 
  implements ModCommand {

  @Override
  public @NotNull Set<@NotNull VirtualFile> modifiedFiles() {
    return Set.of(file);
  }

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
    public @NotNull TemplateField withRange(@NotNull TextRange range) {
      return new ExpressionField(range, varName, expression);
    }
  }

  public record DependantVariableField(@NotNull TextRange range, @NotNull String varName,
                                       @NotNull String dependantVariableName,
                                       boolean alwaysStopAt,
                                       @Nullable String defaultValue) implements TemplateField {
    public DependantVariableField(@NotNull TextRange range, @NotNull String varName,
                                  @NotNull String dependantVariableName,
                                  boolean alwaysStopAt) {
      this(range, varName, dependantVariableName, alwaysStopAt, null);
    }

    @Override
    public @NotNull TemplateField withRange(@NotNull TextRange range) {
      return new DependantVariableField(range, varName, dependantVariableName, alwaysStopAt, defaultValue);
    }
  }

  /**
   * A field to designate the end offset (where caret should be moved after the template is finished)
   * 
   * @param range left bound of the range designates the end position, right bound is ignored
   */
  public record EndField(@NotNull TextRange range) implements TemplateField {
    @Override
    public @NotNull TemplateField withRange(@NotNull TextRange range) {
      return new EndField(range);
    }
  }
}
