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
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiParameterStub;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.reference.SoftReference;
import com.intellij.ui.RowIcon;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;

public class PsiParameterImpl extends JavaStubPsiElement<PsiParameterStub> implements PsiParameter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiParameterImpl");

  private volatile SoftReference<PsiType> myCachedType = null;

  public PsiParameterImpl(@NotNull PsiParameterStub stub) {
    this(stub, JavaStubElementTypes.PARAMETER);
  }

  protected PsiParameterImpl(@NotNull PsiParameterStub stub, @NotNull IStubElementType type) {
    super(stub, type);
  }

  public PsiParameterImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    myCachedType = null;
  }

  @Override
  protected Object clone() {
    PsiParameterImpl clone = (PsiParameterImpl)super.clone();
    clone.myCachedType = null;

    return clone;
  }

  @Override
  @NotNull
  public final String getName() {
    PsiParameterStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }

    return getParameterIdentifier().getText();
  }

  @Override
  public final PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    if (this instanceof PsiReceiverParameter) {
      throw new IncorrectOperationException("Cannot rename receiver parameter");
    }

    PsiImplUtil.setName(getParameterIdentifier(), name);
    return this;
  }

  @Override
  public final PsiIdentifier getNameIdentifier() {
    return PsiTreeUtil.getChildOfType(this, PsiIdentifier.class);
  }

  @NotNull
  private PsiElement getParameterIdentifier() {
    PsiJavaToken identifier = PsiTreeUtil.getChildOfAnyType(this, PsiIdentifier.class, PsiKeyword.class);
    assert identifier != null : getClass() + ":" + getText();
    return identifier;
  }

  @Override
  @NotNull
  public CompositeElement getNode() {
    return (CompositeElement)super.getNode();
  }

  @Override
  @NotNull
  public PsiType getType() {
    PsiParameterStub stub = getStub();
    if (stub != null) {
      SoftReference<PsiType> cachedType = myCachedType;
      if (cachedType != null) {
        PsiType type = cachedType.get();
        if (type != null) return type;
      }

      String typeText = TypeInfo.createTypeText(stub.getType(true));
      assert typeText != null : stub;
      try {
        PsiType type = JavaPsiFacade.getInstance(getProject()).getParserFacade().createTypeFromText(typeText, this);
        myCachedType = new SoftReference<PsiType>(type);
        return type;
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return null;
      }
    }

    myCachedType = null;

    PsiTypeElement typeElement = getTypeElement();
    if (typeElement == null) {
      assert isLambdaParameter() : this;
      return LambdaUtil.getLambdaParameterType(this);
    }
    else {
      return JavaSharedImplUtil.getType(typeElement, getParameterIdentifier());
    }
  }

  private boolean isLambdaParameter() {
    final PsiElement parent = getParent();
    return parent instanceof PsiParameterList && parent.getParent() instanceof PsiLambdaExpression;
  }

  @Override
  public PsiTypeElement getTypeElement() {
    for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiTypeElement) {
        //noinspection unchecked
        return (PsiTypeElement)child;
      }
    }
    return null;
  }

  @Override
  @NotNull
  public PsiModifierList getModifierList() {
    PsiModifierList modifierList = getStubOrPsiChild(JavaStubElementTypes.MODIFIER_LIST);
    assert modifierList != null : this;
    return modifierList;
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @Override
  public PsiExpression getInitializer() {
    return null;
  }

  @Override
  public boolean hasInitializer() {
    return false;
  }

  @Override
  public Object computeConstantValue() {
    return null;
  }

  @Override
  public void normalizeDeclaration() throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    JavaSharedImplUtil.normalizeBrackets(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitParameter(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiParameter:" + getName();
  }

  @Override
  @NotNull
  public PsiElement getDeclarationScope() {
    final PsiElement parent = getParent();
    if (parent == null) return this;

    if (parent instanceof PsiParameterList) {
      return parent.getParent();
    }
    if (parent instanceof PsiForeachStatement) {
      return parent;
    }
    if (parent instanceof PsiCatchSection) {
      return parent;
    }

    PsiElement[] children = parent.getChildren();
    //noinspection ConstantConditions
    if (children != null) {
      ext:
      for (int i = 0; i < children.length; i++) {
        if (children[i].equals(this)) {
          for (int j = i + 1; j < children.length; j++) {
            if (children[j] instanceof PsiCodeBlock) return children[j];
          }
          break ext;
        }
      }
    }

    LOG.error("Code block not found among parameter' (" + this + ") parent' (" + parent + ") children: " + Arrays.asList(children));
    return null;
  }

  @Override
  public boolean isVarArgs() {
    final PsiParameterStub stub = getStub();
    if (stub != null) {
      return stub.isParameterTypeEllipsis();
    }

    myCachedType = null;
    final PsiTypeElement typeElement = getTypeElement();
    return typeElement != null && SourceTreeToPsiMap.psiToTreeNotNull(typeElement).findChildByType(JavaTokenType.ELLIPSIS) != null;
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProviders.getItemPresentation(this);
  }

  @Override
  public Icon getElementIcon(final int flags) {
    final RowIcon baseIcon = createLayeredIcon(this, PlatformIcons.PARAMETER_ICON, 0);
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }
  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }

  @Override
  @NotNull
  public SearchScope getUseScope() {
    final PsiElement declarationScope = getDeclarationScope();
    return new LocalSearchScope(declarationScope);
  }
}
