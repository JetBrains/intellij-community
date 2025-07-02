// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import com.intellij.CodeStyleBundle;
import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;
import com.intellij.psi.codeStyle.modifier.CodeStyleStatusBarUIContributor;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class IndentStatusBarUIContributor implements CodeStyleStatusBarUIContributor {
  private final IndentOptions myIndentOptions;

  public IndentStatusBarUIContributor(IndentOptions options) {
    myIndentOptions = options;
  }

  public IndentOptions getIndentOptions() {
    return myIndentOptions;
  }

  /**
   * Returns a short, usually one-word, string to indicate the source of the given indent options.
   *
   * @return The indent options source hint or {@code null} if not available.
   */
  public abstract @Nullable @NlsContexts.HintText String getHint();

  @Override
  public @Nullable String getTooltip() {
    return createTooltip(getIndentInfo(myIndentOptions), getHint());
  }

  public static @Nls @NotNull String getIndentInfo(@NotNull IndentOptions indentOptions) {
    return indentOptions.USE_TAB_CHARACTER
           ? CodeStyleBundle.message("indent.status.bar.tab")
           : CodeStyleBundle.message("indent.status.bar.spaces", indentOptions.INDENT_SIZE);
  }

  /**
   * @return True if "Configure indents for [Language]" action should be available when the provider is active (returns its own indent
   *          options), false otherwise.
   */
  public boolean isShowFileIndentOptionsEnabled() {
    return true;
  }

  public static @NotNull @NlsContexts.Tooltip String createTooltip(@Nls String indentInfo, @NlsContexts.HintText String hint) {
    HtmlBuilder builder = new HtmlBuilder();
    builder.append(CodeStyleBundle.message("indent.status.bar.indent.tooltip")).append(" ").append(indentInfo);
    if (hint != null) {
      builder.nbsp(2).append(HtmlChunk.span("color: "+ColorUtil.toHtmlColor(JBColor.GRAY)).addText(hint));
    }
    return builder.wrapWith("html").toString();
  }

  @Override
  public @NotNull String getStatusText(@NotNull PsiFile psiFile) {
    return getIndentInfo(myIndentOptions);
  }
}

