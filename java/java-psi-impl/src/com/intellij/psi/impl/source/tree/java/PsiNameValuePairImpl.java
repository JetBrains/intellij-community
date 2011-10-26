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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */

//Retrieves method reference from this pair, do NOT reuse!!!
public class PsiNameValuePairImpl extends CompositePsiElement implements PsiNameValuePair {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiNameValuePairImpl");
  private volatile String myCachedName = null;
  private volatile PsiIdentifier myCachedNameIdentifier = null;
  private volatile PsiAnnotationMemberValue myCachedValue = null;
  private volatile boolean myNameCached = false;

  @Override
  public void clearCaches() {
    myNameCached = false;
    myCachedName = null;
    myCachedNameIdentifier = null;
    myCachedValue = null;
    super.clearCaches();
  }

  public PsiNameValuePairImpl() {
    super(JavaElementType.NAME_VALUE_PAIR);
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    PsiIdentifier cachedNameIdentifier = myCachedNameIdentifier;
    if (!myNameCached) {
      myCachedNameIdentifier = cachedNameIdentifier = (PsiIdentifier)findChildByRoleAsPsiElement(ChildRole.NAME);
      myCachedName = cachedNameIdentifier == null ? null : cachedNameIdentifier.getText();
      myNameCached = true;
    }
    return cachedNameIdentifier;
  }

  @Override
  public String getName() {
    String cachedName = myCachedName;
    if (!myNameCached) {
      PsiIdentifier identifier;
      myCachedNameIdentifier = identifier = (PsiIdentifier)findChildByRoleAsPsiElement(ChildRole.NAME);
      myCachedName = cachedName = identifier == null ? null : identifier.getText();
      myNameCached = true;
    }
    return cachedName;
  }

  @Override
  public PsiAnnotationMemberValue getValue() {
    PsiAnnotationMemberValue cachedValue = myCachedValue;
    if (cachedValue == null) {
      myCachedValue = cachedValue = (PsiAnnotationMemberValue)findChildByRoleAsPsiElement(ChildRole.ANNOTATION_VALUE);
    }

    return cachedValue;
  }

  @Override
  @NotNull
  public PsiAnnotationMemberValue setValue(@NotNull PsiAnnotationMemberValue newValue) {
    getValue().replace(newValue);
    return getValue();
  }

  @Override
  public int getChildRole(ASTNode child) {
    if (ElementType.ANNOTATION_MEMBER_VALUE_BIT_SET.contains(child.getElementType())) {
      return ChildRole.ANNOTATION_VALUE;
    }
    if (child.getElementType() == JavaTokenType.IDENTIFIER) {
      return ChildRole.NAME;
    }
    if (child.getElementType() == JavaTokenType.EQ) {
      return ChildRole.OPERATION_SIGN;
    }

    return ChildRoleBase.NONE;
  }

  @Override
  public ASTNode findChildByRole(int role) {
    if (role == ChildRole.NAME) {
      return findChildByType(JavaTokenType.IDENTIFIER);
    }
    if (role == ChildRole.ANNOTATION_VALUE) {
      return findChildByType(ElementType.ANNOTATION_MEMBER_VALUE_BIT_SET);
    }
    if (role == ChildRole.OPERATION_SIGN) {
      return findChildByType(JavaTokenType.EQ);
    }

    return null;
  }

  public String toString() {
    return "PsiNameValuePair";
  }

  @Override
  public PsiReference getReference() {
    return new PsiReference() {
      @Nullable
      private PsiClass getReferencedClass() {
        LOG.assertTrue(getParent() instanceof PsiAnnotationParameterList && getParent().getParent() instanceof PsiAnnotation);
        PsiAnnotation annotation = (PsiAnnotation)getParent().getParent();
        PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
        if (nameRef == null) return null;
        PsiElement target = nameRef.resolve();
        return target instanceof PsiClass ? (PsiClass)target : null;
      }

      @Override
      public PsiElement getElement() {
        PsiIdentifier nameIdentifier = getNameIdentifier();
        if (nameIdentifier != null) {
          return nameIdentifier;
        }
        return PsiNameValuePairImpl.this;
      }

      @Override
      public TextRange getRangeInElement() {
        PsiIdentifier id = getNameIdentifier();
        if (id != null) {
          return new TextRange(0, id.getTextLength());
        }
        return TextRange.EMPTY_RANGE;
      }

      @Override
      public PsiElement resolve() {
        PsiClass refClass = getReferencedClass();
        if (refClass == null) return null;
        String name = getName();
        if (name == null) name = PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME;
        MethodSignature signature = MethodSignatureUtil.createMethodSignature(name, PsiType.EMPTY_ARRAY, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
        return MethodSignatureUtil.findMethodBySignature(refClass, signature, false);
      }

      @Override
      @NotNull
      public String getCanonicalText() {
        String name = getName();
        return name != null ? name : PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME;
      }

      @Override
      public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
        PsiIdentifier nameIdentifier = getNameIdentifier();
        if (nameIdentifier != null) {
          PsiImplUtil.setName(nameIdentifier, newElementName);
        }
        else if (ElementType.ANNOTATION_MEMBER_VALUE_BIT_SET.contains(getFirstChildNode().getElementType())) {
          PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
          nameIdentifier = factory.createIdentifier(newElementName);
          addBefore(nameIdentifier, SourceTreeToPsiMap.treeElementToPsi(getFirstChildNode()));
        }

        return PsiNameValuePairImpl.this;
      }

      @Override
      public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        throw new IncorrectOperationException("Not implemented");
      }

      @Override
      public boolean isReferenceTo(PsiElement element) {
        return element instanceof PsiMethod && element.equals(resolve());
      }

      @Override
      @NotNull
      public Object[] getVariants() {
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
      }

      @Override
      public boolean isSoft() {
        return false;
      }
    };
  }

  @Override
  public final void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitNameValuePair(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
    final TreeElement treeElement = super.addInternal(first, last, anchor, before);
    if (first == last && first.getElementType() == JavaTokenType.IDENTIFIER) {
      LeafElement eq = Factory.createSingleLeafElement(JavaTokenType.EQ, "=", 0, 1, treeCharTab, getManager());
      super.addInternal(eq, eq, first, Boolean.FALSE);
    }
    return treeElement;
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    super.deleteChildInternal(child);
    if (child.getElementType() == JavaTokenType.IDENTIFIER) {
      final ASTNode sign = findChildByRole(ChildRole.OPERATION_SIGN);
      if (sign != null) {
        super.deleteChildInternal(sign);
      }
    }
  }
}
