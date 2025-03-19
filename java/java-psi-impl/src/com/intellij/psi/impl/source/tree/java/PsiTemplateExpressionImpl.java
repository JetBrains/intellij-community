// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.util.Ref;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class PsiTemplateExpressionImpl extends ExpressionPsiElement implements PsiTemplateExpression {

  public PsiTemplateExpressionImpl() {
    super(JavaElementType.TEMPLATE_EXPRESSION);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitTemplateExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public @Nullable PsiExpression getProcessor() {
    final PsiElement child = getFirstChild();
    return child instanceof PsiExpression ? (PsiExpression)child : null;
  }

  @Override
  public @NotNull ArgumentType getArgumentType() {
    final PsiElement lastChild = getLastChild();
    if (lastChild instanceof PsiTemplate) {
      return ArgumentType.TEMPLATE;
    }
    else if (!(lastChild instanceof PsiLiteralExpression)) {
      throw new AssertionError("literal expression expected, got " + lastChild.getClass());
    }
    return ((PsiLiteralExpression)lastChild).isTextBlock() ? ArgumentType.TEXT_BLOCK : ArgumentType.STRING_LITERAL;
  }

  @Override
  public @Nullable PsiTemplate getTemplate() {
    final PsiElement lastChild = getLastChild();
    return lastChild instanceof PsiTemplate ? (PsiTemplate)lastChild : null;
  }

  @Override
  public @Nullable PsiLiteralExpression getLiteralExpression() {
    final PsiElement lastChild = getLastChild();
    return lastChild instanceof PsiLiteralExpression ? (PsiLiteralExpression)lastChild : null;
  }
  
  @Override
  public @NotNull JavaResolveResult resolveMethodGenerics() {
    final PsiExpression processor = getProcessor();
    if (processor == null) return JavaResolveResult.EMPTY;
    final PsiType type = processor.getType();
    if (type == null) return JavaResolveResult.EMPTY;
    PsiMethod method = findBaseProcessMethod(type);
    if (method == null) return JavaResolveResult.EMPTY;
    for (PsiClassType classType : PsiTypesUtil.getClassTypeComponents(type)) {
      if (!TypeConversionUtil.isAssignable(type, classType)) continue;
      final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      final PsiClass aClass = resolveResult.getElement();
      if (aClass == null) continue;
      PsiMethod foundMethod = aClass.findMethodBySignature(method, true);
      if (foundMethod == null) continue;
      PsiClass methodContainingClass = foundMethod.getContainingClass();
      if (methodContainingClass == null) continue;
      final PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(methodContainingClass, aClass, resolveResult.getSubstitutor());
      if (substitutor == null) continue;
      return new MethodCandidateInfo(foundMethod, substitutor, false, false, this, null, null, null);
    }
    return JavaResolveResult.EMPTY;
  }

  @Override
  public @Nullable PsiMethod resolveMethod() {
    return ObjectUtils.tryCast(resolveMethodGenerics(), PsiMethod.class);
  }

  private static PsiMethod findBaseProcessMethod(PsiType type) {
    Ref<PsiMethod> refMethod = Ref.create();
    InheritanceUtil.processSuperTypes(type, true, superType -> {
      if (!(superType instanceof PsiClassType)) return true;
      PsiClassType.ClassResolveResult result = ((PsiClassType)superType).resolveGenerics();
      PsiClass superClass = result.getElement();
      if (superClass == null) return true;
      if (!CommonClassNames.JAVA_LANG_STRING_TEMPLATE_PROCESSOR.equals(superClass.getQualifiedName())) return true;
      PsiMethod[] processMethods = superClass.findMethodsByName("process", false);
      for (PsiMethod method : processMethods) {
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length == 1) {
          PsiType parameterType = parameters[0].getType();
          if (parameterType instanceof PsiClassType && parameterType.equalsToText(CommonClassNames.JAVA_LANG_STRING_TEMPLATE)) {
            refMethod.set(method);
            return false;
          }
        }
      }
      return true;
    });
    return refMethod.get();
  }

  @Override
  public @Nullable PsiType getType() {
    if (!PsiUtil.getLanguageLevel(this).equals(LanguageLevel.JDK_21_PREVIEW)) {
      JavaResolveResult result = resolveMethodGenerics();
      PsiMethod method = (PsiMethod)result.getElement();
      if (method != null) {
        return result.getSubstitutor().substitute(method.getReturnType());
      }
    }
    final PsiExpression processor = getProcessor();
    if (processor == null) return null;
    PsiType type = processor.getType();
    if (type == null) return null;
    for (PsiClassType classType : PsiTypesUtil.getClassTypeComponents(type)) {
      PsiType substituted = PsiUtil.substituteTypeParameter(classType, CommonClassNames.JAVA_LANG_STRING_TEMPLATE_PROCESSOR, 0, false);
      if (substituted != null) {
        return PsiUtil.captureToplevelWildcards(substituted, this);
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return "PsiTemplateExpression";
  }
}