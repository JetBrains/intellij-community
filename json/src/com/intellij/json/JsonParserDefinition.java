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
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

import static com.intellij.json.JsonElementTypes.*;

public class JsonParserDefinition implements ParserDefinition {
  public static final TokenSet WHITE_SPACES = TokenSet.create(TokenType.WHITE_SPACE);
  public static final TokenSet STRING_LITERALS = TokenSet.create(SINGLE_QUOTED_STRING, DOUBLE_QUOTED_STRING);

  public static final IFileElementType FILE = new IFileElementType(JsonLanguage.INSTANCE);

  public static final TokenSet JSON_BRACES = TokenSet.create(L_CURLY, R_CURLY);
  public static final TokenSet JSON_BRACKETS = TokenSet.create(L_BRACKET, R_BRACKET);
  public static final TokenSet JSON_CONTAINERS = TokenSet.create(OBJECT, ARRAY);
  public static final TokenSet JSON_BOOLEANS = TokenSet.create(TRUE, FALSE);
  public static final TokenSet JSON_KEYWORDS = TokenSet.create(TRUE, FALSE, NULL);
  public static final TokenSet JSON_LITERALS = TokenSet.create(STRING_LITERAL, NUMBER_LITERAL, NULL_LITERAL, TRUE, FALSE);
  public static final TokenSet JSON_VALUES = TokenSet.orSet(JSON_CONTAINERS, JSON_LITERALS);
  public static final TokenSet JSON_COMMENTARIES = TokenSet.create(BLOCK_COMMENT, LINE_COMMENT);


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
    return JSON_COMMENTARIES;
  }

  @NotNull
  @Override
  public TokenSet getStringLiteralElements() {
    return STRING_LITERALS;
  }

  @NotNull
  @Override
  public PsiElement createElement(ASTNode astNode) {
    return Factory.createElement(astNode);
  }

  @Override
  public PsiFile createFile(FileViewProvider fileViewProvider) {
    return new JsonFileImpl(fileViewProvider, JsonLanguage.INSTANCE);
  }

  @Override
  public SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode astNode, ASTNode astNode2) {
    return SpaceRequirements.MAY;
  }
}
