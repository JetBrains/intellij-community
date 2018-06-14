// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.parser;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.*;
import com.intellij.lang.impl.PsiBuilderAdapter;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.indexing.IndexingDataKeys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.List;

public class JavaParserUtil {
  private static final Key<LanguageLevel> LANG_LEVEL_KEY = Key.create("JavaParserUtil.LanguageLevel");
  private static final Key<Boolean> DEEP_PARSE_BLOCKS_IN_STATEMENTS = Key.create("JavaParserUtil.ParserExtender");

  public interface ParserWrapper {
    void parse(PsiBuilder builder);
  }

  private static class PrecedingWhitespacesAndCommentsBinder implements WhitespacesAndCommentsBinder {
    private final boolean myAfterEmptyImport;

    public PrecedingWhitespacesAndCommentsBinder(final boolean afterImport) {
      this.myAfterEmptyImport = afterImport;
    }

    @Override
    public int getEdgePosition(final List<IElementType> tokens, final boolean atStreamEdge, final TokenTextGetter
      getter) {
      if (tokens.size() == 0) return 0;

      // 1. bind doc comment
      for (int idx = tokens.size() - 1; idx >= 0; idx--) {
        if (tokens.get(idx) == JavaDocElementType.DOC_COMMENT) return idx;
      }

      // 2. bind plain comments
      int result = tokens.size();
      for (int idx = tokens.size() - 1; idx >= 0; idx--) {
        final IElementType tokenType = tokens.get(idx);
        if (TokenSet.WHITE_SPACE.contains(tokenType)) {
          if (StringUtil.getLineBreakCount(getter.get(idx)) > 1) break;
        }
        else if (ElementType.JAVA_PLAIN_COMMENT_BIT_SET.contains(tokenType)) {
          if (atStreamEdge ||
              (idx == 0 && myAfterEmptyImport) ||
              (idx > 0 && TokenSet.WHITE_SPACE.contains(tokens.get(idx - 1)) && StringUtil.containsLineBreak(getter.get(idx - 1)))) {
            result = idx;
          }
        }
        else break;
      }

      return result;
    }
  }

  private static class TrailingWhitespacesAndCommentsBinder implements WhitespacesAndCommentsBinder {
    @Override
    public int getEdgePosition(final List<IElementType> tokens, final boolean atStreamEdge, final TokenTextGetter getter) {
      if (tokens.size() == 0) return 0;

      int result = 0;
      for (int idx = 0; idx < tokens.size(); idx++) {
        final IElementType tokenType = tokens.get(idx);
        if (TokenSet.WHITE_SPACE.contains(tokenType)) {
          if (StringUtil.containsLineBreak(getter.get(idx))) break;
        }
        else if (ElementType.JAVA_PLAIN_COMMENT_BIT_SET.contains(tokenType)) {
          result = idx + 1;
        }
        else break;
      }

      return result;
    }
  }

  private static final TokenSet PRECEDING_COMMENT_SET = TokenSet.orSet(
    TokenSet.create(JavaElementType.MODULE), ElementType.FULL_MEMBER_BIT_SET);

  private static final TokenSet TRAILING_COMMENT_SET = TokenSet.orSet(
    TokenSet.create(JavaElementType.PACKAGE_STATEMENT),
    ElementType.IMPORT_STATEMENT_BASE_BIT_SET, ElementType.FULL_MEMBER_BIT_SET, ElementType.JAVA_STATEMENT_BIT_SET);

  public static final WhitespacesAndCommentsBinder PRECEDING_COMMENT_BINDER = new PrecedingWhitespacesAndCommentsBinder(false);
  public static final WhitespacesAndCommentsBinder SPECIAL_PRECEDING_COMMENT_BINDER = new PrecedingWhitespacesAndCommentsBinder(true);
  public static final WhitespacesAndCommentsBinder TRAILING_COMMENT_BINDER = new TrailingWhitespacesAndCommentsBinder();

  private JavaParserUtil() { }

