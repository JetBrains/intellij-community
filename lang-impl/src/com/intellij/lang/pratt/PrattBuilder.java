/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.pratt;

import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ITokenTypeRemapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.ListIterator;

/**
 * @author peter
 */
public abstract class PrattBuilder {

  public abstract Lexer getLexer();

  public abstract void setTokenTypeRemapper(@Nullable ITokenTypeRemapper remapper);

  public abstract MutableMarker mark();

  public PrattBuilderFacade createChildBuilder(int priority, @Nullable String expectedMessage) {
    return createChildBuilder(priority).expecting(expectedMessage);
  }

  public PrattBuilderFacade createChildBuilder(int priority) {
    return createChildBuilder().withLowestPriority(priority);
  }

  protected abstract PrattBuilderFacade createChildBuilder();

  public boolean assertToken(final PrattTokenType type) {
    return assertToken(type, type.getExpectedText(this));
  }

  public abstract boolean assertToken(PrattTokenType type, @NotNull String errorMessage);

  public abstract boolean checkToken(PrattTokenType type);

  public abstract void advance();

  public abstract void error(String errorText);

  public boolean isEof() {
    return isToken(null);
  }

  public boolean isToken(@Nullable IElementType type) {
    return getTokenType() == type;
  }

  @Nullable
  public abstract IElementType getTokenType();

  @Nullable
  public abstract String getTokenText();

  public abstract void reduce(@NotNull IElementType type);

  public ListIterator<IElementType> getBackResultIterator() {
    final LinkedList<IElementType> resultTypes = getResultTypes();
    return resultTypes.listIterator(resultTypes.size());
  }

  public abstract LinkedList<IElementType> getResultTypes();

  public abstract PrattBuilder getParent();

  public abstract int getPriority();

}
