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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiElementFilter;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ArrayUtil;

import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.intellij.lang.regexp.psi.RegExpElement;
import org.intellij.lang.regexp.psi.RegExpGroup;
import org.intellij.lang.regexp.psi.RegExpBackref;
import org.jetbrains.annotations.NotNull;

public class RegExpBackrefImpl extends RegExpElementImpl implements RegExpBackref {
    public RegExpBackrefImpl(ASTNode astNode) {
        super(astNode);
    }

    public int getIndex() {
        final String s = getUnescapedText();
        assert s.charAt(0) == '\\';
        return Integer.parseInt(s.substring(1));
    }

    public void accept(RegExpElementVisitor visitor) {
        visitor.visitRegExpBackref(this);
    }

    public RegExpGroup resolve() {
        final int index = getIndex();

        final PsiElementProcessor.FindFilteredElement<RegExpElement> processor =
          new PsiElementProcessor.FindFilteredElement<>(new PsiElementFilter() {
            int groupCount;

            public boolean isAccepted(PsiElement element) {
              if (element instanceof RegExpGroup) {
                if (((RegExpGroup)element).isCapturing() && ++groupCount == index) {
                  return true;
                }
              }
              return element == RegExpBackrefImpl.this;
            }
          });

        PsiTreeUtil.processElements(getContainingFile(), processor);
        if (processor.getFoundElement() instanceof RegExpGroup) {
            return (RegExpGroup)processor.getFoundElement();
        }
        return null;
    }

    public PsiReference getReference() {
        return new PsiReference() {
            public PsiElement getElement() {
                return RegExpBackrefImpl.this;
            }

            public TextRange getRangeInElement() {
                return TextRange.from(0, getElement().getTextLength());
            }

            @NotNull
            public String getCanonicalText() {
                return getElement().getText();
            }

            public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
                throw new IncorrectOperationException();
            }

            public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
                throw new IncorrectOperationException();
            }

            public boolean isReferenceTo(PsiElement element) {
                return Comparing.equal(element, resolve());
            }

            public boolean isSoft() {
                return false;
            }

            public PsiElement resolve() {
                return RegExpBackrefImpl.this.resolve();
            }
            
            @NotNull
            public Object[] getVariants() {
                return ArrayUtil.EMPTY_OBJECT_ARRAY;
            }
        };
    }
}
