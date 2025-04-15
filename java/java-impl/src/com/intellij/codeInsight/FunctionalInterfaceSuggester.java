// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.module.JdkApiCompatabilityCache;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedMembersSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

public final class FunctionalInterfaceSuggester {
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
          left[i]  = interfaceMethodParameters[i].getType();
          right[i] = parameters[i].getType();
        }

        left[parameters.length] = method.getReturnType();
        right[parameters.length] = interfaceMethod.getReturnType();

        final PsiTypeParameter[] typeParameters = aClass.getTypeParameters();
        final PsiSubstitutor substitutor = PsiResolveHelper.getInstance(aClass.getProject())
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

  private static <T extends PsiElement> Collection<? extends PsiType> suggestFunctionalInterfaces(final @NotNull T element, final Function<? super PsiClass, ? extends List<? extends PsiType>> acceptanceChecker) {
    final Project project = element.getProject();
    final Set<PsiType> types = new HashSet<>();
    final ReadActionProcessor<PsiMember> consumer = new ReadActionProcessor<>() {
      @Override
      public boolean processInReadAction(PsiMember member) {
        if (member instanceof PsiClass &&
            JdkApiCompatabilityCache.getInstance().firstCompatibleLanguageLevel(member, PsiUtil.getLanguageLevel(element)) == null) {
          if (!JavaPsiFacade.getInstance(project).getResolveHelper().isAccessible(member, element, null)) {
            return true;
          }
          types.addAll(acceptanceChecker.apply((PsiClass)member));
        }
        return true;
      }
    };
    Task.Modal task = new Task.Modal(project, JavaBundle.message("dialog.title.check.functional.interface.candidates"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        final GlobalSearchScope allScope = GlobalSearchScope.allScope(project);
        final PsiClass functionalInterfaceClass =
          ReadAction.compute(() -> psiFacade.findClass(CommonClassNames.JAVA_LANG_FUNCTIONAL_INTERFACE, allScope));
        if (functionalInterfaceClass != null) {
          AnnotatedMembersSearch.search(functionalInterfaceClass, ReadAction.compute(() -> element.getResolveScope())).forEach(consumer);
        }

        for (String functionalInterface : FUNCTIONAL_INTERFACES) {
          final PsiClass aClass = ReadAction.compute(() -> psiFacade.findClass(functionalInterface, element.getResolveScope()));
          if (aClass != null) {
            consumer.process(aClass);
          }
        }
      }
    };
    if (ApplicationManager.getApplication().isDispatchThread()) {
      ProgressManager.getInstance().run(task);
    }
    else {
      task.run(new EmptyProgressIndicator());
    }

    final ArrayList<PsiType> typesToSuggest = new ArrayList<>(types);
    typesToSuggest.sort(Comparator.comparing(PsiType::getCanonicalText));
    return typesToSuggest;
  }

  private static List<? extends PsiType> composeAcceptableType(@NotNull PsiClass interface2Consider,
                                                             @NotNull PsiFunctionalExpression expression,
                                                             PsiType qualifierType) {
    final PsiType type = JavaPsiFacade.getElementFactory(interface2Consider.getProject()).createType(interface2Consider, PsiSubstitutor.EMPTY);
    if (expression.isAcceptable(type)) {
      return Collections.singletonList(type);
    }

    return composeAcceptableType1(interface2Consider, expression, qualifierType);
  }

  private static @NotNull List<? extends PsiType> composeAcceptableType1(final PsiClass interface2Consider,
                                                                         final PsiFunctionalExpression expression,
                                                                         final PsiType qualifierType) {
    if (interface2Consider.hasTypeParameters()) {
      final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(interface2Consider);
      if (interfaceMethod != null) {
        final PsiParameter[] parameters = interfaceMethod.getParameterList().getParameters();
        Project project = interface2Consider.getProject();
        PsiType returnType = interfaceMethod.getReturnType();
        if (expression instanceof PsiLambdaExpression && ((PsiLambdaExpression)expression).hasFormalParameterTypes()) {
          PsiParameter[] functionalExprParameters = ((PsiLambdaExpression)expression).getParameterList().getParameters();
          if (parameters.length != functionalExprParameters.length) {
            return Collections.emptyList();
          }

          final PsiType[] left = new PsiType[parameters.length + 1];
          final PsiType[] right = new PsiType[parameters.length + 1];
          for (int i = 0; i < functionalExprParameters.length; i++) {
            left[i] = parameters[i].getType();
            right[i] = functionalExprParameters[i].getType();
          }

          List<PsiExpression> returnExpressions = LambdaUtil.getReturnExpressions(((PsiLambdaExpression)expression));
          left[parameters.length] = returnExpressions.isEmpty() ? PsiTypes.voidType() : returnExpressions.get(0).getType();
          right[parameters.length] = returnType;

          final PsiSubstitutor substitutor = PsiResolveHelper.getInstance(project)
            .inferTypeArguments(interface2Consider.getTypeParameters(), left, right, PsiUtil.getLanguageLevel(expression));

          PsiType type = JavaPsiFacade.getElementFactory(project).createType(interface2Consider, substitutor);

          if (expression.isAcceptable(type)) {
            return Collections.singletonList(type);
          }
        }
        else if (expression instanceof PsiMethodReferenceExpression referenceExpression) {
          List<PsiType> types = new ArrayList<>();
          for (JavaResolveResult result : referenceExpression.multiResolve(true)) {
            final PsiElement element = result.getElement();
            if (element == null) continue;

            if (element instanceof PsiMethod method) {
              int offset = hasOffset(referenceExpression, method) ? 1 : 0;
              final PsiParameter[] targetMethodParameters = method.getParameterList().getParameters();
              if (targetMethodParameters.length + offset == parameters.length) {

                final PsiType[] left = new PsiType[parameters.length + 1];
                final PsiType[] right = new PsiType[parameters.length + 1];
                if (offset > 0) {
                  if (qualifierType == null) {
                    continue;
                  }
                  left[0] = parameters[0].getType();
                  right[0] = qualifierType;
                }

                final PsiSubstitutor partialSubstitutor = getPartialSubstitutor(referenceExpression, method);

                for (int i = 0; i < targetMethodParameters.length; i++) {
                  left[i + offset] = parameters[i + offset].getType();
                  right[i + offset] = partialSubstitutor.substitute(targetMethodParameters[i].getType());
                }

                left[parameters.length] = method.isConstructor() ? qualifierType : partialSubstitutor.substitute(method.getReturnType());
                right[parameters.length] = returnType;

                ContainerUtil.addIfNotNull(types, getAcceptableType(interface2Consider, expression, left, right));
              }
            }
            else if (maybeDefaultConstructorRef(referenceExpression, interfaceMethod, element)) {

              final PsiType[] left = new PsiType[] { qualifierType };
              final PsiType[] right = new PsiType[] { returnType };

              ContainerUtil.addIfNotNull(types, getAcceptableType(interface2Consider, expression, left, right));
            }
          }
          return types;
        }
      }
    }
    return Collections.emptyList();
  }

  /**
   * Checks if the passed {@code expression} is a reference to the default constructor and it matches the {@code interfaceMethod}
   *
   * @param expression expression to examine
   * @param interfaceMethod a SAM method from {@link FunctionalInterface} to check if it has no parameters
   * @param element a possible instance of {@link PsiClass} to check if it has no declared constructors
   *
   * @return {@code true} if {@code expression} is the method reference to a default constructor, otherwise {@code false}
   */
  private static boolean maybeDefaultConstructorRef(final @NotNull PsiMethodReferenceExpression expression,
                                                    final @NotNull PsiMethod interfaceMethod,
                                                    final @NotNull PsiElement element) {
    return element instanceof PsiClass &&
           ((PsiClass)element).getConstructors().length == 0 &&
           expression.isConstructor() &&
           !interfaceMethod.hasParameters();
  }

  /**
   * The method returns a {@link PsiType} constructed type from {@code interface2Consider}
   * if it is acceptable by {@code expression}
   *
   * @param interface2Consider a {@link FunctionalInterface} to construct the type from
   * @param expression a {@link PsiFunctionalExpression} to check the constructed type against
   * @param left a list of type arguments from {@code expression}
   * @param right a list of type arguments from {@code interface2Consider}
   *
   * @return a {@link PsiType} constructed type if the constructed type is acceptable by the {@code expression},
   * otherwise {@code null}
   */
  private static @Nullable PsiType getAcceptableType(final @NotNull PsiClass interface2Consider,
                                           final @NotNull PsiFunctionalExpression expression,
                                           final @NotNull PsiType[] left,
                                           final @NotNull PsiType[] right) {
    final Project project = interface2Consider.getProject();

    final PsiSubstitutor substitutor = PsiResolveHelper.getInstance(project)
      .inferTypeArguments(interface2Consider.getTypeParameters(), left, right, PsiUtil.getLanguageLevel(expression));

    final PsiType type = JavaPsiFacade.getElementFactory(project).createType(interface2Consider, substitutor);

    return expression.isAcceptable(type) ? type : null;
  }

  /**
   * Generates a {@link PsiSubstitutor} from the provided type parameters of the expression
   *
   * @param expression an instance of {@link PsiMethodReferenceExpression} to get the list of type parameters' values
   * @param method an instance of {@link PsiMethod} that corresponds to {@code expression}
   *
   * @return {@link PsiSubstitutor#EMPTY} if there are no type parameters' values in {@code expression} or {@link PsiSubstitutor}
   * that is generated from {@code expression}
   */
  private static @NotNull PsiSubstitutor getPartialSubstitutor(final @NotNull PsiMethodReferenceExpression expression,
                                                      final @NotNull PsiMethod method) {
    final PsiType[] typeArguments = expression.getTypeParameters();

    if (typeArguments.length > 0) {
      final PsiTypeParameter[] typeParameters = method.getTypeParameters();
      if (typeParameters.length == typeArguments.length) {
        final List<PsiTypeParameter> arguments = Arrays.asList(typeParameters);
        final List<PsiType> values = Arrays.asList(typeArguments);

        final Map<PsiTypeParameter, PsiType> substitutorMap = ContainerUtil.newHashMap(arguments, values);

        return PsiSubstitutor.createSubstitutor(substitutorMap);
      }
    }
    return PsiSubstitutor.EMPTY;
  }

  private static boolean hasOffset(PsiMethodReferenceExpression expression, PsiMethod method) {
    return PsiMethodReferenceUtil.isStaticallyReferenced(expression) &&
           !method.hasModifierProperty(PsiModifier.STATIC) &&
           !method.isConstructor();
  }
}
