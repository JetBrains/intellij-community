/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.psi.formatter;

import com.intellij.formatting.WhiteSpaceFormattingStrategy;
import org.jetbrains.annotations.NotNull;

/**
 * Abstract common {@link WhiteSpaceFormattingStrategy} implementation that doesn't replace default strategy and doesn't
 * adjust white space and
 *
 * @author Denis Zhdanov
 * @since Sep 22, 2010 10:19:07 AM
 */
public abstract class AbstractWhiteSpaceFormattingStrategy implements WhiteSpaceFormattingStrategy {

  @Override
  public boolean replaceDefaultStrategy() {
    return false;
  }

  @NotNull
  @Override
  public CharSequence adjustWhiteSpaceIfNecessary(@NotNull CharSequence whiteSpaceText,
                                                  @NotNull CharSequence text,
                                                  int startOffset,
                                                  int endOffset)
  {
    // Does nothing
    return whiteSpaceText;
  }
}
