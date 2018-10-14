/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.codeInspection.java15api.Java15APIUsageInspection;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedMembersSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

public class FunctionalInterfaceSuggester {
  public static final String[] FUNCTIONAL_INTERFACES = {
    //old jdk without annotations
    CommonClassNames.JAVA_LANG_RUNNABLE,
    CommonClassNames.JAVA_UTIL_CONCURRENT_CALLABLE,
    CommonClassNames.JAVA_UTIL_COMPARATOR,

    //IDEA
    "com.intellij.util.Function",
    "com.intellij.util.Consumer",
    "com.intellij.openapi.util.Computable",
    "com.intellij.openapi.util.Condition",
    "com.intellij.util.Processor",
    "com.intellij.util.Producer",

    //guava
    "com.google.common.base.Function",
    "com.google.common.base.Predicate",
    "com.google.common.base.Supplier",

    //common collections
    "org.apache.commons.collections.Closure",
    "org.apache.commons.collections.Factory",
    "org.apache.commons.collections.Predicate",
    "org.apache.commons.collections.Transformer",

    //trove
    "gnu.trove.TObjectFunction",
    "gnu.trove.TObjectProcedure",
    "gnu.trove.TObjectObjectProcedure",
  };

  public static Collection<? extends PsiType> suggestFunctionalInterfaces(final @NotNull PsiFunctionalExpression expression) {
    final PsiType qualifierType = expression instanceof PsiMethodReferenceExpression
                                  ? PsiMethodReferenceUtil.getQualifierType((PsiMethodReferenceExpression)expression) : null;
    return suggestFunctionalInterfaces(expression, aClass -> composeAcceptableType(aClass, expression, qualifierType));
  }

  public static Collection<? extends PsiType> suggestFunctionalInterfaces(final @NotNull PsiMethod method) {
    return suggestFunctionalInterfaces(method, false);
  }

  public static Collection<? extends PsiType> suggestFunctionalInterfaces(final @NotNull PsiMethod method,
                                                                          boolean suggestUnhandledThrowables) {
    if (method.isConstructor()) {
      return Collections.emptyList();
    }

    return suggestFunctionalInterfaces(method, aClass -> {
      final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(aClass);
      if (interfaceMethod != null) {
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        final PsiParameter[] interfaceMethodParameters = interfaceMethod.getParameterList().getParameters();
        if (parameters.length != interfaceMethodParameters.length) {
          return Collections.emptyList();
        }

        final PsiType[] left = new PsiType[parameters.length + 1];
        final PsiType[] right = new PsiType[parameters.length + 1];

        for (int i = 0; i < parameters.length; i++) {
          left[i] = interfaceMethodParameters[i].getType();
          right[i] = parameters[i].getType();
        }

        left[parameters.length] = method.getReturnType();
        right[parameters.length] = interfaceMethod.getReturnType();

        final PsiTypeParameter[] typeParameters = aClass.getTypeParameters();
        final PsiSubstitutor substitutor = PsiResolveHelper.SERVICE.getInstance(aClass.getProject())
          .inferTypeArguments(typeParameters, left, right, PsiUtil.getLanguageLevel(method));
        if (PsiUtil.isRawSubstitutor(aClass, substitutor)) {
          return Collections.emptyList();
        }

        for (int i = 0; i < interfaceMethodParameters.length; i++) {
          PsiType paramType = parameters[i].getType();
          PsiType interfaceParamType = substitutor.substitute(interfaceMethodParameters[i].getType());
          if (!(interfaceParamType instanceof PsiPrimitiveType
                ? paramType.equals(interfaceParamType) : TypeConversionUtil.isAssignable(paramType, interfaceParamType))) {
            return Collections.emptyList();
          }
        }

        final PsiType returnType = method.getReturnType();
        PsiType interfaceMethodReturnType = substitutor.substitute(interfaceMethod.getReturnType());
        if (returnType != null && !TypeConversionUtil.isAssignable(returnType, interfaceMethodReturnType)) {
          return Collections.emptyList();
        }

        if (interfaceMethodReturnType instanceof PsiPrimitiveType && !interfaceMethodReturnType.equals(returnType)) {
          return Collections.emptyList();
        }

        final PsiClassType[] interfaceThrownTypes = interfaceMethod.getThrowsList().getReferencedTypes();
        final PsiClassType[] thrownTypes = method.getThrowsList().getReferencedTypes();
        for (PsiClassType thrownType : thrownTypes) {
          if (!ExceptionUtil.isHandledBy(thrownType, interfaceThrownTypes, substitutor)) {
            return Collections.emptyList();
          }
        }

        if (!suggestUnhandledThrowables) {
          for (PsiClassType thrownType : interfaceThrownTypes) {
            final PsiCodeBlock codeBlock = PsiTreeUtil.getContextOfType(method, PsiCodeBlock.class);
            PsiType substitutedThrowable = substitutor.substitute(thrownType);
            if (codeBlock == null || !(substitutedThrowable instanceof PsiClassType) ||
                !ExceptionUtil.isHandled((PsiClassType)substitutedThrowable, codeBlock)) {
              return Collections.emptyList();
            }
          }
        }

        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(aClass.getProject());
        return Collections.singletonList(elementFactory.createType(aClass, substitutor));
      }
      return Collections.emptyList();
    });
  }

