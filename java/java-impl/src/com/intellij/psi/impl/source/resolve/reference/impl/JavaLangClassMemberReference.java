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
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.*;

/**
 * @author Konstantin Bulenkov
 */
public class JavaLangClassMemberReference extends PsiReferenceBase<PsiLiteralExpression> implements InsertHandler<LookupElement> {
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
    final Object value = myElement.getValue();
    if (value instanceof String) {
      final String name = (String)value;
      final String type = getMemberType(myElement);

      if (type != null) {
        final ReflectiveClass ownerClass = getOwnerClass();
        if (ownerClass != null) {
          switch (type) {

            case GET_FIELD: {
              return ownerClass.getPsiClass().findFieldByName(name, true);
            }

            case GET_DECLARED_FIELD: {
              final PsiField field = ownerClass.getPsiClass().findFieldByName(name, false);
              return isPotentiallyAccessible(field, ownerClass) ? field : null;
            }

            case GET_METHOD: {
              PsiMethod[] methods = ownerClass.getPsiClass().findMethodsByName(name, true);
              if (methods.length > 1) {
                methods =
                  ContainerUtil.filter(methods, method -> isRegularMethod(method) && isPublic(method))
                    .toArray(PsiMethod.EMPTY_ARRAY);
                if (methods.length > 1) {
                  return findOverloadedMethod(methods);
                }
              }
              return methods.length != 0 ? methods[0] : null;
            }

            case GET_DECLARED_METHOD: {
              PsiMethod[] methods = ownerClass.getPsiClass().findMethodsByName(name, false);
              if (methods.length > 1) {
                methods =
                  ContainerUtil.filter(methods, method -> isRegularMethod(method) && isPotentiallyAccessible(method, ownerClass))
                    .toArray(PsiMethod.EMPTY_ARRAY);
                if (methods.length > 1) {
                  return findOverloadedMethod(methods);
                }
              }
              return methods.length != 0 ? methods[0] : null;
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private ReflectiveClass getOwnerClass() {
    return getReflectiveClass(myContext);
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    final String type = getMemberType(myElement);
    if (type != null) {
      final ReflectiveClass ownerClass = getOwnerClass();
      if (ownerClass != null) {
        switch (type) {

          case GET_DECLARED_FIELD:
            return Arrays.stream(ownerClass.getPsiClass().getFields())
              .filter(field -> field.getName() != null)
              .sorted(Comparator.comparing(PsiField::getName))
              .map(field -> lookupField(field))
              .toArray();

          case GET_FIELD: {
            final Set<String> uniqueNames = new THashSet<>();
            return Arrays.stream(ownerClass.getPsiClass().getAllFields())
              .filter(field -> isPotentiallyAccessible(field, ownerClass) && field.getName() != null && uniqueNames.add(field.getName()))
              .sorted(Comparator.comparingInt((PsiField field) -> isPublic(field) ? 0 : 1).thenComparing(PsiField::getName))
              .map(field -> withPriority(lookupField(field), isPublic(field)))
              .toArray();
          }

          case GET_DECLARED_METHOD:
            return Arrays.stream(ownerClass.getPsiClass().getMethods())
              .filter(method -> isRegularMethod(method))
              .sorted(Comparator.comparing(PsiMethod::getName))
              .map(method -> lookupMethod(method, this))
              .filter(Objects::nonNull)
              .toArray();

          case GET_METHOD: {
            return ownerClass.getPsiClass().getVisibleSignatures()
              .stream()
              .map(MethodSignatureBackedByPsiMethod::getMethod)
              .filter(method -> isRegularMethod(method) && isPotentiallyAccessible(method, ownerClass))
              .sorted(Comparator.comparingInt((PsiMethod method) -> getMethodSortOrder(method)).thenComparing(PsiMethod::getName))
              .map(method -> withPriority(lookupMethod(method, this), -getMethodSortOrder(method)))
              .filter(Objects::nonNull)
              .toArray();
          }
        }
      }
    }
    return EMPTY_ARRAY;
  }


  /**
   * Non-public members of superclass/superinterface can't be obtained via reflection, they need to be filtered out.
   */
  @Contract("null, _ -> false")
  private static boolean isPotentiallyAccessible(PsiMember member, ReflectiveClass psiClass) {
    return member != null && (member.getContainingClass() == psiClass.getPsiClass() || isPublic(member));
  }

  @Nullable
  private PsiElement findOverloadedMethod(PsiMethod[] methods) {
    final PsiMethodCallExpression definitionCall = PsiTreeUtil.getParentOfType(myElement, PsiMethodCallExpression.class);
    if (definitionCall != null) {
      final List<PsiExpression> arguments = getReflectionMethodArguments(definitionCall, 1);
      if (arguments != null) {
        final List<ReflectiveType> parameterTypes = ContainerUtil.map(arguments, type -> getReflectiveType(type));
        return matchMethod(methods, parameterTypes);
      }
    }
    return null;
  }

  @Override
  public void handleInsert(InsertionContext context, LookupElement item) {
    final Object object = item.getObject();
    if (object instanceof ReflectiveSignature) {
      final ReflectiveSignature signature = (ReflectiveSignature)object;
      final String text = signature.getText(false, false, type -> type + ".class");
      replaceText(context, text.isEmpty() ? "" : ", " + text);
    }
  }


  @Nullable
  public static PsiMethod matchMethod(@NotNull PsiMethod[] methods, @NotNull List<ReflectiveType> argumentTypes) {
    int mismatchCount = Integer.MAX_VALUE;
    PsiMethod bestGuess = null;
    for (PsiMethod method : methods) {
      final int match = matchMethodArguments(method, argumentTypes);
      if (match == 0) {
        return method;
      }
      if (match < 0) {
        continue;
      }
      if (mismatchCount > match) {
        mismatchCount = match;
        bestGuess = method;
      }
    }
    return bestGuess;
  }

  private static int matchMethodArguments(PsiMethod method, List<ReflectiveType> argumentTypes) {
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != argumentTypes.size()) {
      return -1;
    }
    int mismatchCount = 0;
    for (int i = 0; i < parameters.length; i++) {
      final ReflectiveType argumentType = argumentTypes.get(i);
      if (argumentType == null) {
        mismatchCount++;
        continue;
      }
      if (!argumentType.isEqualTo(parameters[i].getType())) {
        return -1;
      }
    }
    return mismatchCount;
  }

  @Nullable
  public static List<PsiExpression> getReflectionMethodArguments(@NotNull PsiMethodCallExpression definitionCall, int argumentOffset) {
    final PsiExpression[] arguments = definitionCall.getArgumentList().getExpressions();

    if (arguments.length == argumentOffset + 1) {
      final PsiExpression[] arrayElements = getVarargAsArray(arguments[argumentOffset]);
      if (arrayElements != null) {
        return Arrays.asList(arrayElements);
      }
    }
    if (arguments.length >= argumentOffset) {
      return Arrays.asList(arguments).subList(argumentOffset, arguments.length);
    }
    return null;
  }
}
