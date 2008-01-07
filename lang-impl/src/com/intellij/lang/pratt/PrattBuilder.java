/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.pratt;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.Stack;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.Iterator;

/**
 * @author peter
 */
public class PrattBuilder {
  private final PsiBuilder myBuilder;
  private final LinkedList<Object> myPath = new LinkedList<Object>();
  private final Stack<PrattTokenType> myStack = new Stack<PrattTokenType>();
  private MutableMarker myStartMarker;
  private IElementType myLastReduced;

  public PrattBuilder(final PsiBuilder builder) {
    myBuilder = builder;
  }

  public Lexer getLexer() {
    return ((PsiBuilderImpl) myBuilder).getLexer();
  }

  public MutableMarker mark() {
    return new MutableMarker(myPath, myBuilder.mark(), myPath.size());
  }

  @Nullable
  public IElementType parse(int rightPriority) {
    return parse(rightPriority, null);
  }

  @Nullable
  public IElementType parse(int rightPriority, @Nullable String expectedMessage) {
    if (isEof()) {
      error(expectedMessage != null ? expectedMessage : JavaErrorMessages.message("unexpected.eof"));
      return null;
    }

    myPath.addFirst(Boolean.TRUE);

    TokenParser parser = findParser(rightPriority);
    if (parser == null) {
      myPath.removeFirst();
      error(expectedMessage != null ? expectedMessage : JavaErrorMessages.message("unexpected.token"));
      return null;
    }


    final MutableMarker oldStartMarker = myStartMarker;
    final IElementType oldLastReduced = myLastReduced;
    myStartMarker = mark();
    try {
      while (!isEof()) {
        int startOffset = myBuilder.getCurrentOffset();
        PrattTokenType tokenType = (PrattTokenType)getTokenType();
        assert tokenType != null;

        myLastReduced = null;
        pushToken(tokenType);
        try {
          if (!parser.parseToken(this)) break;
        }
        finally {
          popToken();
        }

        assert startOffset < myBuilder.getCurrentOffset() : "Endless loop on " + getTokenType();

        parser = findParser(rightPriority);
        if (parser == null) break;
      }

      myStartMarker.finish();
      return myLastReduced;
    }
    finally {
      for (Iterator<Object> iterator = myPath.iterator(); iterator.hasNext();) {
        Object o = iterator.next();
        if (o == Boolean.TRUE) {
          iterator.remove();
          break;
        }
      }      

      myStartMarker = oldStartMarker;
      myLastReduced = oldLastReduced;
    }
  }

  public void popToken() {
    myStack.pop();
  }

  public void pushToken(@NotNull final PrattTokenType tokenType) {
    myStack.push(tokenType);
  }

  @Nullable
  private TokenParser findParser(final int rightPriority) {
    final List<Trinity<Integer,PathPattern,TokenParser>> parsers = PrattRegistry.getParsers(getTokenType());
    final Trinity<Integer, PathPattern, TokenParser> parserTrinity =
      ContainerUtil.find(parsers, new Condition<Trinity<Integer, PathPattern, TokenParser>>() {
        public boolean value(final Trinity<Integer, PathPattern, TokenParser> trinity) {
          return trinity.first > rightPriority && trinity.second.accepts(PrattBuilder.this);
        }
      });
    return parserTrinity == null ? null : parserTrinity.third;
  }

  public boolean assertToken(final PrattTokenType type) {
    return assertToken(type, type.getExpectedText(this));
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
    myPath.addFirst(getTokenType());
    myBuilder.advanceLexer();
  }

  public void error(final String errorText) {
    final PsiBuilder.Marker marker = myBuilder.mark();
    myBuilder.error(errorText);
    marker.drop();
  }

  public boolean isEof() {
    return isToken(null);
  }

  public boolean isToken(@Nullable IElementType type) {
    return getTokenType() == type;
  }

  @Nullable
  public IElementType getTokenType() {
    return myBuilder.getTokenType();
  }

  /**
   * @param depth 0 is current
   * @return
   */
  @Nullable
  public PrattTokenType getParsedToken(int depth) {
    return myStack.size() - 1 < depth ? null : myStack.get(myStack.size() - 1 - depth);
  }

  @Nullable
  public String getTokenText() {
    return myBuilder.getTokenText();
  }

  public void reduce(@NotNull final IElementType type) {
    myLastReduced = type;
    myStartMarker.finish(type);
    myStartMarker = myStartMarker.precede();
  }

  protected LinkedList<Object> getPath() {
    return myPath;
  }
}
