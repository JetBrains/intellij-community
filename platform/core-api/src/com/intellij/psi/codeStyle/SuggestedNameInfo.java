/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.Key;
import com.intellij.util.ArrayUtil;

/**
 * Represents an array of suggested variable names and allows to keep statistics on
 * which of the suggestions has been accepted.
 *
 * (see JavaCodeStyleManager.suggestVariableName() methods).
 */
public abstract class SuggestedNameInfo {
  @SuppressWarnings("UnusedDeclaration")
  public static final Key<SuggestedNameInfo> SUGGESTED_NAME_INFO_KEY = Key.create("SUGGESTED_NAME_INFO_KEY");

  public static final SuggestedNameInfo NULL_INFO = new SuggestedNameInfo(ArrayUtil.EMPTY_STRING_ARRAY) {
    @Override
    public void nameChosen(String name) {}
  };

  /**
   * The suggested names.
   */
  public final String[] names;

  public SuggestedNameInfo(String[] names) {
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

    public Delegate(final String[] names, final SuggestedNameInfo delegate) {
      super(names);
      myDelegate = delegate;
    }

    @Override
    public void nameChosen(final String name) {
      myDelegate.nameChosen(name);
    }
  }
}
