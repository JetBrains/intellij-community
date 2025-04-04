// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiParameterStub;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;

public class PsiParameterImpl extends JavaStubPsiElement<PsiParameterStub> implements PsiParameter {
  private volatile PsiType myCachedType;
  private volatile String myCachedName;

  public PsiParameterImpl(@NotNull PsiParameterStub stub) {
    this(stub, JavaStubElementTypes.PARAMETER);
  }

  protected PsiParameterImpl(@NotNull PsiParameterStub stub, @NotNull IElementType type) {
    super(stub, type);
  }

  public PsiParameterImpl(@NotNull ASTNode node) {
    super(node);
  }

  private static PsiType getLambdaParameterType(@NotNull PsiParameter param) {
    PsiElement paramParent = param.getParent();
    if (paramParent instanceof PsiParameterList) {
      int parameterIndex = ((PsiParameterList)paramParent).getParameterIndex(param);
      if (parameterIndex > -1) {
        PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(param, PsiLambdaExpression.class);
        if (lambdaExpression != null) {
          PsiType functionalInterfaceType = MethodCandidateInfo.ourOverloadGuard.doPreventingRecursion(param, false,
                                                                                                              () -> LambdaUtil.getFunctionalInterfaceType(lambdaExpression, true));
          if (functionalInterfaceType == null) {
            Ref<PsiType> typeRef = Ref.create();
            // Probably there are several candidates for the functional expression type but all of them have the same parameter type
            LambdaUtil.processParentOverloads(lambdaExpression, t -> {
              PsiType candidate = getTypeForFunctionalInterfaceType(lambdaExpression, t, parameterIndex);
              PsiType prevType = typeRef.get();
              if (prevType == null) {
                typeRef.set(candidate);
              }
              else {
                if (!(prevType instanceof PsiLambdaParameterType) && !prevType.equals(candidate)) {
                  typeRef.set(new PsiLambdaParameterType(param));
                }
              }
            });
            if (!typeRef.isNull()) {
              return typeRef.get();
            }
          }
          PsiType lambdaParameterFromType = getTypeForFunctionalInterfaceType(lambdaExpression, functionalInterfaceType, parameterIndex);
          if (lambdaParameterFromType != null) return lambdaParameterFromType;
        }
      }
    }
    return new PsiLambdaParameterType(param);
  }

  private static @Nullable PsiType getTypeForFunctionalInterfaceType(PsiLambdaExpression lambdaExpression, PsiType functionalInterfaceType, int parameterIndex) {
    PsiType type = lambdaExpression.getGroundTargetType(functionalInterfaceType);
    if (type instanceof PsiIntersectionType) {
      PsiType[] conjuncts = ((PsiIntersectionType)type).getConjuncts();
      for (PsiType conjunct : conjuncts) {
        PsiType lambdaParameterFromType = LambdaUtil.getLambdaParameterFromType(conjunct, parameterIndex);
        if (lambdaParameterFromType != null) {
          return lambdaParameterFromType;
        }
      }
    } else {
      PsiType lambdaParameterFromType = LambdaUtil.getLambdaParameterFromType(type, parameterIndex);
      if (lambdaParameterFromType != null) {
        return lambdaParameterFromType;
      }
    }
    return null;
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    dropCaches();
  }

  private void dropCaches() {
    myCachedType = null;
    myCachedName = null;
  }

  @Override
  protected Object clone() {
    PsiParameterImpl clone = (PsiParameterImpl)super.clone();
    clone.dropCaches();

    return clone;
  }

  @Override
  public final @NotNull String getName() {
    String name = myCachedName;
    if (name == null) {
      PsiParameterStub stub = getGreenStub();
      if (stub == null) {
        name = getNameIdentifier().getText();
      }
      else {
        name = stub.getName();
      }
    }
    myCachedName = name;
    return name;
  }

  @Override
  public final PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  @Override
  public final @NotNull PsiIdentifier getNameIdentifier() {
    return PsiTreeUtil.getRequiredChildOfType(this, PsiIdentifier.class);
  }

  @Override
  public @NotNull CompositeElement getNode() {
    return (CompositeElement)super.getNode();
  }

  @Override
  public @NotNull PsiType getType() {
    PsiParameterStub stub = getStub();
    if (stub != null) {
      PsiType type = myCachedType;
      if (type == null) {
        type = JavaSharedImplUtil.createTypeFromStub(this, stub.getType());
        myCachedType = type;
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
    PsiElement parent = getParent();
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
  public @NotNull PsiModifierList getModifierList() {
    return getRequiredStubOrPsiChild(JavaStubElementTypes.MODIFIER_LIST, PsiModifierList.class);
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
  public @NotNull PsiElement getDeclarationScope() {
    PsiElement parent = getParent();
    if (parent == null) return this;

    if (parent instanceof PsiParameterList) {
      return parent.getParent();
    }
    if (parent instanceof PsiForeachStatementBase) {
      return parent;
    }
    if (parent instanceof PsiCatchSection) {
      return parent;
    }

    PsiElement[] children = parent.getChildren();
    for (int i = 0; i < children.length; i++) {
      if (children[i].equals(this)) {
        for (int j = i + 1; j < children.length; j++) {
          if (children[j] instanceof PsiCodeBlock) return children[j];
        }
        break;
      }
    }

    StringBuilder ancestors = new StringBuilder();
    for (PsiElement e = parent; e != null; e = e.getParent()) ancestors.append(' ').append(e);
    throw new IllegalStateException("Parameter: " + this + "; siblings: " + Arrays.asList(children) + "; ancestors:" + ancestors);
  }

  @Override
  public boolean isVarArgs() {
    PsiParameterStub stub = getGreenStub();
    if (stub != null) {
      return stub.isParameterTypeEllipsis();
    }

    myCachedType = null;
    PsiTypeElement typeElement = getTypeElement();
    return typeElement != null && SourceTreeToPsiMap.psiToTreeNotNull(typeElement).findChildByType(JavaTokenType.ELLIPSIS) != null;
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProviders.getItemPresentation(this);
  }

  @Override
  public Icon getElementIcon(int flags) {
    RowIcon baseIcon = IconManager.getInstance().createLayeredIcon(this, IconManager.getInstance().getPlatformIcon(PlatformIcons.Parameter), 0);
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }

  @Override
  public @NotNull SearchScope getUseScope() {
    if (isUnnamed()) {
      return LocalSearchScope.EMPTY;
    }
    PsiElement declarationScope = getDeclarationScope();
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
