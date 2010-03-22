/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiElementFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.psi.RegExpElement;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.intellij.lang.regexp.psi.RegExpGroup;
import org.intellij.lang.regexp.psi.RegExpPyNamedGroupRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class RegExpPyNamedGroupRefImpl extends RegExpElementImpl implements RegExpPyNamedGroupRef {
  public RegExpPyNamedGroupRefImpl(ASTNode node) {
    super(node);
  }

  @Override
  public void accept(RegExpElementVisitor visitor) {
    visitor.visitRegExpPyNamedGroupRef(this);
  }

  @Nullable
  public RegExpGroup resolve() {
    final PsiElementProcessor.FindFilteredElement<RegExpElement> processor = new PsiElementProcessor.FindFilteredElement<RegExpElement>(new PsiElementFilter() {
        public boolean isAccepted(PsiElement element) {
            if (element instanceof RegExpGroup) {
                if (((RegExpGroup)element).isPythonNamedGroup() && Comparing.equal(getGroupName(), ((RegExpGroup)element).getGroupName())) {
                    return true;
                }
            }
            return element == RegExpPyNamedGroupRefImpl.this;
        }
    });

    PsiTreeUtil.processElements(getContainingFile(), processor);
    if (processor.getFoundElement() instanceof RegExpGroup) {
        return (RegExpGroup) processor.getFoundElement();
    }
    return null;
  }

  @Nullable
  public String getGroupName() {
    final ASTNode nameNode = getNode().findChildByType(RegExpTT.NAME);
    return nameNode != null ? nameNode.getText() : null;
  }

  @Override
  public PsiReference getReference() {
    return new PsiReference() {
      public PsiElement getElement() {
        return RegExpPyNamedGroupRefImpl.this;
      }

      public TextRange getRangeInElement() {
        return new TextRange(4, getTextLength()-1);
      }

      public PsiElement resolve() {
        return RegExpPyNamedGroupRefImpl.this.resolve();
      }

      public String getCanonicalText() {
        return getRangeInElement().substring(getText());
      }

      public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
        throw new UnsupportedOperationException();
      }

      public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        throw new UnsupportedOperationException();
      }

      public boolean isReferenceTo(PsiElement element) {
        return resolve() == element;
      }

      @NotNull
      public Object[] getVariants() {
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
      }

      public boolean isSoft() {
        return false;
      }
    };
  }
}
