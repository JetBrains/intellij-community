/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class ClsAnnotationParameterListImpl extends ClsElementImpl implements PsiAnnotationParameterList {
  private final PsiAnnotation myParent;
  private final ClsNameValuePairImpl[] myAttributes;

  public ClsAnnotationParameterListImpl(@NotNull PsiAnnotation parent, @NotNull PsiNameValuePair[] psiAttributes) {
    myParent = parent;
    myAttributes = new ClsNameValuePairImpl[psiAttributes.length];
    for (int i = 0; i < psiAttributes.length; i++) {
      String name = psiAttributes[i].getName();

      PsiAnnotationMemberValue value = psiAttributes[i].getValue();
      if (value == null) {
        String anno = parent instanceof ClsAnnotationImpl ? ((ClsAnnotationImpl)parent).getStub().getText() : parent.getText();
        Logger.getInstance(getClass()).error("name=" + name + " anno=[" + anno + "] file=" + parent.getContainingFile());
        value = new ClsLiteralExpressionImpl(this, "null", PsiType.NULL, null);
      }

      if (psiAttributes.length == 1 && "value".equals(name)) {
        name = null;  // cosmetics - omit default attribute name
      }

      myAttributes[i] = new ClsNameValuePairImpl(this, name, value);
    }
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    if (myAttributes.length != 0) {
      buffer.append("(");
      for (int i = 0; i < myAttributes.length; i++) {
        if (i > 0) buffer.append(", ");
        myAttributes[i].appendMirrorText(indentLevel, buffer);
      }
      buffer.append(")");
    }
  }

  @Override
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, null);
    setMirrors(myAttributes, SourceTreeToPsiMap.<PsiAnnotationParameterList>treeToPsiNotNull(element).getAttributes());
  }

  @Override
  @NotNull
  public PsiElement[] getChildren() {
    return myAttributes;
  }

  @Override
  public PsiElement getParent() {
    return myParent;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAnnotationParameterList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  @NotNull
  public PsiNameValuePair[] getAttributes() {
    return myAttributes;
  }
}
