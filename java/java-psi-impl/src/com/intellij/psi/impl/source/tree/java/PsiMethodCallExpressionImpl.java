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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.resolve.JavaResolveCache;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiMethodCallExpressionImpl extends ExpressionPsiElement implements PsiMethodCallExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl");

  public PsiMethodCallExpressionImpl() {
    super(JavaElementType.METHOD_CALL_EXPRESSION);
  }

  @Override
  public PsiType getType() {
    return JavaResolveCache.getInstance(getProject()).getType(this, ourTypeEvaluator);
  }

  @Override
  public PsiMethod resolveMethod() {
    return (PsiMethod)getMethodExpression().resolve();
  }

  @Override
  @NotNull
  public JavaResolveResult resolveMethodGenerics() {
    return getMethodExpression().advancedResolve(false);
  }

  @Override
  public void removeChild(@NotNull ASTNode child) {
    if (child == getArgumentList()) {
      LOG.error("Cannot delete argument list since it will break contract on argument list notnullity");
    }
    super.removeChild(child);
  }

  @Override
  @NotNull
  public PsiReferenceParameterList getTypeArgumentList() {
    PsiReferenceExpression expression = getMethodExpression();
    PsiReferenceParameterList result = expression.getParameterList();
    if (result != null) return result;
    LOG.error("Invalid method call expression. Children:\n" + DebugUtil.psiTreeToString(expression, false));
    return result;
  }

  @Override
  @NotNull
  public PsiType[] getTypeArguments() {
    return getMethodExpression().getTypeParameters();
  }

  @Override
  @NotNull
  public PsiReferenceExpression getMethodExpression() {
    return (PsiReferenceExpression)findChildByRoleAsPsiElement(ChildRole.METHOD_EXPRESSION);
  }

  @Override
  @NotNull
  public PsiExpressionList getArgumentList() {
    PsiExpressionList list = (PsiExpressionList)findChildByRoleAsPsiElement(ChildRole.ARGUMENT_LIST);
    if (list != null) return list;
    LOG.error("Invalid PSI. Children:" + DebugUtil.psiToString(this, false));
    return list;
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      default:
        return null;

      case ChildRole.METHOD_EXPRESSION:
        return getFirstChildNode();

      case ChildRole.ARGUMENT_LIST:
        return findChildByType(JavaElementType.EXPRESSION_LIST);
    }
  }

  @Override
  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaElementType.EXPRESSION_LIST) {
      return ChildRole.ARGUMENT_LIST;
    }
    else {
      if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return ChildRole.METHOD_EXPRESSION;
      }
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitMethodCallExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiMethodCallExpression:" + getText();
  }

  private static final TypeEvaluator ourTypeEvaluator = new TypeEvaluator();

  private static class TypeEvaluator implements Function<PsiMethodCallExpression, PsiType> {
    @Override
    @Nullable
    public PsiType fun(final PsiMethodCallExpression call) {
      PsiReferenceExpression methodExpression = call.getMethodExpression();
      PsiType theOnly = null;
      final JavaResolveResult[] results = methodExpression.multiResolve(false);
      LanguageLevel languageLevel = PsiUtil.getLanguageLevel(call);
      for (int i = 0; i < results.length; i++) {
        final PsiType type = getResultType(call, methodExpression, results[i], languageLevel);
        if (type == null) {
          return null;
        }

        if (i == 0) {
          theOnly = type;
        }
        else if (!theOnly.equals(type)) {
          return null;
        }
      }

      return theOnly;
    }

    @Nullable
    private static PsiType getResultType(PsiMethodCallExpression call,
                                         PsiReferenceExpression methodExpression,
                                         JavaResolveResult result,
                                         @NotNull final LanguageLevel languageLevel) {
      final PsiMethod method = (PsiMethod)result.getElement();
      if (method == null) return null;

      boolean is15OrHigher = languageLevel.compareTo(LanguageLevel.JDK_1_5) >= 0;
      final PsiType getClassReturnType = PsiTypesUtil.patchMethodGetClassReturnType(call, methodExpression, method,
                                                                                    new Condition<IElementType>() {
                                                                                      @Override
                                                                                      public boolean value(IElementType type) {
                                                                                        return type != JavaElementType.CLASS;
                                                                                      }
                                                                                    }, languageLevel);

      if (getClassReturnType != null) {
        return getClassReturnType;
      }

      PsiType ret = method.getReturnType();
      if (ret == null) return null;
      if (ret instanceof PsiClassType) {
        ret = ((PsiClassType)ret).setLanguageLevel(languageLevel);
      }
      if (is15OrHigher) {
        return captureReturnType(call, method, ret, result.getSubstitutor());
      }
      return TypeConversionUtil.erasure(ret);
    }
  }

  public static PsiType captureReturnType(PsiMethodCallExpression call,
                                          PsiMethod method,
                                          PsiType ret,
                                          PsiSubstitutor substitutor) {
    PsiType substitutedReturnType = substitutor.substitute(ret);
    if (substitutedReturnType == null) return TypeConversionUtil.erasure(ret);
    if (PsiUtil.isRawSubstitutor(method, substitutor)) {
      final PsiType returnTypeErasure = TypeConversionUtil.erasure(ret);
      if (Comparing.equal(TypeConversionUtil.erasure(substitutedReturnType), returnTypeErasure)) {
        return returnTypeErasure;
      }
    }
    PsiType lowerBound = PsiType.NULL;
    if (substitutedReturnType instanceof PsiCapturedWildcardType) {
      lowerBound = ((PsiCapturedWildcardType)substitutedReturnType).getLowerBound();
    } else if (substitutedReturnType instanceof PsiWildcardType) {
      lowerBound = ((PsiWildcardType)substitutedReturnType).getSuperBound();
    }
    if (lowerBound != PsiType.NULL) { //? super
      final PsiClass containingClass = method.getContainingClass();
      final PsiExpression qualifierExpression = call.getMethodExpression().getQualifierExpression();
      final PsiClass childClass = qualifierExpression != null ? PsiUtil.resolveClassInClassTypeOnly(qualifierExpression.getType()) : null;
      if (containingClass != null && childClass != null) {
        final PsiType typeInChildClassTypeParams = TypeConversionUtil.getSuperClassSubstitutor(containingClass, childClass, PsiSubstitutor.EMPTY).substitute(ret);
        final PsiClass substituted = PsiUtil.resolveClassInClassTypeOnly(typeInChildClassTypeParams);
        if (substituted instanceof PsiTypeParameter) {
          final PsiClassType[] extendsListTypes = substituted.getExtendsListTypes();
          if (extendsListTypes.length == 1) {
            return extendsListTypes[0];
          }
        }
      }
    }
    return PsiImplUtil.normalizeWildcardTypeByPosition(substitutedReturnType, call);
  }
}

