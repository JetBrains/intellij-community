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

/*
 * @author max
 */
package com.intellij.lexer;

public interface RestorableLexer {
  /**
   * Returns the current position and state of the lexer.
   *
   * @return the lexer position and state.
   */
  LexerPosition getCurrentPosition();

  /**
   * Restores the lexer to the specified state and position.
   *
   * @param position the state and position to restore to.
   */
  void restore(LexerPosition position);
}