  public static void setLanguageLevel(final PsiBuilder builder, final LanguageLevel level) {
    builder.putUserData(LANG_LEVEL_KEY, level);
  }

  @NotNull
  public static LanguageLevel getLanguageLevel(final PsiBuilder builder) {
    final LanguageLevel level = builder.getUserData(LANG_LEVEL_KEY);
    assert level != null : builder;
    return level;
  }

  public static void setParseStatementCodeBlocksDeep(final PsiBuilder builder, final boolean deep) {
    builder.putUserData(DEEP_PARSE_BLOCKS_IN_STATEMENTS, deep);
  }

  public static boolean isParseStatementCodeBlocksDeep(final PsiBuilder builder) {
    return Boolean.TRUE.equals(builder.getUserData(DEEP_PARSE_BLOCKS_IN_STATEMENTS));
  }

  @NotNull
  public static PsiBuilder createBuilder(final ASTNode chameleon) {
    final PsiElement psi = chameleon.getPsi();
    assert psi != null : chameleon;
    final Project project = psi.getProject();

    CharSequence text;
    if (TreeUtil.isCollapsedChameleon(chameleon)) {
      text = chameleon.getChars();
    }
    else {
      text = psi.getUserData(IndexingDataKeys.FILE_TEXT_CONTENT_KEY);
      if (text == null) text = chameleon.getChars();
    }

    final PsiBuilderFactory factory = PsiBuilderFactory.getInstance();
    final LanguageLevel level = PsiUtil.getLanguageLevel(psi);
    final Lexer lexer = JavaParserDefinition.createLexer(level);
    Language language = psi.getLanguage();
    if (!language.isKindOf(JavaLanguage.INSTANCE)) language = JavaLanguage.INSTANCE;
    final PsiBuilder builder = factory.createBuilder(project, chameleon, lexer, language, text);
    setLanguageLevel(builder, level);

    return builder;
  }

  @NotNull
  public static PsiBuilder createBuilder(final LighterLazyParseableNode chameleon) {
    final PsiElement psi = chameleon.getContainingFile();
    assert psi != null : chameleon;
    final Project project = psi.getProject();

    final PsiBuilderFactory factory = PsiBuilderFactory.getInstance();
    final LanguageLevel level = PsiUtil.getLanguageLevel(psi);
    final Lexer lexer = JavaParserDefinition.createLexer(level);
    final PsiBuilder builder = factory.createBuilder(project, chameleon, lexer, chameleon.getTokenType().getLanguage(), chameleon.getText());
    setLanguageLevel(builder, level);

    return builder;
  }

  @Nullable
  public static ASTNode parseFragment(final ASTNode chameleon, final ParserWrapper wrapper) {
    return parseFragment(chameleon, wrapper, true, LanguageLevel.HIGHEST);
  }

  @Nullable
  public static ASTNode parseFragment(final ASTNode chameleon, final ParserWrapper wrapper, final boolean eatAll, final LanguageLevel level) {
    final PsiElement psi = (chameleon.getTreeParent() != null ? chameleon.getTreeParent().getPsi() : chameleon.getPsi());
    assert psi != null : chameleon;
    final Project project = psi.getProject();

    final PsiBuilderFactory factory = PsiBuilderFactory.getInstance();
    final Lexer lexer = chameleon.getElementType() == JavaDocElementType.DOC_COMMENT
                        ? JavaParserDefinition.createDocLexer(level) : JavaParserDefinition.createLexer(level);
    final PsiBuilder builder = factory.createBuilder(project, chameleon, lexer, chameleon.getElementType().getLanguage(), chameleon.getChars());
    setLanguageLevel(builder, level);

    final PsiBuilder.Marker root = builder.mark();
    wrapper.parse(builder);
    if (!builder.eof()) {
      if (!eatAll) throw new AssertionError("Unexpected tokens");
      final PsiBuilder.Marker extras = builder.mark();
      while (!builder.eof()) builder.advanceLexer();
      extras.error(JavaErrorMessages.message("unexpected.tokens"));
    }
    root.done(chameleon.getElementType());

    return builder.getTreeBuilt().getFirstChildNode();
  }

