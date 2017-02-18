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
import org.intellij.lang.regexp.RegExpElementTypes;
import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.intellij.lang.regexp.psi.RegExpNumber;
import org.intellij.lang.regexp.psi.RegExpQuantifier;
import org.jetbrains.annotations.Nullable;

public class RegExpQuantifierImpl extends RegExpElementImpl implements RegExpQuantifier {

    private static final TokenSet TOKENS = TokenSet.create(RegExpElementTypes.NUMBER, RegExpTT.COMMA);

    public RegExpQuantifierImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    public void accept(RegExpElementVisitor visitor) {
        visitor.visitRegExpQuantifier(this);
    }

    @Override
    public boolean isCounted() {
        return getNode().getFirstChildNode().getElementType() == RegExpTT.LBRACE;
    }

    @Override
    @Nullable
    public ASTNode getToken() {
        final ASTNode node = getNode().getFirstChildNode();
        final IElementType type = node.getElementType();
        if (type == RegExpTT.LBRACE) {
            return null;
        }
        return node;
    }

    @Nullable
    @Override
    public RegExpNumber getMin() {
        final ASTNode[] nodes = getNode().getChildren(TOKENS);
        if (nodes.length == 0 || nodes[0].getElementType() != RegExpElementTypes.NUMBER) {
            return null;
        }
        return (RegExpNumber)nodes[0].getPsi();
    }

    @Nullable
    @Override
    public RegExpNumber getMax() {
        final ASTNode[] nodes = getNode().getChildren(TOKENS);
        if (nodes.length == 0) {
            return null;
        }
        final ASTNode node = nodes[nodes.length - 1];
        if (node.getElementType() != RegExpElementTypes.NUMBER) {
            return null;
        }
        return (RegExpNumber)node.getPsi();
    }

    @Nullable
    @Override
    public ASTNode getModifier() {
        final ASTNode[] nodes = getNode().getChildren(RegExpTT.QUANTIFIERS);
        if (nodes.length > 1) {
            final ASTNode node = nodes[1];
            final IElementType type = node.getElementType();
            if (type == RegExpTT.QUEST || type == RegExpTT.PLUS) {
                return node;
            }
        }
        return null;
    }

    @Override
    public boolean isReluctant() {
        final ASTNode modifier = getModifier();
        return modifier != null && modifier.getElementType() == RegExpTT.QUEST;
    }

    @Override
    public boolean isPossessive() {
        final ASTNode modifier = getModifier();
        return modifier != null && modifier.getElementType() == RegExpTT.PLUS;
    }
}
