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
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiParameterListStub;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ClsParameterListImpl extends ClsRepositoryPsiElement<PsiParameterListStub> implements PsiParameterList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsParameterListImpl");

  public ClsParameterListImpl(final PsiParameterListStub stub) {
    super(stub);
  }

  @NotNull
  public PsiParameter[] getParameters() {
    return getStub().getChildrenByType(JavaStubElementTypes.PARAMETER, PsiParameter.ARRAY_FACTORY);
  }

  public int getParameterIndex(PsiParameter parameter) {
    LOG.assertTrue(parameter.getParent() == this);
    return PsiImplUtil.getParameterIndex(parameter, this);
  }

  public int getParametersCount() {
    return getParameters().length;
  }

  public void appendMirrorText(final int indentLevel, final StringBuilder buffer) {
    buffer.append('(');
    PsiParameter[] parameters = getParameters();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parm = parameters[i];
      if (i > 0) buffer.append(", ");
      ((ClsElementImpl)parm).appendMirrorText(indentLevel, buffer);
    }
    buffer.append(')');
  }

  public void setMirror(@NotNull TreeElement element) {
    setMirrorCheckingType(element, null);

    PsiParameter[] parms = getParameters();
    PsiParameter[] parmMirrors = ((PsiParameterList)SourceTreeToPsiMap.treeElementToPsi(element)).getParameters();
    LOG.assertTrue(parms.length == parmMirrors.length);
    if (parms.length == parmMirrors.length) {
      for (int i = 0; i < parms.length; i++) {
          ((ClsElementImpl)parms[i]).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(parmMirrors[i]));
      }
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitParameterList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @NonNls
  public String toString() {
    return "PsiParameterList";
  }
}
