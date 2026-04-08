// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Strategy interface for expanding a postfix template as a {@link ModCommand}.
 * Implementations contain the ModCommand-based expansion logic, separated from the {@link PostfixTemplate} class.
 *
 * @see PostfixTemplate#createModExpander()
 */
@ApiStatus.Experimental
public interface PostfixModExpander {
  /**
   * Expands the template as a {@link ModCommand}, suitable for use in ModCompletion and preview/batch modes.
   */
  @NotNull ModCommand expand(@NotNull ActionContext ctx,
                              @NotNull PostfixTemplateProvider provider,
                              @NotNull TextRange keyRange);
}