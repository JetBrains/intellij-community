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
import org.jetbrains.annotations.Nullable;

import org.intellij.lang.regexp.psi.RegExpClassElement;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.intellij.lang.regexp.psi.RegExpIntersection;
import org.intellij.lang.regexp.RegExpElementTypes;

public class RegExpIntersectionImpl extends RegExpElementImpl implements RegExpIntersection {
    public RegExpIntersectionImpl(ASTNode astNode) {
        super(astNode);
    }

    public void accept(RegExpElementVisitor visitor) {
        visitor.visitRegExpIntersection(this);
    }

    @NotNull
    public RegExpClassElement getLOperand() {
        final ASTNode[] nodes = getNode().getChildren(RegExpElementTypes.CLASS_ELEMENTS);
        return (RegExpClassElement)nodes[0].getPsi();
    }

    @Nullable
    public RegExpClassElement getROperand() {
        final ASTNode[] nodes = getNode().getChildren(RegExpElementTypes.CLASS_ELEMENTS);
        return nodes.length > 1 ? (RegExpClassElement)nodes[1].getPsi() : null;
    }
}
