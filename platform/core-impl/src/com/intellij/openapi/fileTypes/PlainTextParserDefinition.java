// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.openapi.fileTypes;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.EmptyLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PlainTextTokenTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PsiPlainTextFileImpl;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

public class PlainTextParserDefinition implements ParserDefinition {
  private static final IFileElementType PLAIN_FILE_ELEMENT_TYPE = new IFileElementType(PlainTextFileType.INSTANCE.getLanguage()) {
    @Override
    public ASTNode parseContents(@NotNull ASTNode chameleon) {
      final CharSequence chars = chameleon.getChars();
      return ASTFactory.leaf(PlainTextTokenTypes.PLAIN_TEXT, chars);
    }
  };

  @Override
  @NotNull
  public Lexer createLexer(Project project) {
    return new EmptyLexer();
  }

  @Override
  @NotNull
  public PsiParser createParser(Project project) {
    throw new UnsupportedOperationException("Not supported");
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return PLAIN_FILE_ELEMENT_TYPE;
  }

  @Override
  @NotNull
  public TokenSet getWhitespaceTokens() {
    return TokenSet.EMPTY;
  }

  @Override
  @NotNull
  public TokenSet getCommentTokens() {
    return TokenSet.EMPTY;
  }

  @Override
  @NotNull
  public TokenSet getStringLiteralElements() {
    return TokenSet.EMPTY;
  }

  @Override
  @NotNull
  public PsiElement createElement(ASTNode node) {
    return PsiUtilCore.NULL_PSI_ELEMENT;
  }

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return new PsiPlainTextFileImpl(viewProvider);
  }

  @Override
  public @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
    return SpaceRequirements.MAY;
  }
}
