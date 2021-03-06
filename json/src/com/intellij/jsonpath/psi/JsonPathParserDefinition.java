// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath.psi;

import com.intellij.jsonpath.JsonPathLanguage;
import com.intellij.jsonpath.lexer.JsonPathLexer;
import com.intellij.jsonpath.parser.JsonPathParser;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public final class JsonPathParserDefinition implements ParserDefinition {
  public static final IFileElementType FILE = new IFileElementType(JsonPathLanguage.INSTANCE);
  public static final TokenSet STRINGS = TokenSet.create(
    JsonPathTypes.SINGLE_QUOTED_STRING,
    JsonPathTypes.DOUBLE_QUOTED_STRING
  );

  @Override
  public @NotNull Lexer createLexer(Project project) {
    return new JsonPathLexer();
  }

  @Override
  public @NotNull PsiParser createParser(Project project) {
    return new JsonPathParser();
  }

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return new JsonPathFile(viewProvider);
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return FILE;
  }

  @Override
  public @NotNull TokenSet getCommentTokens() {
    return TokenSet.EMPTY;
  }

  @Override
  public @NotNull TokenSet getStringLiteralElements() {
    return STRINGS;
  }

  @Override
  public @NotNull PsiElement createElement(ASTNode node) {
    return JsonPathTypes.Factory.createElement(node);
  }
}
