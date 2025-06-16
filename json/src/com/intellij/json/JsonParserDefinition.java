// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json;

import com.intellij.json.psi.impl.JsonFileImpl;
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

import static com.intellij.json.JsonElementTypes.Factory;

public class JsonParserDefinition implements ParserDefinition {
  public static final IFileElementType FILE = new IFileElementType(JsonLanguage.INSTANCE);

  @Override
  public @NotNull Lexer createLexer(Project project) {
    return new JsonLexer();
  }

  @Override
  public @NotNull PsiParser createParser(Project project) {
    return new JsonParser();
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return FILE;
  }

  @Override
  public @NotNull TokenSet getCommentTokens() {
    return JsonTokenSets.JSON_COMMENTARIES;
  }

  @Override
  public @NotNull TokenSet getStringLiteralElements() {
    return JsonTokenSets.STRING_LITERALS;
  }

  @Override
  public @NotNull PsiElement createElement(ASTNode astNode) {
    return Factory.createElement(astNode);
  }

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider fileViewProvider) {
    return new JsonFileImpl(fileViewProvider, JsonLanguage.INSTANCE);
  }

  @Override
  public @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode astNode, ASTNode astNode2) {
    return SpaceRequirements.MAY;
  }
}
