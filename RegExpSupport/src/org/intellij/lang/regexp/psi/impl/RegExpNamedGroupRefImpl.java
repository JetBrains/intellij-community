// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;
import java.util.List;
import java.util.Objects;
import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.intellij.lang.regexp.psi.RegExpGroup;
import org.intellij.lang.regexp.psi.RegExpNamedGroupRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


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
    if (isPcreNumberedGroupRef()) {
      Integer groupNumber;
      ASTNode node = getNode().findChildByType(RegExpTT.NUMBER);
      if (node == null) return null;
      try {
        groupNumber = Integer.parseInt(node.getText());
      }
      catch (NumberFormatException e) {
        groupNumber = null;
      }
      return groupNumber == null ? null : resolveNumberedGroupRef(groupNumber, getContainingFile());
    }
    final String groupName = getGroupName();
    return groupName == null ? null : resolve(groupName, getContainingFile());
  }

  static RegExpGroup resolve(@NotNull String groupName, PsiFile file) {
    return SyntaxTraverser.psiTraverser(file)
      .filter(RegExpGroup.class)
      .filter(group -> Objects.equals(groupName, group.getGroupName()))
      .first();
  }

  @Nullable
  static RegExpGroup resolveNumberedGroupRef(int groupNumber, PsiFile file) {
    if (groupNumber < 1) return null;
    List<RegExpGroup> groups = SyntaxTraverser.psiTraverser(file)
      .filter(RegExpGroup.class)
      .filter(RegExpGroup::isCapturing)
      .toList();
    if (groups.size() < groupNumber) {
      return null;
    }
    return groups.get(groupNumber - 1);
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
  public boolean isPcreNumberedGroupRef() {
    return getNode().findChildByType(RegExpTT.PCRE_NUMBERED_GROUP_REF) != null;
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
        return SyntaxTraverser.psiTraverser(getContainingFile()).filter(RegExpGroup.class)
          .filter(RegExpGroup::isAnyNamedGroup).toArray(new RegExpGroup[0]);
      }

      @Override
      public boolean isSoft() {
        return false;
      }
    };
  }
}
