/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
   * @param start start index of lexeme in text (as lexer.getTokenStart() would return).
   * @param end end index of lexeme in text (as lexer.getTokenEnd() would return).
   * @param text text being parsed.
   * @return altered (or not) element type.
  **/
  IElementType filter(final IElementType source, final int start, final int end, final CharSequence text);
}
