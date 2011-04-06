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
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.PsiAnnotationStub;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class ClsAnnotationImpl extends ClsRepositoryPsiElement<PsiAnnotationStub> implements PsiAnnotation, Navigatable {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.compiled.ClsAnnotationImpl");
  private ClsJavaCodeReferenceElementImpl myReferenceElement; //protected by lock
  private ClsAnnotationParameterListImpl myParameterList; //protected by lock
  private final Object lock = new Object();

  public ClsAnnotationImpl(final PsiAnnotationStub stub) {
    super(stub);
  }

  public void appendMirrorText(final int indentLevel, final StringBuilder buffer) {
    buffer.append("@").append(getReferenceElement().getCanonicalText());
    ((ClsAnnotationParameterListImpl)getParameterList()).appendMirrorText(indentLevel, buffer);
  }

  public void setMirror(@NotNull TreeElement element) {
    setMirrorCheckingType(element, null);

    PsiAnnotation mirror = (PsiAnnotation)SourceTreeToPsiMap.treeElementToPsi(element);
      ((ClsElementImpl)getParameterList()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getParameterList()));
      ((ClsElementImpl)getNameReferenceElement()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getNameReferenceElement()));
  }

  @NotNull
  public PsiElement[] getChildren() {
    return new PsiElement[]{getReferenceElement(), getParameterList()};
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAnnotation(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @NotNull
  public PsiAnnotationParameterList getParameterList() {
    synchronized (lock) {
      if (myParameterList == null) {
        final PsiAnnotationStub stub = getStub();
        final CompositeElement mirror = stub.getTreeElement();

        final PsiAnnotationParameterList paramList = (PsiAnnotationParameterList)mirror.findChildByRoleAsPsiElement(ChildRole.PARAMETER_LIST);

        myParameterList = new ClsAnnotationParameterListImpl(this, paramList.getAttributes());
      }

      return myParameterList;
    }
  }

  @Nullable public String getQualifiedName() {
    if (getReferenceElement() == null) return null;
    return getReferenceElement().getCanonicalText();
  }

  public PsiJavaCodeReferenceElement getNameReferenceElement() {
    return getReferenceElement();
  }

  public PsiAnnotationMemberValue findAttributeValue(String attributeName) {
    return PsiImplUtil.findAttributeValue(this, attributeName);
  }

  @Nullable
  public PsiAnnotationMemberValue findDeclaredAttributeValue(@NonNls final String attributeName) {
    return PsiImplUtil.findDeclaredAttributeValue(this, attributeName);
  }

  public <T extends PsiAnnotationMemberValue>  T setDeclaredAttributeValue(@NonNls String attributeName, T value) {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  public String getText() {
    final StringBuilder buffer = new StringBuilder();
    appendMirrorText(0, buffer);
    return buffer.toString();
  }

  public PsiMetaData getMetaData() {
    return MetaRegistry.getMetaBase(this);
  }

  private ClsJavaCodeReferenceElementImpl getReferenceElement() {
    synchronized (lock) {
      if (myReferenceElement == null) {
        final PsiAnnotationStub stub = getStub();
        final CompositeElement mirror = stub.getTreeElement();
        myReferenceElement = new ClsJavaCodeReferenceElementImpl(this, mirror.findChildByRole(ChildRole.CLASS_REFERENCE).getText());
      }

      return myReferenceElement;
    }
  }

  public PsiAnnotationOwner getOwner() {
    return (PsiAnnotationOwner)getParent();//todo
  }
}