  private static <T extends PsiElement> Collection<? extends PsiType> suggestFunctionalInterfaces(final @NotNull T element,
                                                                                                  final Function<? super PsiClass, List<? extends PsiType>> acceptanceChecker) {
    final Project project = element.getProject();
    final Set<PsiType> types = new HashSet<>();
    final Processor<PsiMember> consumer = member -> {
      if (member instanceof PsiClass &&
          Java15APIUsageInspection.getLastIncompatibleLanguageLevel(member, PsiUtil.getLanguageLevel(element)) == null) {
        if (!JavaPsiFacade.getInstance(project).getResolveHelper().isAccessible(member, element, null)) {
          return true;
        }
        ContainerUtil.addAll(types, acceptanceChecker.apply((PsiClass)member));
      }
      return true;
    };
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final GlobalSearchScope allScope = GlobalSearchScope.allScope(project);
    final PsiClass functionalInterfaceClass = psiFacade.findClass(CommonClassNames.JAVA_LANG_FUNCTIONAL_INTERFACE, allScope);
    if (functionalInterfaceClass != null) {
      AnnotatedMembersSearch.search(functionalInterfaceClass, element.getResolveScope()).forEach(consumer);
    }

    for (String functionalInterface : FUNCTIONAL_INTERFACES) {
      final PsiClass aClass = psiFacade.findClass(functionalInterface, element.getResolveScope());
      if (aClass != null) {
        consumer.process(aClass);
      }
    }

    final ArrayList<PsiType> typesToSuggest = new ArrayList<>(types);
    Collections.sort(typesToSuggest, Comparator.comparing(PsiType::getCanonicalText));
    return typesToSuggest;
  }

  private static List<PsiType> composeAcceptableType(@NotNull PsiClass interface2Consider,
                                                     @NotNull PsiFunctionalExpression expression,
                                                     PsiType qualifierType) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(interface2Consider.getProject());
    final PsiType type = elementFactory.createType(interface2Consider, PsiSubstitutor.EMPTY);
    if (expression.isAcceptable(type)) {
      return Collections.singletonList(type);
    }

