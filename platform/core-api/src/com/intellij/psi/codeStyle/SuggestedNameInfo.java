// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.Key;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

/**
 * Represents an array of suggested variable names and allows to keep statistics on
 * which of the suggestions has been accepted.
 *
 * (see JavaCodeStyleManager.suggestVariableName() methods).
 */
public abstract class SuggestedNameInfo {
  @SuppressWarnings("UnusedDeclaration")
  public static final Key<SuggestedNameInfo> SUGGESTED_NAME_INFO_KEY = Key.create("SUGGESTED_NAME_INFO_KEY");

  public static final SuggestedNameInfo NULL_INFO = new SuggestedNameInfo(ArrayUtilRt.EMPTY_STRING_ARRAY) {
  };

  /**
   * The suggested names.
   */
  @NotNull
  public final String[] names;

  public SuggestedNameInfo(@NotNull String[] names) {
    this.names = names;
  }

  /**
   * <p>Should be called when one of the suggested names has been chosen by the user, to
   * update the statistics on name usage.</p>
   * <p><b>Note to implementers:</b> do not leave this method non-overridden as it going to be abstract.</p>
   *
   * @param name the accepted suggestion.
   */
  public void nameChosen(String name) { }

  public static class Delegate extends SuggestedNameInfo {
    SuggestedNameInfo myDelegate;

    public Delegate(@NotNull String[] names, @NotNull SuggestedNameInfo delegate) {
      super(names);
      myDelegate = delegate;
    }

    @Override
    public void nameChosen(final String name) {
      myDelegate.nameChosen(name);
    }
  }
}
