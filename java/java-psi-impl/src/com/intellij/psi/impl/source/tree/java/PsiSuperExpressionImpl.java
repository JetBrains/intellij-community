// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiSuperExpressionImpl extends ExpressionPsiElement implements PsiSuperExpression, Constants {
  private static final Logger LOG = Logger.getInstance(PsiSuperExpressionImpl.class);

  public PsiSuperExpressionImpl() {
    super(SUPER_EXPRESSION);
  }

  @Override
  public PsiJavaCodeReferenceElement getQualifier() {
    return (PsiJavaCodeReferenceElement)findChildByRoleAsPsiElement(ChildRole.QUALIFIER);
  }

  @Override
  public PsiType getType() {
    final PsiJavaCodeReferenceElement qualifier = getQualifier();
    if (qualifier != null) {
      final PsiElement aClass = qualifier.resolve();
      if (!(aClass instanceof PsiClass)) return null;
      return getSuperType((PsiClass)aClass, PsiUtil.isAvailable(JavaFeature.EXTENSION_METHODS, this));
    }

    for (PsiElement scope = getContext(); scope != null; scope = scope.getContext()) {
      if (scope instanceof PsiClass) {
        final PsiClass aClass = (PsiClass)scope;
        return getSuperType(aClass, false);
      }
      if (scope instanceof PsiExpressionList && scope.getParent() instanceof PsiAnonymousClass) {
        //noinspection AssignmentToForLoopParameter
        scope = scope.getParent();
      }
      else if (scope instanceof JavaCodeFragment) {
        PsiType fragmentSuperType = ((JavaCodeFragment)scope).getSuperType();
        if (fragmentSuperType != null) return fragmentSuperType;
      }
    }

    return null;
  }

  private @Nullable PsiType getSuperType(PsiClass aClass, boolean checkImmediateSuperInterfaces) {
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName())) return null;

    PsiClass containingClass = checkImmediateSuperInterfaces ? PsiTreeUtil.getContextOfType(this, PsiClass.class) : null;
    if (containingClass != null) {
      final PsiClassType[] superTypes;
      if (containingClass.isInterface()) {
        superTypes = containingClass.getExtendsListTypes();
      }
      else if (containingClass instanceof PsiAnonymousClass) {
        if (PsiTreeUtil.isAncestor(((PsiAnonymousClass)containingClass).getArgumentList(), this, true)) {
          containingClass = PsiTreeUtil.getContextOfType(containingClass, PsiClass.class, true);
          superTypes = containingClass != null ? containingClass.getSuperTypes() : PsiClassType.EMPTY_ARRAY;
        }
        else {
          superTypes = new PsiClassType[]{((PsiAnonymousClass)containingClass).getBaseClassType()};
        }
      }
      else {
        superTypes = containingClass.getImplementsListTypes();
      }

      for (PsiClassType superType : superTypes) {
        final PsiClass superClass = superType.resolve();
        if (superClass != null && superClass.isInterface() && aClass.equals(superClass)) return superType;
      }
    }

    if (aClass.isInterface()) {
      return PsiType.getJavaLangObject(getManager(), getResolveScope());
    }

    if (aClass instanceof PsiAnonymousClass) {
      final PsiClassType baseClassType = ((PsiAnonymousClass)aClass).getBaseClassType();
      final PsiClass psiClass = baseClassType.resolve();
      return psiClass != null && !psiClass.isInterface() ? baseClassType : PsiType.getJavaLangObject(getManager(), getResolveScope());
    }

    final PsiClassType[] superTypes = aClass.getExtendsListTypes();
    return superTypes.length == 0 ? PsiType.getJavaLangObject(getManager(), getResolveScope()) : superTypes[0];
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      case ChildRole.QUALIFIER:
        return getFirstChildNode().getElementType() == JAVA_CODE_REFERENCE ? getFirstChildNode() : null;

      case ChildRole.DOT:
        return findChildByType(DOT);

      case ChildRole.SUPER_KEYWORD:
        return getLastChildNode();

      default:
        return null;
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JAVA_CODE_REFERENCE) {
      return ChildRole.QUALIFIER;
    }
    else if (i == DOT) {
      return ChildRole.DOT;
    }
    else if (i == SUPER_KEYWORD) {
      return ChildRole.SUPER_KEYWORD;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitSuperExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiSuperExpression:" + getText();
  }
}
