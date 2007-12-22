/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.pratt;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.lang.ASTNode;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class PrattBuilder {
  public static final PsiParser PRATT_PARSER = new PsiParser() {
    @NotNull
    public ASTNode parse(final IElementType root, final PsiBuilder builder) {
      builder.setDebugMode(true);

      final PrattBuilder prattBuilder = new PrattBuilder(builder);
      final MutableMarker marker = prattBuilder.mark();
      prattBuilder.parse(Integer.MIN_VALUE);
      while (builder.getTokenType() != null) builder.advanceLexer();
      marker.finish(root);
      return builder.getTreeBuilt();
    }
  };

  private final PsiBuilder myBuilder;
  private final Stack<PrattTokenType> myStack = new Stack<PrattTokenType>();
  private MutableMarker myStartMarker;

  public PrattBuilder(final PsiBuilder builder) {
    myBuilder = builder;
  }

  public Lexer getLexer() {
    return ((PsiBuilderImpl) myBuilder).getLexer();
  }

  public MutableMarker getCurrentMarker() {
    return myStartMarker;
  }

  public MutableMarker mark() {
    return new MutableMarker(myBuilder.mark());
  }

  public void precedeCurrentMarker() {
    myStartMarker = myStartMarker.precede();
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

    final MutableMarker oldStartMarker = myStartMarker;
    myStartMarker = mark();
    try {
      while (!isEof()) {
        int startOffset = myBuilder.getCurrentOffset();
        PrattTokenType tokenType = (PrattTokenType)getTokenType();
        assert tokenType != null;

        pushToken(tokenType);
        try {
          if (!tokenType.getParser().parseToken(this)) break;
        }
        finally {
          popToken();
        }

        assert startOffset < myBuilder.getCurrentOffset() : "Endless loop on " + getTokenType();

        if (cannotBeParsed(rightPriority)) break;
      }

      myStartMarker.finish();
      return myStartMarker.getResultType();
    }
    finally {
      myStartMarker = oldStartMarker;
    }
  }

  public void popToken() {
    myStack.pop();
  }

  public void pushToken(@NotNull final PrattTokenType tokenType) {
    myStack.push(tokenType);
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
  public PrattTokenType getParsedToken(int depth) {
    return myStack.size() - 1 < depth ? null : myStack.get(myStack.size() - 1 - depth);
  }

  @Nullable
  public String getTokenText() {
    return myBuilder.getTokenText();
  }
}
