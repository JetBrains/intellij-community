// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.pratt;

import com.intellij.lang.ITokenTypeRemapper;
import com.intellij.lang.LangBundle;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

/**
 * @author peter
 */
public class PrattBuilderImpl extends PrattBuilder {
  private final PsiBuilder myBuilder;
  private final PrattBuilder myParentBuilder;
  private final PrattRegistry myRegistry;
  private final LinkedList<IElementType> myLeftSiblings = new LinkedList<>();
  private boolean myParsingStarted;
  private @NlsContexts.ParsingError String myExpectedMessage;
  private int myPriority = Integer.MIN_VALUE;
  private MutableMarker myStartMarker;

  private PrattBuilderImpl(final PsiBuilder builder, final PrattBuilder parent, final PrattRegistry registry) {
    myBuilder = builder;
    myParentBuilder = parent;
    myRegistry = registry;
  }

  public static PrattBuilder createBuilder(final PsiBuilder builder, final PrattRegistry registry) {
    return new PrattBuilderImpl(builder, null, registry);
  }

  @Override
  public PrattBuilder expecting(final @NlsContexts.ParsingError String expectedMessage) {
    myExpectedMessage = expectedMessage;
    return this;
  }

  @Override
  public PrattBuilder withLowestPriority(final int priority) {
    myPriority = priority;
    return this;
  }

  @Override
  public Lexer getLexer() {
    return ((PsiBuilderImpl) myBuilder).getLexer();
  }

  @Override
  public void setTokenTypeRemapper(@Nullable final ITokenTypeRemapper remapper) {
    myBuilder.setTokenTypeRemapper(remapper);
  }

  @Override
  public MutableMarker mark() {
    return new MutableMarker(myLeftSiblings, myBuilder.mark(), myLeftSiblings.size());
  }

  @Override
  @Nullable
  public IElementType parse() {
    checkParsed();
    return myLeftSiblings.size() != 1 ? null : myLeftSiblings.getLast();
  }

  @Override
  protected PrattBuilder createChildBuilder() {
    assert myParsingStarted;
    return new PrattBuilderImpl(myBuilder, this, myRegistry) {
      @Override
      protected void doParse() {
        super.doParse();
        PrattBuilderImpl.this.myLeftSiblings.addAll(getResultTypes());
      }
    };
  }

  protected void doParse() {
    if (isEof()) {
      error(myExpectedMessage != null ? myExpectedMessage : LangBundle.message("unexpected.eof"));
      return;
    }

    TokenParser parser = findParser();
    if (parser == null) {
      error(myExpectedMessage != null ? myExpectedMessage : LangBundle.message("unexpected.token"));
      return;
    }

    myStartMarker = mark();
    while (!isEof()) {
      int startOffset = myBuilder.getCurrentOffset();

      if (!parser.parseToken(this)) break;

      assert startOffset < myBuilder.getCurrentOffset() : "Endless loop on " + getTokenType();

      parser = findParser();
      if (parser == null) break;
    }
    myStartMarker.drop();
  }

  @Nullable
  private TokenParser findParser() {
    final IElementType tokenType = getTokenType();
    for (final Trinity<Integer, PathPattern, TokenParser> trinity : myRegistry.getParsers(tokenType)) {
      if (trinity.first > myPriority && trinity.second.accepts(this)) {
        return trinity.third;
      }
    }
    return null;
  }

  @Override
  public void advance() {
    myLeftSiblings.addLast(getTokenType());
    myBuilder.advanceLexer();
  }

  @Override
  public void error(@NotNull @NlsContexts.ParsingError final String errorText) {
    final PsiBuilder.Marker marker = myBuilder.mark();
    myBuilder.error(errorText);
    marker.drop();
  }

  @Override
  @Nullable
  public IElementType getTokenType() {
    return myBuilder.getTokenType();
  }

  @Override
  @Nullable
  public String getTokenText() {
    return myBuilder.getTokenText();
  }

  @Override
  public void reduce(@NotNull final IElementType type) {
    myStartMarker.finish(type);
    myStartMarker = myStartMarker.precede();
  }

  @Override
  @NotNull
  public List<IElementType> getResultTypes() {
    checkParsed();
    return myLeftSiblings;
  }

  private void checkParsed() {
    if (!myParsingStarted) {
      myParsingStarted = true;
      doParse();
    }
  }

  @Override
  public PrattBuilder getParent() {
    return myParentBuilder;
  }

  @Override
  public int getPriority() {
    return myPriority;
  }

  @Override
  public int getCurrentOffset() {
    return myBuilder.getCurrentOffset();
  }
}
