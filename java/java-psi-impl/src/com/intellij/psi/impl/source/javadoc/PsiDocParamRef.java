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
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author mike
 */
public class PsiDocParamRef extends CompositePsiElement implements PsiDocTagValue {

  public PsiDocParamRef() {
    super(Constants.DOC_PARAMETER_REF);
  }

  public PsiReference getReference() {
    final PsiDocComment comment = PsiTreeUtil.getParentOfType(this, PsiDocComment.class);
    if (comment == null) return null;
    final PsiDocCommentOwner owner = comment.getOwner();
    if (!(owner instanceof PsiMethod) &&
        !(owner instanceof PsiClass)) return null;
    final ASTNode valueToken = findChildByType(JavaDocTokenType.DOC_TAG_VALUE_TOKEN);
    if (valueToken == null) return null;
    final String name = valueToken.getText();
    PsiElement reference = null;
    final PsiElement firstChild = getFirstChild();
    if (firstChild instanceof PsiDocToken && ((PsiDocToken)firstChild).getTokenType().equals(JavaDocTokenType.DOC_TAG_VALUE_LT)) {
      final PsiTypeParameter[] typeParameters = ((PsiTypeParameterListOwner)owner).getTypeParameters();
      for (PsiTypeParameter typeParameter : typeParameters) {
        if (typeParameter.getName().equals(name)) {
          reference = typeParameter;
        }
      }
    }
    else if (owner instanceof PsiMethod) {
      final PsiParameter[] parameters = ((PsiMethod)owner).getParameterList().getParameters();
      for (PsiParameter parameter : parameters) {
        if (parameter.getName().equals(name)) {
          reference = parameter;
        }
      }
    }

    final PsiElement resultReference = reference;
    return new PsiJavaReference() {
      public PsiElement resolve() {
        return resultReference;
      }

      @NotNull
      public String getCanonicalText() {
        return valueToken.getText();
      }

      public PsiElement handleElementRename(String newElementName) {
        final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(getNode());
        LeafElement newElement = Factory.createSingleLeafElement(JavaDocTokenType.DOC_TAG_VALUE_TOKEN, newElementName, charTableByTree, getManager());
        replaceChild(valueToken, newElement);
        return PsiDocParamRef.this;
      }

      public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        if (isReferenceTo(element)) return PsiDocParamRef.this;
        if(!(element instanceof PsiParameter)) {
          throw new IncorrectOperationException("Unsupported operation");
        }
        return handleElementRename(((PsiParameter) element).getName());
      }

      public boolean isReferenceTo(PsiElement element) {
        if (!(element instanceof PsiNamedElement)) return false;
        PsiNamedElement namedElement = (PsiNamedElement)element;
        if (!getCanonicalText().equals(namedElement.getName())) return false;
        return getManager().areElementsEquivalent(resolve(), element);
      }

      @NotNull
      public PsiElement[] getVariants() {
        final PsiElement firstChild = getFirstChild();
        if (firstChild instanceof PsiDocToken && ((PsiDocToken)firstChild).getTokenType().equals(JavaDocTokenType.DOC_TAG_VALUE_LT)) {
          return ((PsiTypeParameterListOwner)owner).getTypeParameters();
        } else if (owner instanceof PsiMethod) {
          return ((PsiMethod)owner).getParameterList().getParameters();
        }
        return PsiElement.EMPTY_ARRAY;
      }

      public boolean isSoft(){
        return false;
      }

      public TextRange getRangeInElement() {
        final int startOffsetInParent = valueToken.getPsi().getStartOffsetInParent();
        return new TextRange(startOffsetInParent, startOffsetInParent + valueToken.getTextLength());
      }

      public PsiElement getElement() {
        return PsiDocParamRef.this;
      }

      public void processVariants(PsiScopeProcessor processor) {
        for (final PsiElement element : getVariants()) {
          if (!processor.execute(element, ResolveState.initial())) {
            return;
          }
        }
      }

      @NotNull
      public JavaResolveResult advancedResolve(boolean incompleteCode) {
        return resultReference == null ? JavaResolveResult.EMPTY : new CandidateInfo(resultReference, PsiSubstitutor.EMPTY);
      }

      @NotNull
      public JavaResolveResult[] multiResolve(boolean incompleteCode) {
        return resultReference == null
               ? JavaResolveResult.EMPTY_ARRAY
               : new JavaResolveResult[]{new CandidateInfo(resultReference, PsiSubstitutor.EMPTY)};
      }
    };
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitDocTagValue(this);
    }
    else {
      visitor.visitElement(this);
    }
  }
}
