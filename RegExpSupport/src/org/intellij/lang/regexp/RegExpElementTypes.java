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
package org.intellij.lang.regexp;

import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

public interface RegExpElementTypes {
    IFileElementType REGEXP_FILE = new IFileElementType("REGEXP_FILE", RegExpLanguage.INSTANCE);
    IElementType PATTERN = new RegExpElementType("PATTERN");
    IElementType BRANCH = new RegExpElementType("BRANCH");
    IElementType CLOSURE = new RegExpElementType("COUNTED_CLOSURE");
    IElementType QUANTIFIER = new RegExpElementType("QUANTIFIER");
    IElementType SIMPLE_CLASS = new RegExpElementType("SIMPLE_CLASS");
    IElementType CLASS = new RegExpElementType("CLASS");
    IElementType CHAR_RANGE = new RegExpElementType("CHAR_RANGE");
    IElementType INTERSECTION = new RegExpElementType("INTERSECTION");
    IElementType CHAR = new RegExpElementType("CHAR");
    IElementType GROUP = new RegExpElementType("GROUP");
    IElementType PROPERTY = new RegExpElementType("PROPERTY");
    IElementType OPTIONS = new RegExpElementType("OPTIONS");
    IElementType SET_OPTIONS = new RegExpElementType("SET_OPTIONS");
    IElementType BACKREF = new RegExpElementType("BACKREF");
    IElementType BOUNDARY = new RegExpElementType("BOUNDARY");
    IElementType PY_NAMED_GROUP_REF = new RegExpElementType("PY_NAMED_GROUP_REF");

    TokenSet ATOMS = TokenSet.create(CLOSURE, BOUNDARY,
            SIMPLE_CLASS, CLASS, CHAR, GROUP, PROPERTY, SET_OPTIONS, BACKREF, PY_NAMED_GROUP_REF);

    TokenSet CLASS_ELEMENTS = TokenSet.create(CHAR, CHAR_RANGE,
            SIMPLE_CLASS, CLASS, INTERSECTION, PROPERTY);
}
