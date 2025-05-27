// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.json;

import com.intellij.json.syntax.JsonSyntaxElementTypes;
import com.intellij.json.syntax.JsonSyntaxLexer;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.syntax.SyntaxElementType;
import com.intellij.platform.syntax.lexer.Lexer;
import org.jetbrains.annotations.NotNull;

import static com.intellij.json.JsonTokenSets.JSON_KEYWORDS;

public final class JsonNamesValidator implements NamesValidator {

  private final Lexer myLexer = new JsonSyntaxLexer();

  @Override
  public synchronized boolean isKeyword(@NotNull String name, Project project) {
    myLexer.start(name);
    SyntaxElementType tokenType = myLexer.getTokenType();
    return tokenType != null && JSON_KEYWORDS.contains(myLexer.getTokenType()) && myLexer.getTokenEnd() == name.length();
  }
  @Override
  public synchronized boolean isIdentifier(@NotNull String name, final Project project) {
    if (!StringUtil.startsWithChar(name,'\'') && !StringUtil.startsWithChar(name,'"')) {
      name = "\"" + name;
    }

    if (!StringUtil.endsWithChar(name,'"') && !StringUtil.endsWithChar(name,'\'')) {
      name += "\"";
    }

    myLexer.start(name);
    SyntaxElementType type = myLexer.getTokenType();

    return myLexer.getTokenEnd() == name.length() && (type == JsonSyntaxElementTypes.DOUBLE_QUOTED_STRING ||
                                                      type == JsonSyntaxElementTypes.SINGLE_QUOTED_STRING);
  }

}
