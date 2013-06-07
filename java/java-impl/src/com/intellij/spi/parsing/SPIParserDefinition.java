/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.spi.parsing;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.spi.psi.SPIProviderElement;
import com.intellij.spi.psi.SPIProvidersElementList;
import com.intellij.spi.psi.SPIFile;
import com.intellij.lang.spi.SPILanguage;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 */
public class SPIParserDefinition implements ParserDefinition {
  public static final IFileElementType SPI_FILE_ELEMENT_TYPE = new IFileElementType(SPILanguage.INSTANCE);

  private static final TokenSet WHITE_SPACES = TokenSet.create(TokenType.WHITE_SPACE);
  private static final Logger LOG = Logger.getInstance("#" + SPIParserDefinition.class.getName());

  @NotNull
  @Override
  public Lexer createLexer(Project project) {
    return new SPILexer();
  }

  @Override
  public PsiParser createParser(Project project) {
    return new PsiParser() {
      @NotNull
      @Override
      public ASTNode parse(IElementType root, PsiBuilder builder) {
        final PsiBuilder.Marker rootMarker = builder.mark();
        final PsiBuilder.Marker propertiesList = builder.mark();
        while (!builder.eof()) {
          parseProvider(builder);
        }
        propertiesList.done(SPIElementTypes.PROVIDERS_LIST);
        rootMarker.done(root);
        return builder.getTreeBuilt();
      }
    };
  }

  @Override
  public IFileElementType getFileNodeType() {
    return SPI_FILE_ELEMENT_TYPE;
  }

  @NotNull
  @Override
  public TokenSet getWhitespaceTokens() {
    return WHITE_SPACES;
  }

  @NotNull
  @Override
  public TokenSet getCommentTokens() {
    return TokenSet.create(JavaTokenType.END_OF_LINE_COMMENT);
  }

  @NotNull
  @Override
  public TokenSet getStringLiteralElements() {
    return TokenSet.EMPTY;
  }

  @NotNull
  @Override
  public PsiElement createElement(ASTNode node) {
    final IElementType elementType = node.getElementType();
    if (elementType == SPIElementTypes.PROVIDERS_LIST) {
      return new SPIProvidersElementList(node);
    }
    if (elementType == SPIElementTypes.PROVIDER) {
      return new SPIProviderElement(node);
    }
    return PsiUtilCore.NULL_PSI_ELEMENT;
  }

  @Override
  public PsiFile createFile(FileViewProvider viewProvider) {
    return new SPIFile(viewProvider);
  }

  @Override
  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {
    return SpaceRequirements.MAY;
  }
  
  public static void parseProvider(PsiBuilder builder) {
    if (builder.getTokenType() == JavaTokenType.IDENTIFIER) {
      final PsiBuilder.Marker prop = builder.mark();

      parseProviderChar(builder);
      prop.done(SPIElementTypes.PROVIDER);
    }
    else {
      builder.advanceLexer();
      builder.error("Unexpected token");
    }
  }

  private static void parseProviderChar(final PsiBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == JavaTokenType.IDENTIFIER);
    builder.advanceLexer();
    final IElementType tokenType = builder.getTokenType();
    if (tokenType == JavaTokenType.DOT || tokenType == SPITokenType.DOLLAR) {
      builder.advanceLexer();
      parseProviderChar(builder);
    }
  }
}
