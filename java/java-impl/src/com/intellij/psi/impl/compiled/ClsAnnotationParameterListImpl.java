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
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class ClsAnnotationParameterListImpl extends ClsElementImpl implements PsiAnnotationParameterList {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.compiled.ClsAnnotationParameterListImpl");
  private final ClsNameValuePairImpl[] myAttributes;
  private final PsiAnnotation myParent;

  public ClsAnnotationParameterListImpl(PsiAnnotation parent, PsiNameValuePair[] psiAttributes) {
    myParent = parent;
    myAttributes = new ClsNameValuePairImpl[psiAttributes.length];
    for (int i = 0; i < myAttributes.length; i++) {
      String name = psiAttributes[i].getName();
      PsiAnnotationMemberValue value = psiAttributes[i].getValue();
      myAttributes[i] = new ClsNameValuePairImpl(this, name, value);
    }
  }

  public void appendMirrorText(final int indentLevel, final StringBuilder buffer) {
    if (myAttributes.length != 0) {
      buffer.append("(");
      for (int i = 0; i < myAttributes.length; i++) {
        if (i > 0) buffer.append(", ");
        myAttributes[i].appendMirrorText(indentLevel, buffer);
      }

      buffer.append(")");
    }
  }

  public void setMirror(@NotNull TreeElement element) {
    setMirrorCheckingType(element, null);

    PsiAnnotationParameterList mirror = (PsiAnnotationParameterList)SourceTreeToPsiMap.treeElementToPsi(element);
    PsiNameValuePair[] attrs = mirror.getAttributes();
    LOG.assertTrue(myAttributes.length == attrs.length);
    for (int i = 0; i < myAttributes.length; i++) {
      myAttributes[i].setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(attrs[i]));
    }
  }

  @NotNull
  public PsiElement[] getChildren() {
    return myAttributes;
  }

  public PsiElement getParent() {
    return myParent;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAnnotationParameterList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @NotNull
  public PsiNameValuePair[] getAttributes() {
    return myAttributes;
  }
}
