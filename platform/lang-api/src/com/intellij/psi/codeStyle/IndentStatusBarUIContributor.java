// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;
import com.intellij.psi.codeStyle.modifier.CodeStyleStatusBarUIContributor;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
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
    return createTooltip(getTooltip(myIndentOptions), getHint());
  }

  @NotNull
  public static String getTooltip(@NotNull IndentOptions indentOptions) {
    StringBuilder sb = new StringBuilder();
    if (indentOptions.USE_TAB_CHARACTER) {
      sb.append("Tab");
    }
    else {
      int indent = indentOptions.INDENT_SIZE;
      sb.append(indentOptions.INDENT_SIZE).append(indent > 1 ? " spaces" : " space");
    }
    return sb.toString();
  }

  /**
   * @return True if "Configure indents for [Language]" action should be available when the provider is active (returns its own indent
   *          options), false otherwise.
   */
  public boolean isShowFileIndentOptionsEnabled() {
    return true;
  }

  @NotNull
  private static String createTooltip(String indentInfo, String hint) {
    StringBuilder sb = new StringBuilder();
    sb.append("<html>").append("Indent: ").append(indentInfo);
    if (hint != null) {
      sb.append("&nbsp;&nbsp;").append("<span style=\"color:#").append(ColorUtil.toHex(JBColor.GRAY)).append("\">");
      sb.append(StringUtil.capitalize(hint));
      sb.append("</span>");
    }
    return sb.toString();
  }
}

