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

/**
 * @author peter
 */
public class PrattTokenType extends IElementType {
  protected Nud myNud;
  protected Led myLed;
  private final int myPriority;

  public PrattTokenType(@NotNull final String tokenName,
                          @Nullable final Language language, final int priority, @Nullable final Nud nud, @Nullable final Led led) {
    super(tokenName, language);
    myPriority = priority;
    myNud = nud;
    myLed = led;
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

  @Nullable public Led getLed() {
    return myLed;
  }

  @Nullable
  public Nud getNud() {
    return myNud;
  }

}
