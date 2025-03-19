// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.commandLine;

import com.intellij.commandInterface.commandLine.CommandLineElementTypes.Factory;
import com.intellij.commandInterface.commandLine.psi.CommandLineFile;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

/**
 * Command line language parser definition
 *
 * @author Ilya.Kazakevich
 */
public final class CommandLineParserDefinition implements ParserDefinition {
  public static final IFileElementType FILE_TYPE = new IFileElementType(CommandLineLanguage.INSTANCE);

  @Override
  public @NotNull Lexer createLexer(final Project project) {
    return new FlexAdapter(new _CommandLineLexer());
  }

  @Override
  public @NotNull PsiParser createParser(final Project project) {
    return new CommandLineParser();
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return FILE_TYPE;
  }


  @Override
  public @NotNull TokenSet getWhitespaceTokens() {
    return TokenSet.create(TokenType.WHITE_SPACE);
  }

  @Override
  public @NotNull TokenSet getCommentTokens() {
    return TokenSet.EMPTY;
  }

  @Override
  public @NotNull TokenSet getStringLiteralElements() {
    return TokenSet.EMPTY;
  }

  @Override
  public @NotNull PsiElement createElement(final ASTNode node) {
    return Factory.createElement(node);
  }

  @Override
  public @NotNull PsiFile createFile(final @NotNull FileViewProvider viewProvider) {
    return new CommandLineFile(viewProvider);
  }

  @Override
  public @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens(final ASTNode left, final ASTNode right) {
    return SpaceRequirements.MAY;
  }
}
