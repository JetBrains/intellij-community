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
package org.intellij.lang.regexp.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.intellij.lang.regexp.psi.RegExpSimpleClass;

public class RegExpSimpleClassImpl extends RegExpElementImpl implements RegExpSimpleClass {
    public RegExpSimpleClassImpl(ASTNode node) {
        super(node);
    }

    @Override
    @NotNull
    public Kind getKind() {
      return switch (getUnescapedText()) {
        case "." -> Kind.ANY;
        case "\\d" -> Kind.DIGIT;
        case "\\D" -> Kind.NON_DIGIT;
        case "\\w" -> Kind.WORD;
        case "\\W" -> Kind.NON_WORD;
        case "\\s" -> Kind.SPACE;
        case "\\S" -> Kind.NON_SPACE;
        case "\\h" -> Kind.HORIZONTAL_SPACE;
        case "\\H" -> Kind.NON_HORIZONTAL_SPACE;
        case "\\v" -> Kind.VERTICAL_SPACE;
        case "\\V" -> Kind.NON_VERTICAL_SPACE;
        case "\\i" -> Kind.XML_NAME_START;
        case "\\I" -> Kind.NON_XML_NAME_START;
        case "\\c" -> Kind.XML_NAME_PART;
        case "\\C" -> Kind.NON_XML_NAME_PART;
        case "\\X" -> Kind.UNICODE_GRAPHEME;
        case "\\R" -> Kind.UNICODE_LINEBREAK;
        default -> throw new AssertionError("unknown character class '" + getUnescapedText() + "'");
      };
    }

    @Override
    public void accept(RegExpElementVisitor visitor) {
        visitor.visitSimpleClass(this);
    }
}
