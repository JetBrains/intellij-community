/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author mike
 */
public class PsiDocParamRef extends CompositePsiElement implements PsiDocTagValue {
  public PsiDocParamRef() {
    super(JavaDocElementType.DOC_PARAMETER_REF);
  }

  @Override
  public PsiReference getReference() {
    final PsiDocComment comment = PsiTreeUtil.getParentOfType(this, PsiDocComment.class);
    if (comment == null) return null;
    final PsiJavaDocumentedElement owner = comment.getOwner();
    if (!(owner instanceof PsiMethod) &&
        !(owner instanceof PsiClass)) return null;
    ASTNode valueToken = getValueToken();
    if (valueToken == null) return null;
    final String name = valueToken.getText();
    boolean isTypeParamRef = isTypeParamRef();
    PsiElement target = ContainerUtil.find(getAllParameters(comment),
                                           param -> isTypeParamRef == param instanceof PsiTypeParameter && name.equals(param.getName()));

    TextRange range = TextRange.from(valueToken.getPsi().getStartOffsetInParent(), valueToken.getTextLength());
    return new PsiReferenceBase<PsiElement>(this, range) {
      @Override
      public PsiElement resolve() {
        return target;
      }

      @Override
      public PsiElement handleElementRename(@NotNull String newElementName) {
        final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(getNode());
        LeafElement newElement = Factory.createSingleLeafElement(JavaDocTokenType.DOC_TAG_VALUE_TOKEN, newElementName, charTableByTree, getManager());
        replaceChild(valueToken, newElement);
        return PsiDocParamRef.this;
      }

      @Override
      public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        if (isReferenceTo(element)) return PsiDocParamRef.this;
        if(!(element instanceof PsiParameter)) {
          throw new IncorrectOperationException("Unsupported operation");
        }
        return handleElementRename(((PsiParameter)element).getName());
      }
    };
  }

  @NotNull
  public static List<PsiNamedElement> getAllParameters(@NotNull PsiDocComment comment) {
    List<PsiNamedElement> allParams = new ArrayList<>();
    PsiJavaDocumentedElement owner = comment.getOwner();
    if (owner instanceof PsiMethod) {
      Collections.addAll(allParams, ((PsiMethod)owner).getParameterList().getParameters());
    }
    if (owner instanceof PsiMethod || owner instanceof PsiClass) {
      PsiTypeParameterList tpl = ((PsiTypeParameterListOwner)owner).getTypeParameterList();
      if (tpl != null) {
        Collections.addAll(allParams, tpl.getTypeParameters());
      }
    }
    return allParams;
  }

  public boolean isTypeParamRef() {
    return PsiUtilCore.getElementType(getFirstChild()) == JavaDocTokenType.DOC_TAG_VALUE_LT;
  }

  @Nullable
  public ASTNode getValueToken() {
    return findChildByType(JavaDocTokenType.DOC_TAG_VALUE_TOKEN);
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
}
