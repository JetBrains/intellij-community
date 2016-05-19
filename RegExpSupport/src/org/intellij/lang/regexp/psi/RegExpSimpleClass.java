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
 * Represents a simple character class.
 */
public interface RegExpSimpleClass extends RegExpAtom, RegExpClassElement, RegExpCharRange.Endpoint {
    enum Kind {
        /** . */  ANY,
        /** \d */ DIGIT, /** \D */ NON_DIGIT,
        /** \w */ WORD,  /** \W */ NON_WORD,
        /** \s */ SPACE, /** \S */ NON_SPACE,
        /** \h */ HORIZONTAL_SPACE, /** \H */ NON_HORIZONTAL_SPACE,
        /** \v */ VERTICAL_SPACE,   /** \V */ NON_VERTICAL_SPACE,
        /** \i */ XML_NAME_START,   /** \I */ NON_XML_NAME_START,
        /** \c */ XML_NAME_PART,    /** \C */ NON_XML_NAME_PART,
        /** \X */ UNICODE_GRAPHEME,
        /** \R */ UNICODE_LINEBREAK
    }

    @NotNull
    Kind getKind();
}
