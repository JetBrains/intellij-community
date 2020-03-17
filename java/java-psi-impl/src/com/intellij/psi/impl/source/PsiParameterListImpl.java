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
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiParameterListStub;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiParameterListImpl extends JavaStubPsiElement<PsiParameterListStub> implements PsiParameterList {
  private static final Logger LOG = Logger.getInstance(PsiParameterListImpl.class);

  public PsiParameterListImpl(@NotNull PsiParameterListStub stub) {
    super(stub, JavaStubElementTypes.PARAMETER_LIST);
  }

  public PsiParameterListImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  @NotNull
  public PsiParameter[] getParameters() {
    return getStubOrPsiChildren(JavaStubElementTypes.PARAMETER, PsiParameter.ARRAY_FACTORY);
  }

  @Override
  public int getParameterIndex(@NotNull PsiParameter parameter) {
    LOG.assertTrue(parameter.getParent() == this);
    return PsiImplUtil.getParameterIndex(parameter, this);
  }

  @Override
  @NotNull
  public CompositeElement getNode() {
    return (CompositeElement)super.getNode();
  }

  @Override
  public int getParametersCount() {
    final PsiParameterListStub stub = getGreenStub();
    if (stub != null) {
      int count = 0;
      for (StubElement<?> child : stub.getChildrenStubs()) {
        if (child.getStubType() == JavaStubElementTypes.PARAMETER) {
          count++;
        }
      }
      return count;
    }

    return getNode().countChildren(Constants.PARAMETER_BIT_SET);
  }

  @Nullable
  @Override
  public PsiParameter getParameter(int index) {
    if (index < 0) {
      throw new IllegalArgumentException("index is negative: " + index);
    }
    final PsiParameterListStub stub = getGreenStub();
    if (stub != null) {
      int count = 0;
      for (StubElement<?> child : stub.getChildrenStubs()) {
        if (child.getStubType() == JavaStubElementTypes.PARAMETER) {
          if (count == index) return (PsiParameter)child.getPsi(); 
          count++;
        }
      }
    } else {
      CompositeElement node = getNode();
      int count = 0;
      for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
        if (child.getElementType() == JavaStubElementTypes.PARAMETER) {
          if (count == index) return (PsiParameter)child.getPsi();
          count++;
        }
      }
    }
    return null;
  }

  @Override
  public boolean isEmpty() {
    final PsiParameterListStub stub = getGreenStub();
    if (stub != null) {
      for (StubElement<?> child : stub.getChildrenStubs()) {
        if (child.getStubType() == JavaStubElementTypes.PARAMETER) {
          return false;
        }
      }
      return true;
    }

    return getNode().findChildByType(Constants.PARAMETER_BIT_SET) == null;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitParameterList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  @NonNls
  public String toString(){
    return "PsiParameterList:" + getText();
  }
}