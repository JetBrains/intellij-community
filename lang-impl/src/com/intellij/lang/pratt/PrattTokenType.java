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


  public PrattTokenType(@NotNull @NonNls final String debugName,
                        @Nullable final Language language) {
    super(debugName, language);
  }

  public String getExpectedText(final PrattBuilder builder) {
    return PsiBundle.message("0.expected", toString());
  }

}
