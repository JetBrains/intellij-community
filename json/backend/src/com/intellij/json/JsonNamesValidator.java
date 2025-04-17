// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.json;

import com.intellij.json.syntax.JsonLexer;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.lexer.FlexAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.syntax.SyntaxElementType;
import com.intellij.platform.syntax.lexer.Lexer;
import com.intellij.platform.syntax.psi.lexer.LexerAdapter;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import static com.intellij.json.JsonTokenSets.JSON_KEYWORDS;

public final class JsonNamesValidator implements NamesValidator {

  LexerAdapter myFlexLexer; 
  private final Lexer myLexer = new JsonLexer();

  @Override
  public synchronized boolean isKeyword(@NotNull String name, Project project) {
    myLexer.start(name);
    return JSON_KEYWORDS.contains(myLexer.getTokenType()) && myLexer.getTokenEnd() == name.length();
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
