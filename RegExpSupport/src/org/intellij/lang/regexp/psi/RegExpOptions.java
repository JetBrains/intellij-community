/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.regexp.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.Nullable;

/**
 * Represents an inline options element (?x) or (?-x). Returned from {@link org.intellij.lang.regexp.psi.RegExpSetOptions}
 */
public interface RegExpOptions extends RegExpElement {
    /**
     * @param flag  the flag to check, e.g. 'i'
     * @return true, when the specified flag is switched on, false otherwise.
     */
    boolean isSwitchedOn(char flag);

  /**
   * It's possible for a flag to be both switched on and switched off.
   * @param flag  the flag to check, e.g. 'd'
   * @return true, when the specified flag is switched off, false otherwise.
   */
    boolean isSwitchedOff(char flag);

    @Nullable
    ASTNode getOptionsOn();

    @Nullable
    ASTNode getOptionsOff();
}
