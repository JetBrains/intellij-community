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
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.RegExpElementTypes;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.intellij.lang.regexp.psi.RegExpQuantifier;
import org.intellij.lang.regexp.psi.RegExpAtom;

public class RegExpQuantifierImpl extends RegExpElementImpl implements RegExpQuantifier {

    public RegExpQuantifierImpl(ASTNode astNode) {
        super(astNode);
    }

    public void accept(RegExpElementVisitor visitor) {
        visitor.visitRegExpQuantifier(this);
    }

    @NotNull
    public RegExpAtom getAtom() {
        final ASTNode[] nodes = getNode().getChildren(RegExpElementTypes.ATOMS);
        assert nodes.length > 0;
        return (RegExpAtom)nodes[0].getPsi();
    }

    @NotNull
    public Count getCount() {
        final ASTNode[] nodes = getNode().getChildren(RegExpTT.QUANTIFIERS);
        assert nodes.length > 0;

        final IElementType type = nodes[0].getElementType();
        if (type == RegExpTT.QUEST) {
            return SimpleCount.ONE_OR_ZERO;
        } else if (type == RegExpTT.STAR) {
            return SimpleCount.ZERO_OR_MORE;
        } else if (type == RegExpTT.PLUS) {
            return SimpleCount.ONE_OR_MORE;
        } else if (type == RegExpTT.LBRACE) {
            final ASTNode[] numbers = getNode().getChildren(TokenSet.create(RegExpTT.NUMBER));
            if (numbers.length >= 1) {
                final String min = numbers[0].getText();
                final String max;
                if (numbers.length == 2) {
                    max = numbers[1].getText();
                } else if (getNode().findChildByType(RegExpTT.COMMA) != null) {
                    max = "";
                } else {
                    max = min;
                }
                return new RepeatedCount(min, max);
            }
            // syntactically incorrect
            return new RepeatedCount("", "");
        }

        assert false;
        return null;
    }

    @NotNull
    public Type getType() {
        final ASTNode[] nodes = getNode().getChildren(RegExpTT.QUANTIFIERS);
        if (nodes.length > 1) {
            final IElementType type = nodes[1].getElementType();
            if (type == RegExpTT.QUEST) {
                return Type.RELUCTANT;
            } else if (type == RegExpTT.PLUS) {
                return Type.POSSESSIVE;
            }
        }
        return Type.GREEDY;
    }

    private static class RepeatedCount implements RegExpQuantifier.Count {
        private final String myMin;
        private final String myMax;

        public RepeatedCount(@NotNull String min, @NotNull String max) {
            myMin = min;
            myMax = max;
        }

        @NotNull
        public String getMin() {
            return myMin;
        }

        @NotNull
        public String getMax() {
            return myMax;
        }
    }
}
