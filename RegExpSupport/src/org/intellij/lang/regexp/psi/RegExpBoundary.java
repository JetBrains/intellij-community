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

public interface RegExpBoundary extends RegExpAtom {
    /**
     * Boundary type enumeration.
     */
    enum Type  {
        LINE_START, LINE_END,
        WORD, UNICODE_EXTENDED_GRAPHEME, NON_WORD,
        BEGIN, END, END_NO_LINE_TERM,
        PREVIOUS_MATCH
    }

    @NotNull
    Type getType();
}
