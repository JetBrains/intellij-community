// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.util.Comparing;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.source.resolve.JavaResolveCache;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
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
    if (list == null) {
      LOG.error("Invalid PSI for'" + getText() + ". Parent:" + DebugUtil.psiToString(getParent(), false));
    }
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
  public int getChildRole(@NotNull ASTNode child) {
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

  @Override
  public String toString() {
    return "PsiMethodCallExpression:" + getText();
  }

  private static final TypeEvaluator ourTypeEvaluator = new TypeEvaluator();

  private static class TypeEvaluator implements Function<PsiMethodCallExpression, PsiType> {
    @Override
    @Nullable
    public PsiType fun(final PsiMethodCallExpression call) {
      PsiReferenceExpression methodExpression = call.getMethodExpression();
      final JavaResolveResult[] results = methodExpression.multiResolve(false);
      PsiFile file = call.getContainingFile();
      LanguageLevel languageLevel = PsiUtil.getLanguageLevel(file);

      final PsiElement callParent = PsiUtil.skipParenthesizedExprUp(call.getParent());
      final PsiExpressionList parentArgList;
      if (languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
        parentArgList = callParent instanceof PsiConditionalExpression && !PsiPolyExpressionUtil.isPolyExpression((PsiExpression)callParent)
                        ? null : PsiTreeUtil.getParentOfType(call, PsiExpressionList.class, true, PsiReferenceExpression.class);
      }
      else {
        parentArgList = null;
      }
      final MethodCandidateInfo.CurrentCandidateProperties properties = MethodCandidateInfo.getCurrentMethod(parentArgList);
      final boolean genericMethodCall = properties != null && properties.getInfo().isToInferApplicability();

      PsiType theOnly = null;
      for (int i = 0; i < results.length; i++) {
        final JavaResolveResult candidateInfo = results[i];

        if (genericMethodCall && PsiPolyExpressionUtil.isMethodCallPolyExpression(call, (PsiMethod)candidateInfo.getElement())) {
          if (callParent instanceof PsiAssignmentExpression) {
            return null;
          }
          LOG.error("poly expression evaluation during overload resolution");
        }

        final PsiType type = getResultType(call, methodExpression, candidateInfo, languageLevel);
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

      return PsiClassImplUtil.correctType(theOnly, file.getResolveScope());
    }

    @Nullable
    private static PsiType getResultType(@NotNull PsiMethodCallExpression call,
                                         @NotNull PsiReferenceExpression methodExpression,
                                         @NotNull JavaResolveResult result,
                                         @NotNull final LanguageLevel languageLevel) {
      final PsiMethod method = (PsiMethod)result.getElement();
      if (method == null) return null;

      boolean is15OrHigher = languageLevel.compareTo(LanguageLevel.JDK_1_5) >= 0;
      final PsiType getClassReturnType = PsiTypesUtil.patchMethodGetClassReturnType(call, methodExpression, method,
                                                                                    type -> type != JavaElementType.CLASS && 
                                                                                             //enum can be created inside enum only, no need to mention it here
                                                                                            type != JavaElementType.ANONYMOUS_CLASS
                                                                                    , languageLevel);

      if (getClassReturnType != null) {
        return getClassReturnType;
      }

      PsiType ret = method.getReturnType();
      if (ret == null) return null;
      if (ret instanceof PsiClassType) {
        ret = ((PsiClassType)ret).setLanguageLevel(languageLevel);
      }
      if (is15OrHigher) {
        return captureReturnType(call, method, ret, result, languageLevel);
      }
      return TypeConversionUtil.erasure(ret);
    }
  }

  private static PsiType captureReturnType(PsiMethodCallExpression call,
                                           PsiMethod method,
                                           PsiType ret,
                                           JavaResolveResult result,
                                           LanguageLevel languageLevel) {
    PsiSubstitutor substitutor = result.getSubstitutor();
    PsiType substitutedReturnType = substitutor.substitute(ret);
    if (substitutedReturnType == null) {
      return TypeConversionUtil.erasure(ret);
    }

    if (InferenceSession.wasUncheckedConversionPerformed(call)) {
      // 18.5.2
      // if unchecked conversion was necessary, then this substitution provides the parameter types of the invocation type, 
      // while the return type and thrown types are given by the erasure of m's type (without applying theta').
      //due to https://bugs.openjdk.java.net/browse/JDK-8135087 erasure is called on substitutedReturnType and not on ret type itself as by spec
      return TypeConversionUtil.erasure(substitutedReturnType);
    }

    //15.12.2.6. Method Invocation Type
    // If unchecked conversion was necessary for the method to be applicable, 
    // the parameter types of the invocation type are the parameter types of the method's type,
    // and the return type and thrown types are given by the erasures of the return type and thrown types of the method's type.
    if (((!languageLevel.isAtLeast(LanguageLevel.JDK_1_8) || call.getTypeArguments().length > 0) && method.hasTypeParameters() ||
         !method.hasTypeParameters() && JavaVersionService.getInstance().isAtLeast(call, JavaSdkVersion.JDK_1_8)) &&
        result instanceof MethodCandidateInfo && ((MethodCandidateInfo)result).isApplicable()) {
      final PsiType[] args = call.getArgumentList().getExpressionTypes();
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      final boolean varargs = ((MethodCandidateInfo)result).getApplicabilityLevel() == MethodCandidateInfo.ApplicabilityLevel.VARARGS;
      for (int i = 0; i < args.length; i++) {
        final PsiType parameterType = substitutor.substitute(PsiTypesUtil.getParameterType(parameters, i, varargs));
        final PsiType expressionType = args[i];
        if (expressionType != null && parameterType != null && JavaGenericsUtil.isRawToGeneric(parameterType, expressionType)) {
          return TypeConversionUtil.erasure(substitutedReturnType);
        }
      }
    }

    if (PsiUtil.isRawSubstitutor(method, substitutor)) {
      final PsiType returnTypeErasure = TypeConversionUtil.erasure(ret);
      if (Comparing.equal(TypeConversionUtil.erasure(substitutedReturnType), returnTypeErasure)) {
        return returnTypeErasure;
      }
    }
    return PsiUtil.captureToplevelWildcards(substitutedReturnType, call);
  }
}

