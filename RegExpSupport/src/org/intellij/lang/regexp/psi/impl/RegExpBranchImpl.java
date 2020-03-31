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
import org.intellij.lang.regexp.RegExpElementTypes;
import org.intellij.lang.regexp.psi.RegExpAtom;
import org.intellij.lang.regexp.psi.RegExpBranch;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.jetbrains.annotations.NotNull;

public class RegExpBranchImpl extends RegExpElementImpl implements RegExpBranch {
    public RegExpBranchImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    public RegExpAtom @NotNull [] getAtoms() {
        final ASTNode[] nodes = getNode().getChildren(RegExpElementTypes.ATOMS);
        final RegExpAtom[] atoms = new RegExpAtom[nodes.length];
        for (int i = 0; i < atoms.length; i++) {
            atoms[i] = (RegExpAtom)nodes[i].getPsi();
        }
        return atoms;
    }

    @Override
    public void accept(RegExpElementVisitor visitor) {
        visitor.visitRegExpBranch(this);
    }
}
