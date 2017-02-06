/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.JavaLookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.DeclarationSearchUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * @author Konstantin Bulenkov
 */
public class JavaLangClassMemberReference extends PsiReferenceBase<PsiLiteralExpression> implements InsertHandler<LookupElement> {
  private static final String FIELD = "getField";
  private static final String DECLARED_FIELD = "getDeclaredField";
  private static final String METHOD = "getMethod";
  private static final String DECLARED_METHOD = "getDeclaredMethod";

  private static final RecursionGuard ourGuard = RecursionManager.createGuard("JavaLangClassMemberReference");
  private final PsiExpression myContext;

  public JavaLangClassMemberReference(@NotNull PsiLiteralExpression literal, @NotNull PsiExpression context) {
    super(literal);
    myContext = context;
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return element;
  }

  @Override
  public PsiElement resolve() {
    Object value = myElement.getValue();
    if (value instanceof String) {
      final String name = (String)value;
      final String type = getMemberType();

      if (type != null) {
        final PsiClass psiClass = getPsiClass();
        if (psiClass != null) {
          switch (type) {

            case FIELD: {
              return psiClass.findFieldByName(name, true);
            }

            case DECLARED_FIELD: {
              PsiField field = psiClass.findFieldByName(name, false);
              return isReachable(field, psiClass) ? field : null;
            }

            case METHOD: {
              final PsiMethod[] methods = psiClass.findMethodsByName(name, true);
              final PsiMethod publicMethod = ContainerUtil.find(methods, method -> isRegularMethod(method) && isPublic(method));
              if (publicMethod != null) {
                return publicMethod;
              }
              return ContainerUtil.find(methods, method -> isRegularMethod(method));
            }

            case DECLARED_METHOD: {
              final PsiMethod[] methods = psiClass.findMethodsByName(name, false);
              return ContainerUtil.find(methods, method -> isRegularMethod(method) && isReachable(method, psiClass));
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private PsiClass getPsiClass() {
    return getPsiClass(myContext);
  }

  @Nullable
  private static PsiClass getPsiClass(PsiExpression context) {
    context = ParenthesesUtils.stripParentheses(context);
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
          return ourGuard.doPreventingRecursion(resolved, false, () -> getPsiClass(definition));
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

  private static boolean isJavaLangClass(PsiClass aClass) {
    return aClass != null && CommonClassNames.JAVA_LANG_CLASS.equals(aClass.getQualifiedName());
  }

  private static boolean isJavaLangObject(PsiClass aClass) {
    return aClass != null && CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName());
  }

  @Nullable
  private String getMemberType() {
    final PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType(myElement, PsiMethodCallExpression.class);
    return methodCall != null ? methodCall.getMethodExpression().getReferenceName() : null;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    final String type = getMemberType();
    if (type != null) {
      final PsiClass psiClass = getPsiClass();
      if (psiClass != null) {
        switch (type) {

          case DECLARED_FIELD:
            return psiClass.getFields();

          case FIELD:
            return ContainerUtil.filter(psiClass.getAllFields(), field -> isReachable(field, psiClass)).toArray();

          case DECLARED_METHOD:
            return Arrays.stream(psiClass.getMethods())
              .filter(method -> isRegularMethod(method))
              .map(this::lookupMethod)
              .toArray();

          case METHOD:
            return Arrays.stream(psiClass.getAllMethods())
              .filter(method -> isRegularMethod(method) && isReachable(method, psiClass) && !isJavaLangObject(method.getContainingClass()))
              .map(this::lookupMethod)
              .toArray();
        }
      }
    }
    return EMPTY_ARRAY;
  }

  @NotNull
  private LookupElementBuilder lookupMethod(PsiMethod method) {
    return JavaLookupElementBuilder.forMethod(method, PsiSubstitutor.EMPTY).withInsertHandler(this);
  }

  @Override
  public void handleInsert(InsertionContext context, LookupElement item) {
    final Object object = item.getObject();
    if (object instanceof PsiMethod) {
      final PsiElement newElement = PsiUtilCore.getElementAtOffset(context.getFile(), context.getStartOffset());
      final int start = newElement.getTextRange().getEndOffset();
      final PsiElement params = newElement.getParent().getParent();
      final int end = params.getTextRange().getEndOffset() - 1;
      final String types = getMethodTypes((PsiMethod)object);
      context.getDocument().replaceString(start, end, types);
      context.commitDocument();
      final PsiElement firstParam = PsiUtilCore.getElementAtOffset(context.getFile(), context.getStartOffset());
      final PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType(firstParam, PsiMethodCallExpression.class);
      if (methodCall != null) {
        JavaCodeStyleManager.getInstance(context.getProject()).shortenClassReferences(methodCall);
      }
    }
  }

  @Contract("null -> false")
  private static boolean isRegularMethod(PsiMethod method) {
    return method != null && !method.isConstructor();
  }

  /**
   * Non-public members of superclass/superinterface can't be obtained via reflection, they need to be filtered out.
   */
  @Contract("null, _ -> false")
  private static boolean isReachable(PsiMember member, PsiClass psiClass) {
    return member != null && (member.getContainingClass() == psiClass || isPublic(member));
  }

  private static boolean isPublic(@NotNull PsiMember member) {
    return member.hasModifierProperty(PsiModifier.PUBLIC);
  }

  private static String getMethodTypes(@NotNull PsiMethod method) {
    final StringBuilder buf = new StringBuilder();
    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      PsiType type = TypeConversionUtil.erasure(parameter.getType());
      if (type instanceof PsiEllipsisType) {
        type = new PsiArrayType(((PsiEllipsisType)type).getComponentType());
      }
      buf.append(", ").append(type.getPresentableText()).append(".class");
    }
    return buf.toString();
  }
}
