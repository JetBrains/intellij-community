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
import com.intellij.psi.tree.IElementType;
import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.psi.RegExpBoundary;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.jetbrains.annotations.NotNull;

public class RegExpBoundaryImpl extends RegExpElementImpl implements RegExpBoundary {
    public RegExpBoundaryImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    @NotNull
    public Type getType() {
        final IElementType type = getNode().getFirstChildNode().getElementType();
        if (type == RegExpTT.CARET) {
            return Type.LINE_START;
        } else if (type == RegExpTT.DOLLAR) {
            return Type.LINE_END;
        } else if (type == RegExpTT.BOUNDARY){
            final String s = getUnescapedText();
            if (s.equals("\\b")) {
                return Type.WORD;
            } else if (s.equals("\\b{g}")) {
                return Type.UNICODE_EXTENDED_GRAPHEME;
            } else if (s.equals("\\B")) {
                return Type.NON_WORD;
            } else if (s.equals("\\A")) {
                return Type.BEGIN;
            } else if (s.equals("\\Z")) {
                return Type.END_NO_LINE_TERM;
            } else if (s.equals("\\z")) {
                return Type.END;
            } else if (s.equals("\\G")) {
                return Type.PREVIOUS_MATCH;
            } else if (s.equals("\\K")) {
                return Type.RESET_MATCH;
            }
        }
        assert false;
        return null;
    }

    @Override
    public void accept(RegExpElementVisitor visitor) {
        visitor.visitRegExpBoundary(this);
    }
}
