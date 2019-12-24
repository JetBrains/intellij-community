// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.reference.SoftReference;
import com.intellij.ui.IconManager;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.lang.ref.Reference;
import java.util.Arrays;

public class PsiParameterImpl extends JavaStubPsiElement<PsiParameterStub> implements PsiParameter {
  private static final Logger LOG = Logger.getInstance(PsiParameterImpl.class);

  private volatile Reference<PsiType> myCachedType;

  public PsiParameterImpl(@NotNull PsiParameterStub stub) {
    this(stub, JavaStubElementTypes.PARAMETER);
  }

  protected PsiParameterImpl(@NotNull PsiParameterStub stub, @NotNull IStubElementType type) {
    super(stub, type);
  }

  public PsiParameterImpl(@NotNull ASTNode node) {
    super(node);
  }

  public static PsiType getLambdaParameterType(PsiParameter param) {
    final PsiElement paramParent = param.getParent();
    if (paramParent instanceof PsiParameterList) {
      final int parameterIndex = ((PsiParameterList)paramParent).getParameterIndex(param);
      if (parameterIndex > -1) {
        final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(param, PsiLambdaExpression.class);
        if (lambdaExpression != null) {
          final PsiType functionalInterfaceType = MethodCandidateInfo.ourOverloadGuard.doPreventingRecursion(param, false,
                                                                                                              () -> LambdaUtil.getFunctionalInterfaceType(lambdaExpression, true));
          PsiType type = lambdaExpression.getGroundTargetType(functionalInterfaceType);
          if (type instanceof PsiIntersectionType) {
            final PsiType[] conjuncts = ((PsiIntersectionType)type).getConjuncts();
            for (PsiType conjunct : conjuncts) {
              final PsiType lambdaParameterFromType = LambdaUtil.getLambdaParameterFromType(conjunct, parameterIndex);
              if (lambdaParameterFromType != null) {
                return lambdaParameterFromType;
              }
            }
          } else {
            final PsiType lambdaParameterFromType = LambdaUtil.getLambdaParameterFromType(type, parameterIndex);
            if (lambdaParameterFromType != null) {
              return lambdaParameterFromType;
            }
          }
        }
      }
    }
    return new PsiLambdaParameterType(param);
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
    PsiParameterStub stub = getGreenStub();
    if (stub != null) {
      return stub.getName();
    }

    return getNameIdentifier().getText();
  }

  @Override
  public final PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  @Override
  @NotNull
  public final PsiIdentifier getNameIdentifier() {
    return PsiTreeUtil.getRequiredChildOfType(this, PsiIdentifier.class);
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
      PsiType type = SoftReference.dereference(myCachedType);
      if (type == null) {
        String typeText = TypeInfo.createTypeText(stub.getType(false));
        assert typeText != null : stub;
        type = JavaPsiFacade.getInstance(getProject()).getParserFacade().createTypeFromText(typeText, this);
        type = JavaSharedImplUtil.applyAnnotations(type, getModifierList());
        myCachedType = new SoftReference<>(type);
      }
      return type;
    }

    myCachedType = null;

    PsiTypeElement typeElement = getTypeElement();
    if (typeElement == null || isLambdaParameter() && typeElement.isInferredType()) {
      assert isLambdaParameter() : this;
      return getLambdaParameterType(this);
    }
    else {
      return JavaSharedImplUtil.getType(typeElement, getNameIdentifier());
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

  @Override
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
    final PsiParameterStub stub = getGreenStub();
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
    final RowIcon baseIcon = IconManager.getInstance().createLayeredIcon(this, PlatformIcons.PARAMETER_ICON, 0);
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

  @Override
  public PsiElement getOriginalElement() {
    PsiElement parent = getParent();
    if (parent instanceof PsiParameterList) {
      PsiElement gParent = parent.getParent();
      if (gParent instanceof PsiMethod) {
        PsiElement originalMethod = gParent.getOriginalElement();
        if (originalMethod instanceof PsiMethod && originalMethod != gParent) {
          int index = ((PsiParameterList)parent).getParameterIndex(this);
          PsiParameter[] originalParameters = ((PsiMethod)originalMethod).getParameterList().getParameters();
          if (index < originalParameters.length) {
            return originalParameters[index];
          }
        }
      }
    }
    return this;
  }
}