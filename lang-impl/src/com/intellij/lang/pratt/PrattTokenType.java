/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.pratt;

import com.intellij.lang.Language;
import com.intellij.psi.PsiBundle;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 */
public class PrattTokenType extends IElementType {
  protected TokenParser myParser;
  private final int myPriority;

  public PrattTokenType(@NotNull final String tokenName,
                          @Nullable final Language language, final int priority, @Nullable final Nud nud, @Nullable final Led led) {
    this(tokenName, language, priority, new DefaultTokenParser(nud, led));
  }

  public PrattTokenType(@NotNull @NonNls final String debugName,
                        @Nullable final Language language, final int priority, final TokenParser parser) {
    super(debugName, language);
    myPriority = priority;
    myParser = parser;
  }

  public PrattTokenType(@NotNull final String tokenName,
                          @Nullable final Language language, final int priority, @Nullable final Nud nud) {
    this(tokenName, language, priority, nud, null);
  }

  public PrattTokenType(@NotNull final String tokenName,
                          @Nullable final Language language, final int priority, @Nullable final Led led) {
    this(tokenName, language, priority, null, led);
  }

  public PrattTokenType(@NotNull final String tokenName,
                          @Nullable final Language language, final int priority) {
    this(tokenName, language, priority, null, null);
  }

  public String getExpectedText(final PrattBuilder builder) {
    return PsiBundle.message("0.expected", toString());
  }

  public final int getPriority() {
    return myPriority;
  }

  public TokenParser getParser() {
    return myParser;
  }
}
