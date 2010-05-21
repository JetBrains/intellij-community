/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.javadoc;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
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
    super(Constants.DOC_TAG_VALUE_TOKEN);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitDocTagValue(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public PsiReference getReference() {
    PsiDocTag docTag = PsiTreeUtil.getParentOfType(this, PsiDocTag.class);
    if (docTag == null) {
      return null;
    }
    final String name = docTag.getName();
    final JavadocManager manager = JavaPsiFacade.getInstance(getProject()).getJavadocManager();
    final JavadocTagInfo info = manager.getTagInfo(name);
    if (info == null) return null;

    return info.getReference(this);
  }

  @Override
  public int getChildRole(ASTNode child) {
    if (child.getElementType() == JavaDocTokenType.DOC_TAG_VALUE_COMMA) {
      return ChildRole.COMMA;
    }
    return super.getChildRole(child);
  }
}
