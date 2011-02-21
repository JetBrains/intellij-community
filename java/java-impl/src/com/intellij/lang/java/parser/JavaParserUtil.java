/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.java.parser;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.*;
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
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public class JavaParserUtil {
  private static final Key<LanguageLevel> LANG_LEVEL_KEY = Key.create("JavaParserUtil.LanguageLevel");
  private static final Key<Boolean> DEEP_PARSE_BLOCKS_IN_STATEMENTS = Key.create("JavaParserUtil.ParserExtender");
  private static final Key<ParserExtender> PARSER_EXTENDER_KEY = Key.create("JavaParserUtil.ParserExtender");

  public interface ParserWrapper {
    void parse(PsiBuilder builder);
  }

  public interface MarkingParserWrapper {
    @Nullable PsiBuilder.Marker parse(PsiBuilder builder);
  }

  public interface ParserExtender extends MarkingParserWrapper {
    boolean enroll(PsiBuilder builder);
  }

  public static final WhitespacesAndCommentsBinder GREEDY_RIGHT_EDGE_PROCESSOR = new WhitespacesAndCommentsBinder() {
    public int getEdgePosition(final List<IElementType> tokens, final boolean atStreamEdge, final TokenTextGetter getter) {
      return tokens.size();
    }
  };

  private static class PrecedingWhitespacesAndCommentsBinder implements WhitespacesAndCommentsBinder {
    private final boolean myAfterEmptyImport;

    public PrecedingWhitespacesAndCommentsBinder(final boolean afterImport) {
      this.myAfterEmptyImport = afterImport;
    }

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
        if (ElementType.JAVA_WHITESPACE_BIT_SET.contains(tokenType)) {
          if (StringUtil.getLineBreakCount(getter.get(idx)) > 1) break;
        }
        else if (ElementType.JAVA_PLAIN_COMMENT_BIT_SET.contains(tokenType)) {
          if (atStreamEdge ||
              (idx == 0 && myAfterEmptyImport) ||
              (idx > 0 && ElementType.JAVA_WHITESPACE_BIT_SET.contains(tokens.get(idx - 1)) && StringUtil.containsLineBreak(getter.get(idx - 1)))) {
            result = idx;
          }
        }
        else break;
      }

      return result;
    }
  }

  private static class TrailingWhitespacesAndCommentsBinder implements WhitespacesAndCommentsBinder {
    public int getEdgePosition(final List<IElementType> tokens, final boolean atStreamEdge, final TokenTextGetter getter) {
      if (tokens.size() == 0) return 0;

      int result = 0;
      for (int idx = 0; idx < tokens.size(); idx++) {
        final IElementType tokenType = tokens.get(idx);
        if (ElementType.JAVA_WHITESPACE_BIT_SET.contains(tokenType)) {
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

  private static final TokenSet PRECEDING_COMMENT_SET = ElementType.FULL_MEMBER_BIT_SET;
  private static final TokenSet TRAILING_COMMENT_SET = TokenSet.orSet(
    TokenSet.create(JavaElementType.PACKAGE_STATEMENT),
    ElementType.IMPORT_STATEMENT_BASE_BIT_SET, ElementType.FULL_MEMBER_BIT_SET, ElementType.JAVA_STATEMENT_BIT_SET);

  public static final WhitespacesAndCommentsBinder PRECEDING_COMMENT_BINDER = new PrecedingWhitespacesAndCommentsBinder(false);
  public static final WhitespacesAndCommentsBinder SPECIAL_PRECEDING_COMMENT_BINDER = new PrecedingWhitespacesAndCommentsBinder(true);
  public static final WhitespacesAndCommentsBinder TRAILING_COMMENT_BINDER = new TrailingWhitespacesAndCommentsBinder();

  private JavaParserUtil() { }

  public static void setLanguageLevel(final PsiBuilder builder, final LanguageLevel level) {
    builder.putUserDataUnprotected(LANG_LEVEL_KEY, level);
  }

  // todo[r.sh] join all JDK 7 check clauses into single method (IDEA 11)
  public static boolean areDiamondsSupported(final PsiBuilder builder) {
    return getLanguageLevel(builder).isAtLeast(LanguageLevel.JDK_1_7);
  }
  public static boolean areMultiCatchSupported(final PsiBuilder builder) {
    return getLanguageLevel(builder).isAtLeast(LanguageLevel.JDK_1_7);
  }
  public static boolean areTryWithResourcesSupported(final PsiBuilder builder) {
    return getLanguageLevel(builder).isAtLeast(LanguageLevel.JDK_1_7);
  }

  public static boolean areTypeAnnotationsSupported(final PsiBuilder builder) {
    return getLanguageLevel(builder).isAtLeast(LanguageLevel.JDK_1_8);
  }

  @NotNull
  public static LanguageLevel getLanguageLevel(final PsiBuilder builder) {
    final LanguageLevel level = builder.getUserDataUnprotected(LANG_LEVEL_KEY);
    assert level != null : builder;
    return level;
  }

  public static void setParseStatementCodeBlocksDeep(final PsiBuilder builder, final boolean deep) {
    builder.putUserDataUnprotected(DEEP_PARSE_BLOCKS_IN_STATEMENTS, deep);
  }

  public static boolean isParseStatementCodeBlocksDeep(final PsiBuilder builder) {
    return Boolean.TRUE.equals(builder.getUserDataUnprotected(DEEP_PARSE_BLOCKS_IN_STATEMENTS));
  }

  public static void setParserExtender(final PsiBuilder builder, final ParserExtender extender) {
    builder.putUserDataUnprotected(PARSER_EXTENDER_KEY, extender);
  }

  @Nullable
  public static ParserExtender getParserExtender(final PsiBuilder builder) {
    final ParserExtender extender = builder.getUserDataUnprotected(PARSER_EXTENDER_KEY);
    return extender != null && extender.enroll(builder) ? extender : null;
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
      text = psi.getUserData(StubUpdatingIndex.FILE_TEXT_CONTENT_KEY);
      if (text == null) text = chameleon.getChars();
    }

    final PsiBuilderFactory factory = PsiBuilderFactory.getInstance();
    final LanguageLevel level = PsiUtil.getLanguageLevel(psi);
    final Lexer lexer = JavaParserDefinition.createLexer(level);
    final PsiBuilder builder = factory.createBuilder(project, chameleon, lexer, psi.getLanguage(), text);
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
    final Lexer lexer = JavaParserDefinition.createLexer(level);
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

  public static boolean expectOrError(final PsiBuilder builder, final IElementType expectedType, final String errorMessage) {
    if (!PsiBuilderUtil.expect(builder, expectedType)) {
      error(builder, errorMessage);
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
    expectOrError(builder, JavaTokenType.SEMICOLON, JavaErrorMessages.message("expected.semicolon"));
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

  public static class PsiBuilderAdapter implements PsiBuilder {
    protected final PsiBuilder myDelegate;

    public PsiBuilderAdapter(final PsiBuilder delegate) {
      myDelegate = delegate;
    }

    @Override
    public Project getProject() {
      return myDelegate.getProject();
    }

    @Override
    public CharSequence getOriginalText() {
      return myDelegate.getOriginalText();
    }

    @Override
    public void advanceLexer() {
      myDelegate.advanceLexer();
    }

    @Override @Nullable
    public IElementType getTokenType() {
      return myDelegate.getTokenType();
    }

    @Override
    public void setTokenTypeRemapper(final ITokenTypeRemapper remapper) {
      myDelegate.setTokenTypeRemapper(remapper);
    }

    @Override
    public void setWhitespaceSkippedCallback(WhitespaceSkippedCallback callback) {
      myDelegate.setWhitespaceSkippedCallback(callback);
    }

    @Override
    public void remapCurrentToken(IElementType type) {
      myDelegate.remapCurrentToken(type);
    }

    @Override
    public IElementType lookAhead(int steps) {
      return myDelegate.lookAhead(steps);
    }

    @Override
    public IElementType rawLookup(int steps) {
      return myDelegate.rawLookup(steps);
    }

    @Override
    public int rawTokenTypeStart(int steps) {
      return myDelegate.rawTokenTypeStart(steps);
    }

    @Override @Nullable @NonNls
    public String getTokenText() {
      return myDelegate.getTokenText();
    }

    @Override
    public int getCurrentOffset() {
      return myDelegate.getCurrentOffset();
    }

    @Override
    public Marker mark() {
      return myDelegate.mark();
    }

    @Override
    public void error(final String messageText) {
      myDelegate.error(messageText);
    }

    @Override
    public boolean eof() {
      return myDelegate.eof();
    }

    @Override
    public ASTNode getTreeBuilt() {
      return myDelegate.getTreeBuilt();
    }

    @Override
    public FlyweightCapableTreeStructure<LighterASTNode> getLightTree() {
      return myDelegate.getLightTree();
    }

    @Override
    public void setDebugMode(final boolean dbgMode) {
      myDelegate.setDebugMode(dbgMode);
    }

    @Override
    public void enforceCommentTokens(final TokenSet tokens) {
      myDelegate.enforceCommentTokens(tokens);
    }

    @Override @Nullable
    public LighterASTNode getLatestDoneMarker() {
      return myDelegate.getLatestDoneMarker();
    }

    @Override @Nullable
    public <T> T getUserData(@NotNull final Key<T> key) {
      return myDelegate.getUserData(key);
    }

    @Override
    public <T> void putUserData(@NotNull final Key<T> key, @Nullable final T value) {
      myDelegate.putUserData(key, value);
    }

    @Override
    public <T> T getUserDataUnprotected(@NotNull final Key<T> key) {
      return myDelegate.getUserDataUnprotected(key);
    }

    @Override
    public <T> void putUserDataUnprotected(@NotNull final Key<T> key, @Nullable final T value) {
      myDelegate.putUserDataUnprotected(key, value);
    }
  }
}
