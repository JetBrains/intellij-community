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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public class JavaParserUtil {
  private static final Key<LanguageLevel> LANG_LEVEL_KEY = Key.create("JavaParserUtil.LanguageLevel");

  public interface ParserWrapper {
    void parse(PsiBuilder builder);
  }

  public interface MarkingParserWrapper {
    @Nullable PsiBuilder.Marker parse(PsiBuilder builder);
  }

  public static final WhitespacesAndCommentsProcessor GREEDY_RIGHT_EDGE_PROCESSOR = new WhitespacesAndCommentsProcessor() {
    public int process(final List<IElementType> tokens, final boolean atStreamEdge, final TokenTextGetter getter) {
      return tokens.size();
    }
  };

  private static class PrecedingWhitespacesAndCommentsProcessor implements WhitespacesAndCommentsProcessor {
    private final boolean myAfterEmptyImport;

    public PrecedingWhitespacesAndCommentsProcessor(final boolean afterImport) {
      this.myAfterEmptyImport = afterImport;
    }

    public int process(final List<IElementType> tokens, final boolean atStreamEdge, final TokenTextGetter
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

  private static class TrailingWhitespacesAndCommentsProcessor implements WhitespacesAndCommentsProcessor {
    public int process(final List<IElementType> tokens, final boolean atStreamEdge, final TokenTextGetter getter) {
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

  public static final WhitespacesAndCommentsProcessor PRECEDING_COMMENT_BINDER = new PrecedingWhitespacesAndCommentsProcessor(false);
  public static final WhitespacesAndCommentsProcessor SPECIAL_PRECEDING_COMMENT_BINDER = new PrecedingWhitespacesAndCommentsProcessor(true);
  public static final WhitespacesAndCommentsProcessor TRAILING_COMMENT_BINDER = new TrailingWhitespacesAndCommentsProcessor();

  private JavaParserUtil() { }

  public static void setLanguageLevel(final PsiBuilder builder, final LanguageLevel level) {
    builder.putUserData(LANG_LEVEL_KEY, level);
  }

  public static boolean areTypeAnnotationsSupported(final PsiBuilder builder) {
    return getLanguageLevel(builder).isAtLeast(LanguageLevel.JDK_1_7);
  }

  public static boolean areDiamondsSupported(final PsiBuilder builder) {
    return getLanguageLevel(builder).isAtLeast(LanguageLevel.JDK_1_7);
  }

  @NotNull
  private static LanguageLevel getLanguageLevel(final PsiBuilder builder) {
    final LanguageLevel level = builder.getUserData(LANG_LEVEL_KEY);
    assert level != null : builder;
    return level;
  }

  @NotNull
  public static PsiBuilder createBuilder(final ASTNode chameleon) {
    final PsiElement psi = chameleon.getPsi();
    assert psi != null : chameleon;
    final Project project = psi.getProject();

    final PsiBuilderFactory factory = PsiBuilderFactory.getInstance();
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(StdLanguages.JAVA);
    final PsiBuilder builder = factory.createBuilder(parserDefinition.createLexer(project), StdLanguages.JAVA, chameleon.getChars());

    final LanguageLevel level = LanguageLevelProjectExtension.getInstance(project).getLanguageLevel();
    setLanguageLevel(builder, level);

    return builder;
  }

  @NotNull
  public static ASTNode parseFragment(final ASTNode chameleon, final ParserWrapper wrapper) {
    final PsiBuilderFactory factory = PsiBuilderFactory.getInstance();
    final LanguageLevel level = LanguageLevel.HIGHEST;
    final PsiBuilder builder = factory.createBuilder(JavaParserDefinition.createLexer(level), StdLanguages.JAVA, chameleon.getChars());
    setLanguageLevel(builder, level);

    final PsiBuilder.Marker root = builder.mark();
    wrapper.parse(builder);
    if (!builder.eof()) {
      final PsiBuilder.Marker extras = builder.mark();
      while (!builder.eof()) builder.advanceLexer();
      extras.error(JavaErrorMessages.message("unexpected.tokens"));
    }
    root.done(chameleon.getElementType());

    return builder.getTreeBuilt().getFirstChildNode();
  }

  public static void done(final PsiBuilder.Marker marker, final IElementType type) {
    marker.done(type);
    final WhitespacesAndCommentsProcessor left = PRECEDING_COMMENT_SET.contains(type) ? PRECEDING_COMMENT_BINDER : null;
    final WhitespacesAndCommentsProcessor right = TRAILING_COMMENT_SET.contains(type) ? TRAILING_COMMENT_BINDER : null;
    marker.setCustomEdgeProcessors(left, right);
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

  public static class PsiBuilderAdapter implements PsiBuilder {
    protected final PsiBuilder myDelegate;

    public PsiBuilderAdapter(final PsiBuilder delegate) {
      myDelegate = delegate;
    }

    public CharSequence getOriginalText() {
      return myDelegate.getOriginalText();
    }

    public void advanceLexer() {
      myDelegate.advanceLexer();
    }

    @Nullable
    public IElementType getTokenType() {
      return myDelegate.getTokenType();
    }

    public void setTokenTypeRemapper(final ITokenTypeRemapper remapper) {
      myDelegate.setTokenTypeRemapper(remapper);
    }

    @Nullable @NonNls
    public String getTokenText() {
      return myDelegate.getTokenText();
    }

    public int getCurrentOffset() {
      return myDelegate.getCurrentOffset();
    }

    public Marker mark() {
      return myDelegate.mark();
    }

    public void error(final String messageText) {
      myDelegate.error(messageText);
    }

    public boolean eof() {
      return myDelegate.eof();
    }

    public ASTNode getTreeBuilt() {
      return myDelegate.getTreeBuilt();
    }

    public FlyweightCapableTreeStructure<LighterASTNode> getLightTree() {
      return myDelegate.getLightTree();
    }

    public void setDebugMode(final boolean dbgMode) {
      myDelegate.setDebugMode(dbgMode);
    }

    public void enforceCommentTokens(final TokenSet tokens) {
      myDelegate.enforceCommentTokens(tokens);
    }

    @Nullable
    public LighterASTNode getLatestDoneMarker() {
      return myDelegate.getLatestDoneMarker();
    }

    @Nullable
    public <T> T getUserData(@NotNull final Key<T> key) {
      return myDelegate.getUserData(key);
    }

    public <T> void putUserData(@NotNull final Key<T> key, @Nullable final T value) {
      myDelegate.putUserData(key, value);
    }
  }
}
