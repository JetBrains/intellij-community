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
import com.intellij.psi.impl.source.tree.CharTableBasedLeafElementImpl;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;

public class PlainTextParserDefinition implements ParserDefinition {
  private static final IFileElementType PLAIN_FILE_ELEMENT_TYPE = new IFileElementType(FileTypes.PLAIN_TEXT.getLanguage()) {
    public ASTNode parseContents(ASTNode chameleon) {
      final CharSequence chars = ((CharTableBasedLeafElementImpl)chameleon).getInternedText();
      return ASTFactory.leaf(PlainTextTokenTypes.PLAIN_TEXT, chars, 0, chars.length(), SharedImplUtil.findCharTableByTree(chameleon));
    }
  };

  @NotNull
  public Lexer createLexer(Project project) {
    return new EmptyLexer();
  }

  @NotNull
  public PsiParser createParser(Project project) {
    throw new UnsupportedOperationException("Not supported");
  }

  public IFileElementType getFileNodeType() {
    return PLAIN_FILE_ELEMENT_TYPE;
  }

  @NotNull
  public TokenSet getWhitespaceTokens() {
    return TokenSet.EMPTY;
  }

  @NotNull
  public TokenSet getCommentTokens() {
    return TokenSet.EMPTY;
  }

  @NotNull
  public TokenSet getStringLiteralElements() {
    return TokenSet.EMPTY;
  }

  @NotNull
  public PsiElement createElement(ASTNode node) {
    return PsiUtilBase.NULL_PSI_ELEMENT;
  }

  public PsiFile createFile(FileViewProvider viewProvider) {
    return new PsiPlainTextFileImpl(viewProvider);
  }

  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {
    return SpaceRequirements.MAY;
  }
}