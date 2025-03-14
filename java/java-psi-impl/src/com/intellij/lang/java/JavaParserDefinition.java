// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java;

import com.intellij.java.syntax.JavaSyntaxDefinition;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.platform.syntax.psi.lexer.LexerAdapter;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypePsiElementMappingRegistry;
import com.intellij.psi.impl.source.JavaFileElementType;
import com.intellij.psi.impl.source.PsiJavaFileImpl;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.lang.java.syntax.JavaElementTypeConverterKt.getJavaElementTypeConverter;

public class JavaParserDefinition implements ParserDefinition {
  public static final IFileElementType JAVA_FILE = new JavaFileElementType();

  /**
   * @deprecated Use {@link JavaSyntaxDefinition#createLexer(LanguageLevel)} instead.
   */
  @Deprecated
  @Override
  public @NotNull Lexer createLexer(@Nullable Project project) {
    LanguageLevel level = project != null ? LanguageLevelProjectExtension.getInstance(project).getLanguageLevel() : LanguageLevel.HIGHEST;
    return createLexer(level);
  }

  /**
   * @deprecated Use {@link JavaSyntaxDefinition#createLexer(LanguageLevel)} instead.
   */
  @Deprecated
  public static @NotNull Lexer createLexer(@NotNull LanguageLevel level) {
    return new LexerAdapter(JavaSyntaxDefinition.createLexer(level), getJavaElementTypeConverter());
  }

  /**
   * @return A lexer which handles JEP-467 bracket escapes when parsing Java types
   *
   * @deprecated Use {@link JavaSyntaxDefinition#createLexerWithMarkdownEscape(LanguageLevel)} instead.
   */
  @Deprecated
  public static @NotNull Lexer createLexerWithMarkdownEscape(@NotNull LanguageLevel level) {
    return new LexerAdapter(JavaSyntaxDefinition.createLexerWithMarkdownEscape(level), getJavaElementTypeConverter());
  }

  /**
   * @deprecated Use {@link JavaSyntaxDefinition#createDocLexer(LanguageLevel)} instead.
   */
  @Deprecated
  public static @NotNull Lexer createDocLexer(@NotNull LanguageLevel level) {
    return new LexerAdapter(JavaSyntaxDefinition.createDocLexer(level), getJavaElementTypeConverter());
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return JAVA_FILE;
  }

  @Override
  public @NotNull TokenSet getCommentTokens() {
    return ElementType.JAVA_COMMENT_BIT_SET;
  }

  @Override
  public @NotNull TokenSet getStringLiteralElements() {
    return TokenSet.create(JavaElementType.LITERAL_EXPRESSION);
  }

  @Override
  public @NotNull PsiParser createParser(Project project) {
    throw new UnsupportedOperationException("Should not be called directly");
  }

  @Override
  public @NotNull PsiElement createElement(ASTNode node) {
    IElementType type = node.getElementType();
    PsiElement psi = JavaStubElementTypePsiElementMappingRegistry.getInstance().createPsi(node);
    if (psi != null) {
      return psi;
    }
    // This exception is caught in com.intellij.psi.impl.source.tree.injected.InjectionRegistrarImpl.findNewInjectionHost
    // Please, check that code if you make any changes here
    throw new IllegalArgumentException("Not a Java node: " + node + " (" + type + ", " + type.getLanguage() + ")");
  }

  @Override
  public @NotNull PsiFile createFile(final @NotNull FileViewProvider viewProvider) {
    return new PsiJavaFileImpl(viewProvider);
  }

  @Override
  public @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
    if (right.getElementType() == JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN ||
        left.getElementType() == JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN) {
      return SpaceRequirements.MUST_NOT;
    }

    PsiFile containingFile = left.getTreeParent().getPsi().getContainingFile();
    LanguageLevel level = containingFile instanceof PsiJavaFile ? ((PsiJavaFile)containingFile).getLanguageLevel() : LanguageLevel.HIGHEST;
    Lexer lexer = createLexer(level);
    SpaceRequirements spaceRequirements = LanguageUtil.canStickTokensTogetherByLexer(left, right, lexer);
    if (left.getElementType() == JavaTokenType.END_OF_LINE_COMMENT) {
      return SpaceRequirements.MUST_LINE_BREAK;
    }

    if (left.getElementType() == JavaDocTokenType.DOC_COMMENT_DATA) {
      String text = left.getText();
      if (!text.isEmpty() && Character.isWhitespace(text.charAt(text.length() - 1))) {
        return SpaceRequirements.MAY;
      }
    }

    if (right.getElementType() == JavaDocTokenType.DOC_COMMENT_DATA) {
      String text = right.getText();
      if (!text.isEmpty() && Character.isWhitespace(text.charAt(0))) {
        return SpaceRequirements.MAY;
      }
    }
    else if (right.getElementType() == JavaDocTokenType.DOC_INLINE_TAG_END) {
      return SpaceRequirements.MAY;
    }

    return spaceRequirements;
  }
}