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
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public abstract class ClsAnnotationValueImpl extends ClsElementImpl implements PsiAnnotation, Navigatable {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.compiled.ClsAnnotationValueImpl");
  public static final ClsAnnotationImpl[] EMPTY_ARRAY = new ClsAnnotationImpl[0];
  private final ClsJavaCodeReferenceElementImpl myReferenceElement;
  private final ClsAnnotationParameterListImpl myParameterList;
  private final ClsElementImpl myParent;

  public ClsAnnotationValueImpl(ClsElementImpl parent) {
    myReferenceElement = createReference();
    myParameterList = createParameterList();
    myParent = parent;
  }

  protected abstract ClsAnnotationParameterListImpl createParameterList();

  protected abstract ClsJavaCodeReferenceElementImpl createReference();

  public void appendMirrorText(final int indentLevel, final StringBuilder buffer) {
    buffer.append("@").append(myReferenceElement.getCanonicalText());
    myParameterList.appendMirrorText(indentLevel, buffer);
  }

  public void setMirror(@NotNull TreeElement element) {
    setMirrorCheckingType(element, null);

    PsiAnnotation mirror = (PsiAnnotation)SourceTreeToPsiMap.treeElementToPsi(element);
      ((ClsElementImpl)getParameterList()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getParameterList()));
      ((ClsElementImpl)getNameReferenceElement()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getNameReferenceElement()));
  }

  @NotNull
  public PsiElement[] getChildren() {
    return new PsiElement[]{myReferenceElement, myParameterList};
  }

  public PsiElement getParent() {
    return myParent;
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
    return myParameterList;
  }

  @Nullable public String getQualifiedName() {
    if (myReferenceElement == null) return null;
    return myReferenceElement.getCanonicalText();
  }

  public PsiJavaCodeReferenceElement getNameReferenceElement() {
    return myReferenceElement;
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
}
