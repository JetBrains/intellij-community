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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiElementFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.regexp.psi.RegExpBackref;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.intellij.lang.regexp.psi.RegExpGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.trimEnd;
import static com.intellij.openapi.util.text.StringUtil.trimStart;

public class RegExpBackrefImpl extends RegExpElementImpl implements RegExpBackref {
    public RegExpBackrefImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    public int getIndex() {
        return Integer.parseInt(getIndexNumberText());
    }

    @NotNull
    private String getIndexNumberText() {
        final String s = getUnescapedText();
        assert s.charAt(0) == '\\';
        boolean pcreBackReference = s.charAt(1) == 'g';
        return pcreBackReference ? getPcreBackrefIndexNumberText(s.substring(2)) : s.substring(1);
    }

    @NotNull
    private static String getPcreBackrefIndexNumberText(String s) {
        return trimEnd(trimStart(s, "{"), "}");
    }

    @Override
    public void accept(RegExpElementVisitor visitor) {
        visitor.visitRegExpBackref(this);
    }

    @Override
    public RegExpGroup resolve() {
        final int index = getIndex();
        if (index < 0) {
            return resolveRelativeGroup(Math.abs(index));
        }

        return SyntaxTraverser.psiTraverser(getContainingFile()).traverse().takeWhile(e -> e != RegExpBackrefImpl.this)
          .filter(RegExpGroup.class).filter(RegExpGroup::isCapturing).skip(index - 1).first();
    }

    @Nullable
    private RegExpGroup resolveRelativeGroup(int index) {
        List<RegExpGroup> groups = SyntaxTraverser.psiTraverser(getContainingFile()).filter(RegExpGroup.class)
          .filter(RegExpGroup::isCapturing).toList();
        return index <= groups.size() ? groups.get(groups.size() - index) : null;
    }

    @Override
    public PsiReference getReference() {
        return new PsiReference() {
            @Override
            @NotNull
            public PsiElement getElement() {
                return RegExpBackrefImpl.this;
            }

            @Override
            @NotNull
            public TextRange getRangeInElement() {
                return TextRange.from(0, getElement().getTextLength());
            }

            @Override
            @NotNull
            public String getCanonicalText() {
                return getElement().getText();
            }

            @Override
            public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
                throw new IncorrectOperationException();
            }

            @Override
            public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
                throw new IncorrectOperationException();
            }

            @Override
            public boolean isReferenceTo(@NotNull PsiElement element) {
                return Comparing.equal(element, resolve());
            }

            @Override
            public boolean isSoft() {
                return false;
            }

            @Override
            public PsiElement resolve() {
                return RegExpBackrefImpl.this.resolve();
            }
        };
    }
}
