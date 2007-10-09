/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.psi;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
*/
public abstract class LiteralTextEscaper<T extends PsiLanguageInjectionHost> {
  protected final T myHost;

  protected LiteralTextEscaper(@NotNull T host) {
    myHost = host;
  }

  public abstract boolean decode(@NotNull TextRange rangeInsideHost, @NotNull StringBuilder outChars);

  /**
   * @param offsetInDecoded offset in the parsed injected file
   * @param rangeInsideHost
   * @return offset in the host PSI element, or -1 if offset is out of host range
   */
  public abstract int getOffsetInHost(int offsetInDecoded, @NotNull TextRange rangeInsideHost);

  @NotNull
  public TextRange getRelevantTextRange() {
    return TextRange.from(0, myHost.getTextLength());
  }

  public abstract boolean isOneLine();

}
