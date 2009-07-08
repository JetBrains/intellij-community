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
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.tree.TokenSet;
import org.intellij.lang.regexp.RegExpElementTypes;
import org.intellij.lang.regexp.psi.RegExpBranch;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.intellij.lang.regexp.psi.RegExpPattern;
import org.jetbrains.annotations.NotNull;

public class RegExpPatternImpl extends RegExpElementImpl implements RegExpPattern {
    private static final TokenSet BRANCH = TokenSet.create(RegExpElementTypes.BRANCH);

    public RegExpPatternImpl(ASTNode astNode) {
        super(astNode);
    }

    public void accept(RegExpElementVisitor visitor) {
        visitor.visitRegExpPattern(this);
    }

    @NotNull
    public RegExpBranch[] getBranches() {
        final ASTNode[] nodes = getNode().getChildren(BRANCH);
        final RegExpBranch[] branches = new RegExpBranch[nodes.length];
        for (int i = 0; i < branches.length; i++) {
            branches[i] = (RegExpBranch)nodes[i].getPsi();
        }
        return branches;
    }

  @NotNull
  @Override
  public PsiReference[] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this, RegExpPattern.class);
  }
}
