// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.javadoc.JavadocManager;
import com.intellij.psi.javadoc.JavadocTagInfo;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author mike
 */
public class PsiDocTagValueImpl extends CompositePsiElement implements PsiDocTagValue {
  public PsiDocTagValueImpl() {
    super(JavaDocElementType.DOC_TAG_VALUE_ELEMENT);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitDocTagValue(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    if (child.getElementType() == JavaDocTokenType.DOC_TAG_VALUE_COMMA ||
        child.getElementType() == JavaDocTokenType.DOC_TAG_VALUE_TOKEN && child.getTextLength() == 1 && child.getChars().charAt(0) == ',') {
      return ChildRole.COMMA;
    }

    return super.getChildRole(child);
  }

  @Override
  public PsiReference getReference() {
    PsiDocTag docTag = PsiTreeUtil.getParentOfType(this, PsiDocTag.class);
    if (docTag == null) return null;

    JavadocTagInfo info = JavadocManager.SERVICE.getInstance(getProject()).getTagInfo(docTag.getName());
    if (info == null) return null;

    return info.getReference(this);
  }
}