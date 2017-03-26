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

import org.jetbrains.annotations.NotNull;

/**
 * Represents a simple, escaped, encoded or named character
 */
public interface RegExpChar extends RegExpAtom, RegExpClassElement {
    /** Character type */
    enum Type {
        /** a plain character ("a") */
        CHAR,

        /** a hex encoded character value ("\x61") */
        HEX,

        /** an octal encoded character value ("\0141") */
        OCT,

        /** a unicode escape character ("\u0061") */
        UNICODE,

        /** a named character (\N{LATIN SMALL LETTER A}) */
        NAMED,
    }

    /**
     * Returns the type of this character.
     * @see Type
     */
    @NotNull
    Type getType();

    /** Returns unescaped character code point value, -1 if escape sequence is invalid. */
    int getValue();
}
