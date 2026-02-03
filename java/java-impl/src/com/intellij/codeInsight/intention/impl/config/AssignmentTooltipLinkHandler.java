// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.highlighting.TooltipLinkHandler;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handles tooltip links in format {@code #assignment/escaped_full_tooltip_text}.
 * On a click comparison table opens.
 */
public final class AssignmentTooltipLinkHandler extends TooltipLinkHandler {
  @Override
  public @Nullable @NlsSafe String getDescription(@NotNull String refSuffix, @NotNull Editor editor) {
    return StringUtil.unescapeXmlEntities(refSuffix);
  }

  @Override
  public @NotNull String getDescriptionTitle(@NotNull String refSuffix, @NotNull Editor editor) {
    return JavaBundle.message("inspection.message.full.description");
  }
}
