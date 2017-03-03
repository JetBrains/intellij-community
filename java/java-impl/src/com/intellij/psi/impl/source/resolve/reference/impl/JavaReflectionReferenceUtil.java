/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.JavaLookupElementBuilder;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.DeclarationSearchUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * @author Pavel.Dolgov
 */
class JavaReflectionReferenceUtil {
  private static final RecursionGuard ourGuard = RecursionManager.createGuard("JavaLangClassMemberReference");

  @Nullable
  static PsiClass getReflectiveClass(@Nullable PsiExpression context) {
    context = ParenthesesUtils.stripParentheses(context);
    if (context == null) {
      return null;
    }
    if (context instanceof PsiClassObjectAccessExpression) { // special case for JDK 1.4
      PsiTypeElement operand = ((PsiClassObjectAccessExpression)context).getOperand();
      return PsiTypesUtil.getPsiClass(operand.getType());
    }

    if (context instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)context;
      final String methodReferenceName = methodCall.getMethodExpression().getReferenceName();
      if ("forName".equals(methodReferenceName)) {
        final PsiMethod method = methodCall.resolveMethod();
        if (method != null && isJavaLangClass(method.getContainingClass())) {
          final PsiExpression[] expressions = methodCall.getArgumentList().getExpressions();
          if (expressions.length == 1) {
            PsiExpression argument = ParenthesesUtils.stripParentheses(expressions[0]);
            if (argument instanceof PsiReferenceExpression) {
              argument = findVariableDefinition(((PsiReferenceExpression)argument));
            }
            final Object value = JavaConstantExpressionEvaluator.computeConstantExpression(argument, false);
            if (value instanceof String) {
              final Project project = context.getProject();
              return JavaPsiFacade.getInstance(project).findClass((String)value, GlobalSearchScope.allScope(project));
            }
          }
        }
      }
      else if ("getClass".equals(methodReferenceName) && methodCall.getArgumentList().getExpressions().length == 0) {
        final PsiMethod method = methodCall.resolveMethod();
        if (method != null && isJavaLangObject(method.getContainingClass())) {
          final PsiExpression qualifier = ParenthesesUtils.stripParentheses(methodCall.getMethodExpression().getQualifierExpression());
          if (qualifier instanceof PsiReferenceExpression) {
            final PsiExpression definition = findVariableDefinition((PsiReferenceExpression)qualifier);
            if (definition != null) {
              final PsiClass actualClass = PsiTypesUtil.getPsiClass(definition.getType());
              if (actualClass != null) {
                return actualClass;
              }
            }
          }
          //TODO type of the qualifier may be a supertype of the actual value - need to compute the type of the actual value
          // otherwise getDeclaredField and getDeclaredMethod may work not reliably
          if (qualifier != null) {
            return PsiTypesUtil.getPsiClass(qualifier.getType());
          }
        }
      }
    }
    PsiType type = context.getType();
    if (type instanceof PsiClassType) {
      PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
      if (!isJavaLangClass(resolveResult.getElement())) return null;
      final PsiTypeParameter[] parameters = resolveResult.getElement().getTypeParameters();
      if (parameters.length == 1) {
        PsiType typeArgument = resolveResult.getSubstitutor().substitute(parameters[0]);
        if (typeArgument instanceof PsiCapturedWildcardType) {
          typeArgument = ((PsiCapturedWildcardType)typeArgument).getUpperBound();
        }
        final PsiClass argumentClass = PsiTypesUtil.getPsiClass(typeArgument);
        if (argumentClass != null && !isJavaLangObject(argumentClass)) {
          return argumentClass;
        }
      }
    }
    if (context instanceof PsiReferenceExpression) {
      final PsiElement resolved = ((PsiReferenceExpression)context).resolve();
      if (resolved instanceof PsiVariable) {
        final PsiExpression definition = findVariableDefinition((PsiReferenceExpression)context, (PsiVariable)resolved);
        if (definition != null) {
          return ourGuard.doPreventingRecursion(resolved, false, () -> getReflectiveClass(definition));
        }
      }
    }
    return null;
  }

  private static PsiExpression findVariableDefinition(@NotNull PsiReferenceExpression referenceExpression) {
    final PsiElement resolved = referenceExpression.resolve();
    return resolved instanceof PsiVariable ? findVariableDefinition(referenceExpression, (PsiVariable)resolved) : null;
  }

  private static PsiExpression findVariableDefinition(@NotNull PsiReferenceExpression referenceExpression, @NotNull PsiVariable variable) {
    if (variable.hasModifierProperty(PsiModifier.FINAL)) {
      final PsiExpression initializer = variable.getInitializer();
      if (initializer != null) {
        return initializer;
      }
    }
    return DeclarationSearchUtils.findDefinition(referenceExpression, variable);
  }

  static boolean isJavaLangClass(@Nullable PsiClass aClass) {
    return aClass != null && CommonClassNames.JAVA_LANG_CLASS.equals(aClass.getQualifiedName());
  }

  static boolean isJavaLangObject(@Nullable PsiClass aClass) {
    return aClass != null && CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName());
  }

  @Contract("null -> false")
  static boolean isRegularMethod(@Nullable PsiMethod method) {
    return method != null && !method.isConstructor();
  }

  static boolean isPublic(@NotNull PsiMember member) {
    return member.hasModifierProperty(PsiModifier.PUBLIC);
  }

  @NotNull
  static String getParameterTypesText(@NotNull PsiMethod method) {
    return Arrays.stream(method.getParameterList().getParameters())
      .map(parameter -> TypeConversionUtil.erasure(parameter.getType()))
      .map(type -> (type instanceof PsiEllipsisType) ? new PsiArrayType(((PsiEllipsisType)type).getComponentType()) : type)
      .map(type -> type.getPresentableText() + ".class")
      .collect(Collectors.joining(", "));
  }

  static void shortenArgumentsClassReferences(@NotNull InsertionContext context) {
    final PsiElement firstParam = PsiUtilCore.getElementAtOffset(context.getFile(), context.getStartOffset());
    final PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType(firstParam, PsiMethodCallExpression.class);
    if (methodCall != null) {
      JavaCodeStyleManager.getInstance(context.getProject()).shortenClassReferences(methodCall.getArgumentList());
    }
  }

  @NotNull
  static LookupElement withPriority(@NotNull LookupElement lookupElement, boolean hasPriority) {
    return hasPriority ? lookupElement : PrioritizedLookupElement.withPriority(lookupElement, -1);
  }

  @NotNull
  static LookupElement withPriority(@NotNull LookupElement lookupElement, int priority) {
    return priority == 0 ? lookupElement : PrioritizedLookupElement.withPriority(lookupElement, priority);
  }

  static int getMethodSortOrder(@NotNull PsiMethod method) {
    return isJavaLangObject(method.getContainingClass()) ? 1 : isPublic(method) ? -1 : 0;
  }

  @Nullable
  static String getMemberType(@Nullable PsiElement element) {
    final PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
    return methodCall != null ? methodCall.getMethodExpression().getReferenceName() : null;
  }

  @NotNull
  static LookupElement lookupField(@NotNull PsiField field) {
    return JavaLookupElementBuilder.forField(field);
  }

  static void replaceText(@NotNull InsertionContext context, @NotNull String text) {
    final PsiElement newElement = PsiUtilCore.getElementAtOffset(context.getFile(), context.getStartOffset());
    final int start = newElement.getTextRange().getEndOffset();
    final PsiElement params = newElement.getParent().getParent();
    final int end = params.getTextRange().getEndOffset() - 1;

    context.getDocument().replaceString(start, end, text);
    context.commitDocument();
    shortenArgumentsClassReferences(context);
  }
}
