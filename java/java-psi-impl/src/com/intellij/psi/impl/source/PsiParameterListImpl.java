// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
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
  public PsiParameter @NotNull [] getParameters() {
    return getStubOrPsiChildren(JavaStubElementTypes.PARAMETER, PsiParameter.ARRAY_FACTORY);
  }

  @Override
  public int getParameterIndex(@NotNull PsiParameter parameter) {
    PsiElement parent = parameter.getParent();
    if (parent != this) {
      LOG.error("Not my parameter; parameter class = " + parameter.getClass() + "; " +
                "this class = " + getClass() + "; " +
                "parameter parent class = " + (parent == null ? null : parent.getClass()));
    }
    return PsiImplUtil.getParameterIndex(parameter, this);
  }

  @Override
  public @NotNull CompositeElement getNode() {
    return (CompositeElement)super.getNode();
  }

  @Override
  public int getParametersCount() {
    PsiParameterListStub stub = getGreenStub();
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

  @Override
  public @Nullable PsiParameter getParameter(int index) {
    if (index < 0) {
      throw new IllegalArgumentException("index is negative: " + index);
    }
    PsiParameterListStub stub = getGreenStub();
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
    PsiParameterListStub stub = getGreenStub();
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
  public @NonNls String toString(){
    return "PsiParameterList:" + getText();
  }
}