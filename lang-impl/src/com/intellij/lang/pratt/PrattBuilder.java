/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.pratt;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class PrattBuilder {
  private final PsiBuilder myBuilder;
  private final Stack<PrattTokenType> myStack = new Stack<PrattTokenType>();
  private final Stack<Pair<PsiBuilder.Marker,IElementType>> myMarkers = new Stack<Pair<PsiBuilder.Marker, IElementType>>();

  public PrattBuilder(final PsiBuilder builder) {
    myBuilder = builder;
  }

  public Lexer getLexer() {
    return ((PsiBuilderImpl) myBuilder).getLexer();
  }

  public void startElement(IElementType type) {
    startElement();
    setCurrentElementType(type);
  }

  public void startElement() {
    myMarkers.push(new Pair<PsiBuilder.Marker, IElementType>(myBuilder.mark(), null));
  }

  public void setCurrentElementType(@Nullable IElementType type) {
    myMarkers.push(new Pair<PsiBuilder.Marker, IElementType>(myMarkers.pop().first, type));
  }

  @Nullable public IElementType getCurrentElementType() {
    return myMarkers.peek().second;
  }

  public void rollbackToElementStart() {
    myMarkers.pop().first.rollbackTo();
  }

  public void finishElement(@Nullable IElementType type) {
    setCurrentElementType(type);
    finishElement();
  }

  public void precedeElement() {
    final Pair<PsiBuilder.Marker, IElementType> pair = myMarkers.pop();
    myMarkers.push(new Pair<PsiBuilder.Marker, IElementType>(pair.first.precede(), null));
    myMarkers.push(pair);
  }

  public void finishElement() {
    final Pair<PsiBuilder.Marker, IElementType> pair = myMarkers.pop();
    final PsiBuilder.Marker marker = pair.first;
    final IElementType type = pair.second;
    if (type != null) {
      marker.done(type);
    } else {
      marker.drop();
    }
  }

  @Nullable
  public IElementType parse(int rightPriority) {
    return parse(rightPriority, null);
  }

  @Nullable
  public IElementType parse(int rightPriority, @Nullable String expectedMessage) {
    if (isEof()) {
      error(expectedMessage != null ? expectedMessage : JavaErrorMessages.message("unexpected.token"));
      return null;
    }

    if (cannotBeParsed(rightPriority)) {
      error(expectedMessage != null ? expectedMessage : JavaErrorMessages.message("unexpected.token"));
      return null;
    }

    int startStack = myMarkers.size();
    startElement();
    while (!isEof()) {
      int startOffset = myBuilder.getCurrentOffset();
      PrattTokenType tokenType = (PrattTokenType)getTokenType();
      assert tokenType != null;

      myStack.push(tokenType);
      try {
        if (!tokenType.getParser().parseToken(this)) break;
      }
      finally {
        myStack.pop();
      }

      assert startOffset < myBuilder.getCurrentOffset() : "Endless loop on " + getTokenType();

      if (cannotBeParsed(rightPriority)) break;
    }

    IElementType result = null;
    do {
      final IElementType currentType = getCurrentElementType();
      if (currentType != null) {
        result = currentType;
      }
      finishElement();
    } while (myMarkers.size() > startStack);
    return result;
  }

  private boolean cannotBeParsed(final int rightPriority) {
    final IElementType tokenType = getTokenType();
    return !(tokenType instanceof PrattTokenType) || ((PrattTokenType)tokenType).getPriority() <= rightPriority;
  }

  public boolean assertToken(final PrattTokenType type) {
    return assertToken(type, type.getExpectedText(this));
  }

  public boolean assertToken(final PrattTokenType type, @NotNull final String errorMessage) {
    return _checkToken(type, errorMessage);
  }

  public PsiBuilder.Marker mark() {
    return myBuilder.mark();
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
    myBuilder.advanceLexer();
  }


  public boolean checkToken(final Class<? extends PrattTokenType> type, @Nullable String errorText) {
    if (type.isInstance(getTokenType())) {
      advance();
      return true;
    }
    if (errorText != null) {
      error(errorText);
    }
    return false;
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
  public PrattTokenType getStackValue(int depth) {
    return myStack.size() - 1 < depth ? null : myStack.get(myStack.size() - 1 - depth);
  }

  @Nullable
  public String getTokenText() {
    return myBuilder.getTokenText();
  }
}
