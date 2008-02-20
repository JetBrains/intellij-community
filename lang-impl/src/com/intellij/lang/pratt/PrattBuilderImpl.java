/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.pratt;

import com.intellij.lang.LangBundle;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;

/**
 * @author peter
 */
public class PrattBuilderImpl extends PrattBuilder implements PrattBuilderFacade {
  private final PrattBuilder myParentBuilder;
  private final PsiBuilder myBuilder;
  private final LinkedList<IElementType> myLeftSiblings = new LinkedList<IElementType>();
  private boolean myParsingStarted;
  private String myExpectedMessage;
  private int myPriority = Integer.MIN_VALUE;
  private MutableMarker myStartMarker;

  private PrattBuilderImpl(final PsiBuilder builder, final PrattBuilder parent) {
    myBuilder = builder;
    myParentBuilder = parent;
  }

  public static PrattBuilderImpl createBuilder(final PsiBuilder builder) {
    return new PrattBuilderImpl(builder, null);
  }

  public PrattBuilderFacade expecting(final String expectedMessage) {
    myExpectedMessage = expectedMessage;
    return this;
  }

  public PrattBuilderFacade withLowestPriority(final int priority) {
    myPriority = priority;
    return this;
  }

  public Lexer getLexer() {
    return ((PsiBuilderImpl) myBuilder).getLexer();
  }

  public MutableMarker mark() {
    return new MutableMarker(myLeftSiblings, myBuilder.mark(), myLeftSiblings.size());
  }

  @Nullable
  public IElementType parse() {
    checkParsed();
    return myLeftSiblings.size() != 1 ? null : myLeftSiblings.getLast();
  }

  protected PrattBuilderFacade createChildBuilder() {
    assert myParsingStarted;
    return new PrattBuilderImpl(myBuilder, this) {
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
    for (final Trinity<Integer, PathPattern, TokenParser> trinity : PrattRegistry.getParsers(tokenType)) {
      if (trinity.first > myPriority && trinity.second.accepts(this)) {
        return trinity.third;
      }
    }
    return null;
  }

  public boolean assertToken(final PrattTokenType type, @NotNull final String errorMessage) {
    return _checkToken(type, errorMessage);
  }

  public boolean checkToken(final PrattTokenType type) {
    return _checkToken(type, null);
  }

  private boolean _checkToken(final PrattTokenType type, @Nullable String errorMessage) {
    if (isToken(type)) {
      advance();
      return true;
    }
    if (errorMessage != null) {
      error(errorMessage);
    }
    return false;
  }

  public void advance() {
    myLeftSiblings.addLast(getTokenType());
    myBuilder.advanceLexer();
  }

  public void error(final String errorText) {
    final PsiBuilder.Marker marker = myBuilder.mark();
    myBuilder.error(errorText);
    marker.drop();
  }

  @Nullable
  public IElementType getTokenType() {
    return myBuilder.getTokenType();
  }

  @Nullable
  public String getTokenText() {
    return myBuilder.getTokenText();
  }

  public void reduce(@NotNull final IElementType type) {
    myStartMarker.finish(type);
    myStartMarker = myStartMarker.precede();
  }

  @NotNull
  public LinkedList<IElementType> getResultTypes() {
    checkParsed();
    return myLeftSiblings;
  }

  private void checkParsed() {
    if (!myParsingStarted) {
      myParsingStarted = true;
      doParse();
    }
  }

  public PrattBuilder getParent() {
    return myParentBuilder;
  }

  public int getPriority() {
    return myPriority;
  }
}
