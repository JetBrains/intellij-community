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
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * @author Konstantin Bulenkov
 */
public class JavaLangClassMemberReference extends PsiReferenceBase<PsiLiteralExpression> implements InsertHandler<LookupElement> {
  private static final RecursionGuard ourGuard = RecursionManager.createGuard("JavaLangClassMemberReference");
  private final PsiExpression myContext;

  public JavaLangClassMemberReference(PsiLiteralExpression literal, PsiExpression context) {
    super(literal);
    myContext = context;
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return element;
  }

  @Override
  public PsiElement resolve() {
    final String name =  (String)getElement().getValue();
    final Type type = getType();

    if (type != null) {
      final PsiClass psiClass = getPsiClass();
      if (psiClass != null) {
        switch (type) {

          case FIELD: {
            PsiField field = psiClass.findFieldByName(name, true);
            return isPublic(field) ? field : null;
          }

          case DECLARED_FIELD:
            return psiClass.findFieldByName(name, false);

          case METHOD: {
            final PsiMethod[] methods = psiClass.findMethodsByName(name, true);
            return ContainerUtil.find(methods, JavaLangClassMemberReference::isPublic);
          }

          case DECLARED_METHOD: {
            final PsiMethod[] methods = psiClass.findMethodsByName(name, false);
            return methods.length == 0 ? null : methods[0];
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

    PsiType type = context.getType();
    if (type instanceof PsiClassType) {
      PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
      if (!isJavaLangClass(resolveResult.getElement())) return null;
      PsiTypeParameter[] parameters = resolveResult.getElement().getTypeParameters();
      if (parameters.length == 1) {
        PsiType typeArgument = resolveResult.getSubstitutor().substitute(parameters[0]);
        PsiClass argumentClass = PsiTypesUtil.getPsiClass(typeArgument);
        if (argumentClass != null) return argumentClass;
      }
    }
    if (context instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression methodCall = (PsiMethodCallExpression)context;
      if ("forName".equals(methodCall.getMethodExpression().getReferenceName())) {
        final PsiMethod method = methodCall.resolveMethod();
        if (method != null && isJavaLangClass(method.getContainingClass())) {
          final PsiExpression[] expressions = methodCall.getArgumentList().getExpressions();
          if (expressions.length == 1 && expressions[0] instanceof PsiLiteralExpression) {
            final Object value = ((PsiLiteralExpression)expressions[0]).getValue();
            if (value instanceof String) {
              final Project project = context.getProject();
              return JavaPsiFacade.getInstance(project).findClass(String.valueOf(value), GlobalSearchScope.allScope(project));
            }
          }
        }
      }
    }
    if (context instanceof PsiReferenceExpression) {
      PsiElement resolved = ((PsiReferenceExpression)context).resolve();
      if (resolved instanceof PsiVariable) {
        PsiExpression initializer = getInitializer((PsiVariable)resolved, context);
        if (initializer != null) {
          return ourGuard.doPreventingRecursion(resolved, false, () -> getPsiClass(initializer));
        }
      }
    }
    return null;
  }

  private static PsiExpression getInitializer(@NotNull PsiVariable variable, @NotNull PsiExpression usage) {
    PsiExpression initializer = variable.getInitializer();
    if (initializer != null) {
      if (variable.hasModifierProperty(PsiModifier.FINAL)) {
        return initializer;
      }
      if (variable instanceof PsiLocalVariable) {
        PsiDeclarationStatement declarationStatement = ObjectUtils.tryCast(variable.getParent(), PsiDeclarationStatement.class);
        if (declarationStatement != null) {
          PsiStatement usageStatement = PsiTreeUtil.getParentOfType(usage, PsiStatement.class);
          if (PsiTreeUtil.getNextSiblingOfType(declarationStatement, PsiStatement.class) == usageStatement) {
            return initializer;
          }
          PsiElement scope = PsiUtil.getVariableCodeBlock(variable, usage);
          if (scope != null && HighlightControlFlowUtil.isEffectivelyFinal(variable, scope, null)) {
            return initializer;
          }
        }
      }
    }
    PsiStatement usageStatement = PsiTreeUtil.getParentOfType(usage, PsiStatement.class);
    if (usageStatement != null) {
      return getAssignedVisibleInUsage(variable, usageStatement);
    }
    // TODO: handle other initializations and assignments where the class can be resolved unambiguously
    return null;
  }

  @Nullable
  private static PsiExpression getAssignedVisibleInUsage(@NotNull PsiVariable variable, PsiStatement usageStatement) {
    PsiStatement previousStatement = PsiTreeUtil.getPrevSiblingOfType(usageStatement, PsiStatement.class);
    if (previousStatement instanceof PsiExpressionStatement) {
      PsiExpression expression = ((PsiExpressionStatement)previousStatement).getExpression();
      if (expression instanceof PsiAssignmentExpression &&
          JavaTokenType.EQ.equals(((PsiAssignmentExpression)expression).getOperationTokenType())) {
        PsiExpression lExpression = ((PsiAssignmentExpression)expression).getLExpression();
        lExpression = ParenthesesUtils.stripParentheses(lExpression);
        if (lExpression instanceof PsiReferenceExpression && ((PsiReferenceExpression)lExpression).resolve() == variable) {
          return ((PsiAssignmentExpression)expression).getRExpression();
        }
      }
    }
    return null;
  }

  private static boolean isJavaLangClass(PsiClass aClass) {
    return aClass != null && CommonClassNames.JAVA_LANG_CLASS.equals(aClass.getQualifiedName());
  }

  private static boolean isJavaLangObject(PsiClass aClass) {
    return aClass != null && CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName());
  }

  @Nullable
  private Type getType() {
    PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType(myElement, PsiMethodCallExpression.class);
    if (methodCall != null) {
      String name = methodCall.getMethodExpression().getReferenceName();
      return Type.fromString(name);
    }
    return null;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    final Type type = getType();
    if (type != null) {
      final PsiClass psiClass = getPsiClass();
      if (psiClass != null) {
        switch (type) {

          case DECLARED_FIELD:
            return psiClass.getFields();

          case FIELD:
            return ContainerUtil.filter(psiClass.getAllFields(), JavaLangClassMemberReference::isPublic).toArray();

          case DECLARED_METHOD:
            return Arrays.stream(psiClass.getMethods())
              .filter(method -> !method.isConstructor())
              .map(this::lookupMethod)
              .toArray();

          case METHOD:
            return Arrays.stream(psiClass.getAllMethods())
              .filter(method -> isPublic(method) && !method.isConstructor() && !isJavaLangObject(method.getContainingClass()))
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

  private static boolean isPublic(final PsiMember psiField) {
    return psiField.hasModifierProperty(PsiModifier.PUBLIC);
  }

  private static String getMethodTypes(PsiMethod method) {
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


  enum Type {
    FIELD, DECLARED_FIELD, METHOD, DECLARED_METHOD;

    @Nullable
    static Type fromString(String s) {
      if ("getField".equals(s)) return FIELD;
      if ("getDeclaredField".equals(s)) return DECLARED_FIELD;
      if ("getMethod".equals(s)) return METHOD;
      if ("getDeclaredMethod".equals(s)) return DECLARED_METHOD;
      return null;
    }
  }
}
