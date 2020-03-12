// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    final PsiElementProcessor.FindFilteredElement<PsiElement> processor = new PsiElementProcessor.FindFilteredElement<>(
      element -> {
        if (!(element instanceof RegExpGroup)) {
          return false;
        }
        final RegExpGroup group = (RegExpGroup)element;
        return group.isAnyNamedGroup() && Comparing.equal(getGroupName(), group.getGroupName());
      }
    );
    PsiTreeUtil.processElements(getContainingFile(), processor);
    return (RegExpGroup)processor.getFoundElement();
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
        final ASTNode nameNode = getNode().findChildByType(RegExpTT.NAME);
        assert nameNode != null;
        final int startOffset = getNode().getFirstChildNode().getTextLength();
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
      public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        throw new UnsupportedOperationException();
      }

      @Override
      public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean isReferenceTo(@NotNull PsiElement element) {
        return resolve() == element;
      }

      @Override
      public Object @NotNull [] getVariants() {
        final PsiElementProcessor.CollectFilteredElements<PsiElement> processor = new PsiElementProcessor.CollectFilteredElements<>(
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
