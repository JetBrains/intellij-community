package com.intellij.json;

import com.intellij.json.psi.JsonFile;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public class JsonParserDefinition implements ParserDefinition {
  public static final TokenSet WHITE_SPACES = TokenSet.create(TokenType.WHITE_SPACE);
  public static final TokenSet STRING_LITERALS = TokenSet.create(JsonElementTypes.STRING);
  public static final IFileElementType FILE = new IFileElementType(JsonLanguage.INSTANCE);
  public static final TokenSet CONTAINERS = TokenSet.create(JsonElementTypes.ARRAY, JsonElementTypes.OBJECT);


  @NotNull
  @Override
  public Lexer createLexer(Project project) {
    return new JsonLexer();
  }

  @Override
  public PsiParser createParser(Project project) {
    return new JsonParser();
  }

  @Override
  public IFileElementType getFileNodeType() {
    return FILE;
  }

  @NotNull
  @Override
  public TokenSet getWhitespaceTokens() {
    return WHITE_SPACES;
  }

  @NotNull
  @Override
  public TokenSet getCommentTokens() {
    return TokenSet.EMPTY;
  }

  @NotNull
  @Override
  public TokenSet getStringLiteralElements() {
    return STRING_LITERALS;
  }

  @NotNull
  @Override
  public PsiElement createElement(ASTNode astNode) {
    return JsonElementTypes.Factory.createElement(astNode);
  }

  @Override
  public PsiFile createFile(FileViewProvider fileViewProvider) {
    return new JsonFile(fileViewProvider);
  }

  @Override
  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode astNode, ASTNode astNode2) {
    return SpaceRequirements.MAY;
  }
}
