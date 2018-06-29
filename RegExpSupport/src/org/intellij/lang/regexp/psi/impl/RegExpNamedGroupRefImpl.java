/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.intellij.lang.regexp.psi.RegExpGroup;
import org.intellij.lang.regexp.psi.RegExpNamedGroupRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class RegExpNamedGroupRefImpl extends RegExpElementImpl implements RegExpNamedGroupRef {
  private static final TokenSet RUBY_GROUP_REF_TOKENS =
    TokenSet.create(RegExpTT.RUBY_NAMED_GROUP_REF, RegExpTT.RUBY_QUOTED_NAMED_GROUP_REF,
                    RegExpTT.RUBY_NAMED_GROUP_CALL, RegExpTT.RUBY_QUOTED_NAMED_GROUP_CALL);

  public RegExpNamedGroupRefImpl(ASTNode node) {
    super(node);
  }

  @Override
  public void accept(RegExpElementVisitor visitor) {
    visitor.visitRegExpNamedGroupRef(this);
  }

  @Override
  @Nullable
  public RegExpGroup resolve() {
    final PsiElementProcessor.FindFilteredElement<RegExpGroup> processor = new PsiElementProcessor.FindFilteredElement<>(
      element -> {
        if (!(element instanceof RegExpGroup)) {
          return false;
        }
        final RegExpGroup group = (RegExpGroup)element;
        return group.isAnyNamedGroup() && Comparing.equal(getGroupName(), group.getGroupName());
      }
    );
    PsiTreeUtil.processElements(getContainingFile(), processor);
    return processor.getFoundElement();
  }

  @Override
  @Nullable
  public String getGroupName() {
    final ASTNode nameNode = getNode().findChildByType(RegExpTT.NAME);
    return nameNode != null ? nameNode.getText() : null;
  }

  @Override
  public boolean isPythonNamedGroupRef() {
    return getNode().findChildByType(RegExpTT.PYTHON_NAMED_GROUP_REF) != null;
  }

  @Override
  public boolean isRubyNamedGroupRef() {
    final ASTNode node = getNode();
    return node.findChildByType(RUBY_GROUP_REF_TOKENS) != null;
  }

  @Override
  public boolean isNamedGroupRef() {
    return getNode().findChildByType(RegExpTT.RUBY_NAMED_GROUP_REF) != null;
  }

  @Override
  public PsiReference getReference() {
    if (getNode().findChildByType(RegExpTT.NAME) == null) {
      return null;
    }
    return new PsiReference() {
      @NotNull
      @Override
      public PsiElement getElement() {
        return RegExpNamedGroupRefImpl.this;
      }

      @NotNull
      @Override
      public TextRange getRangeInElement() {
        final ASTNode groupNode = getNode().getFirstChildNode();
        assert groupNode != null;
        final ASTNode nameNode = getNode().findChildByType(RegExpTT.NAME);
        assert nameNode != null;
        final int startOffset = groupNode.getTextLength();
        return new TextRange(startOffset, startOffset + nameNode.getTextLength());
      }

      @Override
      public PsiElement resolve() {
        return RegExpNamedGroupRefImpl.this.resolve();
      }

      @Override
      @NotNull
      public String getCanonicalText() {
        return getRangeInElement().substring(getText());
      }

      @Override
      public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
        throw new UnsupportedOperationException();
      }

      @Override
      public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean isReferenceTo(PsiElement element) {
        return resolve() == element;
      }

      @Override
      @NotNull
      public Object[] getVariants() {
        final PsiElementProcessor.CollectFilteredElements<RegExpGroup> processor = new PsiElementProcessor.CollectFilteredElements<>(
          e -> e instanceof RegExpGroup && ((RegExpGroup)e).isAnyNamedGroup()
        );
        PsiTreeUtil.processElements(getContainingFile(), processor);
        return processor.toArray();
      }

      @Override
      public boolean isSoft() {
        return false;
      }
    };
  }
}
