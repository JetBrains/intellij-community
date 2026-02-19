// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template.impl;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public final class TemplateImplUtil {

  public static @NotNull LinkedHashSet<@NlsSafe String> parseVariableNames(@NotNull CharSequence text) {
    LinkedHashSet<String> variableNames = new LinkedHashSet<>();
    TemplateTextLexer lexer = new TemplateTextLexer();
    lexer.start(text);

    while (true) {
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null) break;
      if (tokenType == TemplateTokenType.VARIABLE) {
        int start = lexer.getTokenStart();
        int end = lexer.getTokenEnd();
        String name = text.subSequence(start + 1, end - 1).toString();
        variableNames.add(name);
      }
      lexer.advance();
    }
    return variableNames;
  }

  public static @NotNull Map<String, Variable> parseVariables(@NotNull CharSequence text) {
    Map<String, Variable> variables = new LinkedHashMap<>();
    for (String name : parseVariableNames(text)) {
      variables.put(name, new Variable(name, "", "", true));
    }
    return variables;
  }

  public static boolean isValidVariableName(@NotNull String varName) {
    return parseVariableNames("$" + varName + "$").contains(varName);
  }

  public static boolean isValidVariable(@Nullable String var) {
    return var != null && var.length() > 2 && StringUtil.startsWithChar(var, '$') && StringUtil.endsWithChar(var, '$') &&
           isValidVariableName(var.substring(1, var.length() - 1));
  }

  public static TextRange findVariableAtOffset(@NotNull CharSequence text, int offset) {
    TemplateTextLexer lexer = new TemplateTextLexer();
    lexer.start(text);

    while (true) {
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null) {
        return null;
      }
      int start = lexer.getTokenStart();
      if (start < offset) {
        int end = lexer.getTokenEnd();
        if (end >= offset) {
          return tokenType == TemplateTokenType.VARIABLE ? new TextRange(start + 1, end - 1) : null;
        }
      }
      lexer.advance();
    }
  }
}
