// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration.rules.guava;

import com.intellij.codeInspection.AnonymousCanBeLambdaInspection;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.*;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeEvaluator;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.refactoring.typeMigration.inspections.GuavaConversionSettings;
import com.intellij.refactoring.typeMigration.rules.TypeConversionRule;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * @author Dmitry Batkovich
 */
public abstract class BaseGuavaTypeConversionRule extends TypeConversionRule {
  private final Supplier<Map<String, TypeConversionDescriptorBase>> simpleDescriptors = NotNullLazyValue.softLazy(() -> {
    Map<String, TypeConversionDescriptorBase> map = new HashMap<>();
    fillSimpleDescriptors(map);
    return map;
  });

  protected void fillSimpleDescriptors(Map<@NlsSafe String, TypeConversionDescriptorBase> descriptorsMap) {}

  protected @Nullable TypeConversionDescriptorBase findConversionForMethod(PsiType from,
                                                                           PsiType to,
                                                                           @NotNull PsiMethod method,
                                                                           @NotNull @NlsSafe String methodName,
                                                                           PsiExpression context,
                                                                           TypeMigrationLabeler labeler) {
    return null;
  }

  protected @Nullable TypeConversionDescriptorBase findConversionForVariableReference(@Nullable PsiExpression context) {
    return null;
  }

  @Contract(pure = true)
  public abstract @NotNull String ruleFromClass();

  @Contract(pure = true)
  public abstract @NotNull String ruleToClass();

  protected @NotNull Set<String> getAdditionalUtilityClasses() {
    return Collections.emptySet();
  }

  @Override
  public final @Nullable TypeConversionDescriptorBase findConversion(@Nullable PsiType from,
                                                                     @Nullable PsiType to,
                                                                     PsiMember member,
                                                                     PsiExpression context,
                                                                     TypeMigrationLabeler labeler) {
    if (from != null && to != null && !canConvert(from, to, ruleFromClass(), ruleToClass())) {
      return null;
    }
    if (member instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)member;
      final String methodName = method.getName();
      final PsiClass aClass = method.getContainingClass();
      if (isValidMethodQualifierToConvert(aClass)) {
        final TypeConversionDescriptorBase descriptor = simpleDescriptors.get().get(methodName);
        if (descriptor != null) {
          return descriptor;
        }
      }
      TypeConversionDescriptorBase conversionForMethod = findConversionForMethod(from, to, method, methodName, context, labeler);
      return conversionForMethod != null ? conversionForMethod : getUnknownMethodConversion();
    } else if (context instanceof PsiNewExpression) {
      final PsiAnonymousClass anonymousClass = ((PsiNewExpression)context).getAnonymousClass();
      return anonymousClass == null ? null : findConversionForAnonymous(anonymousClass, labeler.getSettings(GuavaConversionSettings.class));
    }
    else if (context instanceof PsiMethodReferenceExpression) {
      final PsiType methodReferenceType = context.getType();
      if (methodReferenceType != null && to != null && to.isAssignableFrom(methodReferenceType)) {
        return new TypeConversionDescriptorBase();
      }
    }
    else {
      if (context instanceof PsiReferenceExpression) {
        final PsiElement resolvedElement = ((PsiReferenceExpression)context).resolve();
        if (resolvedElement instanceof PsiVariable) {
          return findConversionForVariableReference(context);
        }
      }
    }
    return null;
  }

  protected abstract TypeConversionDescriptorBase getUnknownMethodConversion();

  protected @Nullable TypeConversionDescriptorBase findConversionForAnonymous(@NotNull PsiAnonymousClass anonymousClass,
                                                                              @Nullable GuavaConversionSettings settings) {
    final Set<String> ignoredAnnotations = settings != null ? settings.getIgnoredAnnotations() : Collections.emptySet();
    if (AnonymousCanBeLambdaInspection.canBeConvertedToLambda(anonymousClass, false, ignoredAnnotations)) {
      return new TypeConversionDescriptorBase() {
        @Override
        public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) throws IncorrectOperationException {
          return AnonymousCanBeLambdaInspection.replacePsiElementWithLambda(expression, false, true);
        }
      };
    }
    return null;
  }

  protected boolean isValidMethodQualifierToConvert(PsiClass aClass) {
    return aClass != null && (ruleFromClass().equals(aClass.getQualifiedName()) || getAdditionalUtilityClasses().contains(aClass.getQualifiedName()));
  }

  static boolean canConvert(@Nullable PsiType from,
                            @Nullable PsiType to,
                            @NotNull String fromClassName,
                            @NotNull String toClassName) {
    if (!(from instanceof PsiClassType)) {
      return false;
    }
    if (!(to instanceof PsiClassType)) {
      return false;
    }

    final PsiClassType.ClassResolveResult fromResolveResult = ((PsiClassType)from).resolveGenerics();
    PsiClass fromClass = fromResolveResult.getElement();
    if (fromClass instanceof PsiAnonymousClass) {
      fromClass = ((PsiAnonymousClass)fromClass).getBaseClassType().resolve();
    }
    if (fromClass == null || !fromClassName.equals(fromClass.getQualifiedName())) {
      return false;
    }

    final PsiClassType.ClassResolveResult toResolveResult = ((PsiClassType)to).resolveGenerics();
    final PsiClass toClass = toResolveResult.getElement();
    if (toClass == null || !toClassName.equals(toClass.getQualifiedName())) {
      return false;
    }

    //final Collection<PsiType> fromTypeParameters = fromResolveResult.getSubstitutor().getSubstitutionMap().values();
    //final Collection<PsiType> toTypeParameters = toResolveResult.getSubstitutor().getSubstitutionMap().values();
    return true;//!fromTypeParameters.equals(toTypeParameters);
  }
}
