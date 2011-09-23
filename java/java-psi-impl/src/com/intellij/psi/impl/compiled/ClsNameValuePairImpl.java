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
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class ClsNameValuePairImpl extends ClsElementImpl implements PsiNameValuePair {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.compiled.ClsNameValuePairImpl");
  private final ClsElementImpl myParent;
  private final ClsIdentifierImpl myNameIdentifier;
  private final PsiAnnotationMemberValue myMemberValue;

  public ClsNameValuePairImpl(ClsElementImpl parent, String name, PsiAnnotationMemberValue value) {
    myParent = parent;
    myNameIdentifier = new ClsIdentifierImpl(this, name);
    myMemberValue = ClsParsingUtil.getMemberValue(value, this);
  }

  public void appendMirrorText(final int indentLevel, final StringBuilder buffer) {
    if (myNameIdentifier.getText() != null) {
      myNameIdentifier.appendMirrorText(0, buffer);
      buffer.append(" = ");
    }
    ((ClsElementImpl)myMemberValue).appendMirrorText(0, buffer);
  }

  public void setMirror(@NotNull TreeElement element) {
    setMirrorCheckingType(element, null);

    PsiNameValuePair mirror = (PsiNameValuePair)SourceTreeToPsiMap.treeElementToPsi(element);
    final PsiIdentifier mirrorIdentifier = mirror.getNameIdentifier();
    if (mirrorIdentifier != null) {
      ((ClsElementImpl)getNameIdentifier()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirrorIdentifier));
    }
    ((ClsElementImpl)getValue()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getValue()));
  }

  @NotNull
  public PsiElement[] getChildren() {
    return new PsiElement[]{myNameIdentifier, myMemberValue};
  }

  public PsiElement getParent() {
    return myParent;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitNameValuePair(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public PsiIdentifier getNameIdentifier() {
    return myNameIdentifier;
  }

  public String getName() {
    return myNameIdentifier.getText();
  }

  public PsiAnnotationMemberValue getValue() {
    return myMemberValue;
  }

  @NotNull
  public PsiAnnotationMemberValue setValue(@NotNull PsiAnnotationMemberValue newValue) {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }
}