  public static void done(final PsiBuilder.Marker marker, final IElementType type) {
    marker.done(type);
    final WhitespacesAndCommentsBinder left = PRECEDING_COMMENT_SET.contains(type) ? PRECEDING_COMMENT_BINDER : null;
    final WhitespacesAndCommentsBinder right = TRAILING_COMMENT_SET.contains(type) ? TRAILING_COMMENT_BINDER : null;
    marker.setCustomEdgeTokenBinders(left, right);
  }

  @Nullable
  public static IElementType exprType(@Nullable final PsiBuilder.Marker marker) {
    return marker != null ? ((LighterASTNode)marker).getTokenType() : null;
  }

  // used instead of PsiBuilder.error() as it keeps all subsequent error messages
  public static void error(final PsiBuilder builder, final String message) {
    builder.mark().error(message);
  }

  public static void error(final PsiBuilder builder, final String message, @Nullable final PsiBuilder.Marker before) {
    if (before == null) {
      error(builder, message);
    }
    else {
      before.precede().errorBefore(message, before);
    }
  }

  public static boolean expectOrError(PsiBuilder builder, TokenSet expected, @PropertyKey(resourceBundle = JavaErrorMessages.BUNDLE) String key) {
    if (!PsiBuilderUtil.expect(builder, expected)) {
      error(builder, JavaErrorMessages.message(key));
      return false;
    }
    return true;
  }

  public static boolean expectOrError(PsiBuilder builder, IElementType expected, @PropertyKey(resourceBundle = JavaErrorMessages.BUNDLE) String key) {
    if (!PsiBuilderUtil.expect(builder, expected)) {
      error(builder, JavaErrorMessages.message(key));
      return false;
    }
    return true;
  }

  public static void emptyElement(final PsiBuilder builder, final IElementType type) {
    builder.mark().done(type);
  }

  public static void emptyElement(final PsiBuilder.Marker before, final IElementType type) {
    before.precede().doneBefore(type, before);
  }

  public static void semicolon(final PsiBuilder builder) {
    expectOrError(builder, JavaTokenType.SEMICOLON, "expected.semicolon");
  }

  public static PsiBuilder braceMatchingBuilder(final PsiBuilder builder) {
    final PsiBuilder.Marker pos = builder.mark();

    int braceCount = 1;
    while (!builder.eof()) {
      final IElementType tokenType = builder.getTokenType();
      if (tokenType == JavaTokenType.LBRACE) braceCount++;
      else if (tokenType == JavaTokenType.RBRACE) braceCount--;
      if (braceCount == 0) break;
      builder.advanceLexer();
    }
    final int stopAt = builder.getCurrentOffset();

    pos.rollbackTo();

    return stoppingBuilder(builder, stopAt);
  }

  public static PsiBuilder stoppingBuilder(final PsiBuilder builder, final int stopAt) {
    return new PsiBuilderAdapter(builder) {
      @Override
      public IElementType getTokenType() {
        return getCurrentOffset() < stopAt ? super.getTokenType() : null;
      }

      @Override
      public boolean eof() {
        return getCurrentOffset() >= stopAt || super.eof();
      }
    };
  }

  public static PsiBuilder stoppingBuilder(final PsiBuilder builder, final Condition<Pair<IElementType, String>> condition) {
    return new PsiBuilderAdapter(builder) {
      @Override
      public IElementType getTokenType() {
        final Pair<IElementType, String> input = Pair.create(builder.getTokenType(), builder.getTokenText());
        return condition.value(input) ? null : super.getTokenType();
      }

      @Override
      public boolean eof() {
        final Pair<IElementType, String> input = Pair.create(builder.getTokenType(), builder.getTokenText());
        return condition.value(input) || super.eof();
      }
    };
  }
}