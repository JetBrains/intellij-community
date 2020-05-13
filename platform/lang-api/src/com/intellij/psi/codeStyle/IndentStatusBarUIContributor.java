// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.util.text.StringUtil;
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
  @Nullable
  public abstract String getHint();

  @Nullable
  @Override
  public String getTooltip() {
    return createTooltip(getIndentInfo(myIndentOptions), getHint());
  }

  @Nls
  @NotNull
  public static String getIndentInfo(@NotNull IndentOptions indentOptions) {
    return indentOptions.USE_TAB_CHARACTER
           ? CodeInsightBundle.message("indent.status.bar.tab")
           : CodeInsightBundle.message("indent.status.bar.spaces", indentOptions.INDENT_SIZE);
  }

  /**
   * @return True if "Configure indents for [Language]" action should be available when the provider is active (returns its own indent
   *          options), false otherwise.
   */
  public boolean isShowFileIndentOptionsEnabled() {
    return true;
  }

  @NotNull
  public static String createTooltip(String indentInfo, String hint) {
    StringBuilder sb = new StringBuilder();
    sb.append("<html>").append(CodeInsightBundle.message("indent.status.bar.indent.tooltip")).append(indentInfo);
    if (hint != null) {
      sb.append("&nbsp;&nbsp;").append("<span style=\"color:#").append(ColorUtil.toHex(JBColor.GRAY)).append("\">");
      sb.append(StringUtil.capitalize(hint));
      sb.append("</span>");
    }
    return sb.append("</html>").toString();
  }

  @NotNull
  @Override
  public String getStatusText(@NotNull PsiFile psiFile) {
    String indentInfo = getIndentInfo(myIndentOptions);
    StringBuilder widgetText = new StringBuilder();
    widgetText.append(indentInfo);
    IndentOptions projectIndentOptions = CodeStyle.getSettings(psiFile.getProject()).getLanguageIndentOptions(psiFile.getLanguage());
    if (!projectIndentOptions.equals(myIndentOptions)) {
      widgetText.append("*");
    }
    return widgetText.toString();
  }
}