    return composeAcceptableType(interface2Consider, expression, qualifierType, elementFactory);
  }

  private static List<PsiType> composeAcceptableType(final PsiClass interface2Consider,
                                                     final PsiFunctionalExpression expression,
                                                     PsiType qualifierType, final PsiElementFactory elementFactory) {

    if (interface2Consider.hasTypeParameters()) {
      final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(interface2Consider);
      if (interfaceMethod != null) {
        if (expression instanceof PsiLambdaExpression && ((PsiLambdaExpression)expression).hasFormalParameterTypes()) {
          final PsiLambdaExpression lambdaExpression = (PsiLambdaExpression)expression;
          final PsiType type = composeLambdaExpressionType(interface2Consider, expression, elementFactory, interfaceMethod,
                                                           lambdaExpression);
          return type == null ? Collections.emptyList() : Collections.singletonList(type);
        }
        else if (expression instanceof PsiMethodReferenceExpression) {
          final PsiMethodReferenceExpression methodReferenceExpression = (PsiMethodReferenceExpression)expression;
          if (methodReferenceExpression.isConstructor()) {
            return composeConstructorReferenceExpressionType(interface2Consider, expression, qualifierType, elementFactory, interfaceMethod,
                                                             methodReferenceExpression);
          }
          else {
            final PsiType type = composeMethodReferenceExpressionType(interface2Consider, expression, qualifierType, elementFactory,
                                                                      interfaceMethod, methodReferenceExpression);
            return type == null ? Collections.emptyList() : Collections.singletonList(type);
          }
        }
      }
    }

    return Collections.emptyList();
  }

  @Nullable
  private static PsiType composeMethodReferenceExpressionType(PsiClass interface2Consider,
                                                              PsiFunctionalExpression expression,
                                                              PsiType qualifierType,
                                                              PsiElementFactory elementFactory,
                                                              PsiMethod interfaceMethod,
                                                              PsiMethodReferenceExpression methodReferenceExpression) {
    final PsiParameter[] parameters = interfaceMethod.getParameterList().getParameters();
    final PsiType[] left = new PsiType[parameters.length + 1];
    final PsiType[] right = new PsiType[parameters.length + 1];
    final PsiMethod method = getTargetMethod(methodReferenceExpression, qualifierType, parameters, left, right);
    if (method == null) {
      return null;
    }

    PsiParameter[] functionalExprParameters = method.getParameterList().getParameters();

    left[parameters.length] = method.getReturnType();
    right[parameters.length] = interfaceMethod.getReturnType();

    final int offset;
    if (PsiMethodReferenceUtil.isStaticallyReferenced(methodReferenceExpression) &&
        !method.hasModifierProperty(PsiModifier.STATIC)) {
      offset = 1;
    }
    else {
      offset = 0;
    }
    fillLeftRightTypes(parameters, functionalExprParameters, offset, left, right);

    return substituteAcceptableType(interface2Consider, expression, elementFactory, left, right);
  }

  private static List<PsiType> composeConstructorReferenceExpressionType(PsiClass interface2Consider,
                                                                         PsiFunctionalExpression expression,
                                                                         PsiType qualifierType,
                                                                         PsiElementFactory elementFactory,
                                                                         PsiMethod interfaceMethod,
                                                                         PsiMethodReferenceExpression methodReferenceExpression) {
    final PsiParameter[] parameters = interfaceMethod.getParameterList().getParameters();
    final List<ConstructorTypes> constructors = getTargetConstructors(methodReferenceExpression, parameters, qualifierType,
                                                                      interfaceMethod.getReturnType());
    final List<PsiType> types = new ArrayList<>();
    for (ConstructorTypes constructorTypes : constructors) {
      final PsiType type = substituteAcceptableType(interface2Consider, expression, elementFactory,
                                                    constructorTypes.leftParameters, constructorTypes.rightParameters);
      if (type != null) {
        types.add(type);
      }
    }
    return types;
  }

  @Nullable
  private static PsiType composeLambdaExpressionType(PsiClass interface2Consider,
                                                     PsiFunctionalExpression expression,
                                                     PsiElementFactory elementFactory,
                                                     PsiMethod interfaceMethod,
                                                     PsiLambdaExpression lambdaExpression) {
    final PsiParameter[] parameters = interfaceMethod.getParameterList().getParameters();
    final PsiType[] left = new PsiType[parameters.length];
    final PsiType[] right = new PsiType[parameters.length];

    PsiParameter[] functionalExprParameters = lambdaExpression.getParameterList().getParameters();

    if (parameters.length != functionalExprParameters.length) {
      return null;
    }

    fillLeftRightTypes(parameters, functionalExprParameters, 0, left, right);

    return substituteAcceptableType(interface2Consider, expression, elementFactory, left, right);
  }

  private static void fillLeftRightTypes(PsiParameter[] parameters,
                                         PsiParameter[] functionalExprParameters,
                                         int offset,
                                         PsiType[] left,
                                         PsiType[] right) {
    for (int i = 0; i < functionalExprParameters.length; i++) {
      left[i + offset] = parameters[i + offset].getType();
      right[i + offset] = functionalExprParameters[i].getType();
    }
  }

  @Nullable
  private static PsiType substituteAcceptableType(PsiClass interface2Consider,
                                                  PsiFunctionalExpression expression,
                                                  PsiElementFactory elementFactory, PsiType[] left, PsiType[] right) {
    final PsiSubstitutor substitutor = PsiResolveHelper.SERVICE.getInstance(interface2Consider.getProject())
      .inferTypeArguments(interface2Consider.getTypeParameters(), left, right, PsiUtil.getLanguageLevel(expression));

    final PsiType type = elementFactory.createType(interface2Consider, substitutor);

    return expression.isAcceptable(type) ? type : null;
  }

  @Nullable
  private static PsiMethod getTargetMethod(PsiMethodReferenceExpression expression,
                                           PsiType qualifierType,
                                           PsiParameter[] parameters,
                                           PsiType[] left,
                                           PsiType[] right) {
    final boolean staticallyReferenced = PsiMethodReferenceUtil.isStaticallyReferenced(expression);
    final JavaResolveResult[] results = expression.multiResolve(true);
    for (JavaResolveResult result : results) {
      final PsiElement element = result.getElement();
      if (element instanceof PsiMethod) {
        int offset = staticallyReferenced && !((PsiMethod)element).hasModifierProperty(PsiModifier.STATIC) ? 1 : 0;
        final PsiParameter[] functionalExprParameters = ((PsiMethod)element).getParameterList().getParameters();
        if (functionalExprParameters.length + offset == parameters.length) {
          if (offset > 0) {
            if (qualifierType == null) {
              continue;
            }

            left[0] = parameters[0].getType();
            right[0] = qualifierType;
          }
          return (PsiMethod)element;
        }
      }
    }
    return null;
  }

  private static List<ConstructorTypes> getTargetConstructors(PsiMethodReferenceExpression expression,
                                                              PsiParameter[] parameters,
                                                              PsiType qualifierType,
                                                              PsiType interfaceMethodReturnType) {
    final List<ConstructorTypes> constructors = new ArrayList<>();

    final JavaResolveResult[] ctorResults = expression.multiResolve(true);
    for (JavaResolveResult ctorResult : ctorResults) {
      final PsiElement element = ctorResult.getElement();
      if (element instanceof PsiMethod) {
        final PsiMethod ctorMethod = (PsiMethod)element;
        final PsiParameterList parameterList = ctorMethod.getParameterList();
        if (parameters.length == parameterList.getParametersCount()) {
          final PsiType[] left = new PsiType[parameters.length + 1];
          final PsiType[] right = new PsiType[parameters.length + 1];

          for (int i = 0, length = parameters.length; i < length; i++) {
            left[i] = parameters[i].getType();
            right[i] = parameterList.getParameters()[i].getType();
          }

          left[parameters.length] = qualifierType;
          right[parameters.length] = interfaceMethodReturnType;

          constructors.add(new ConstructorTypes(left, right));
        }
      }
    }

    return constructors;
  }

  private static class ConstructorTypes {
    private final PsiType[] leftParameters;
    private final PsiType[] rightParameters;

    private ConstructorTypes(PsiType[] leftParameters, PsiType[] rightParameters) {
      this.leftParameters = leftParameters;
      this.rightParameters = rightParameters;
    }
  }
}
