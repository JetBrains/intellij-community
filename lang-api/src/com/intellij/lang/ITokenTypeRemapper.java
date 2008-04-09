package com.intellij.lang;

import com.intellij.psi.tree.IElementType;

/**
 * Created by IntelliJ IDEA.
 * User: dmitry.cheryasov
 * Date: 06.04.2008
 * Time: 23:41:56
 */
public interface ITokenTypeRemapper {
  /**
   * An external hook to see and alter token types reported by lexer.
   * A lexer might take a delegate implementing this interface.
   * @param source type of an element as lexer understood it.
   * @param start start index of lexem in text (as lexer.getTokenStart() would return).
   * @param end end index of lexem in text (as lexer.getTokenEnd() would return).
   * @param text text being parsed.
   * @return altered (or not) element type.
  **/
  IElementType filter(final IElementType source, final int start, final int end, final CharSequence text);
}
