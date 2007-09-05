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

  public T getHost() {
    return myHost;
  }
}
