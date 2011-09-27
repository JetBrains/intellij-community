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
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class ClsReferenceParametersListImpl extends ClsElementImpl implements PsiReferenceParameterList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsReferenceParametersListImpl");

  private final PsiElement myParent;
  private final PsiTypeElement[] myTypeElements;

  public ClsReferenceParametersListImpl(PsiElement parent, PsiTypeElement[] typeElements) {
    myParent = parent;
    myTypeElements = typeElements;
  }

  public void appendMirrorText(final int indentLevel, final StringBuilder buffer) {
    if (myTypeElements.length != 0) {
      buffer.append('<');
      for (int i = 0; i < myTypeElements.length; i++) {
        if (i > 0) buffer.append(" ,");
        ClsTypeElementImpl typeElement = (ClsTypeElementImpl)myTypeElements[i];
        typeElement.appendMirrorText(indentLevel, buffer);
      }
      buffer.append('>');
    }
  }

  public void setMirror(@NotNull TreeElement element) {
    setMirrorCheckingType(element, null);

    PsiTypeElement[] typeElements = getTypeParameterElements();
    PsiTypeElement[] typeMirrors = ((PsiReferenceParameterList)SourceTreeToPsiMap.treeElementToPsi(element)).getTypeParameterElements();
    LOG.assertTrue(typeElements.length == typeMirrors.length);
    if (typeElements.length == typeMirrors.length) {
      for (int i = 0; i < typeElements.length; i++) {
          ((ClsElementImpl)typeElements[i]).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(typeMirrors[i]));
      }
    }
  }

  @NotNull
  public PsiElement[] getChildren() {
    return myTypeElements;
  }

  @NotNull
  public PsiTypeElement[] getTypeParameterElements() {
    return myTypeElements;
  }

  @NotNull
  public PsiType[] getTypeArguments() {
    return PsiImplUtil.typesByTypeElements(myTypeElements);
  }

  public PsiElement getParent() {
    return myParent;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReferenceParameterList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }
}
