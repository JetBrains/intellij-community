/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.refactoring.typeMigration.rules.guava;

import com.intellij.codeInspection.AnonymousCanBeLambdaInspection;
import com.intellij.psi.*;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeEvaluator;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.refactoring.typeMigration.inspections.GuavaConversionSettings;
import com.intellij.refactoring.typeMigration.rules.TypeConversionRule;
import com.intellij.reference.SoftLazyValue;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public abstract class BaseGuavaTypeConversionRule extends TypeConversionRule {
  private final SoftLazyValue<Map<String, TypeConversionDescriptorBase>> mySimpleDescriptors = new SoftLazyValue<Map<String, TypeConversionDescriptorBase>>() {
    @NotNull
    @Override
    protected Map<String, TypeConversionDescriptorBase> compute() {
      Map<String, TypeConversionDescriptorBase> map = new HashMap<>();
      fillSimpleDescriptors(map);
      return map;
    }
  };

  protected void fillSimpleDescriptors(Map<String, TypeConversionDescriptorBase> descriptorsMap) {};

  @Nullable
  protected TypeConversionDescriptorBase findConversionForMethod(PsiType from,
                                                                 PsiType to,
                                                                 @NotNull PsiMethod method,
                                                                 @NotNull String methodName,
                                                                 PsiExpression context,
                                                                 TypeMigrationLabeler labeler) {
    return null;
  };

  @Nullable
  protected TypeConversionDescriptorBase findConversionForVariableReference(@NotNull PsiReferenceExpression referenceExpression,
                                                                            @NotNull PsiVariable psiVariable,
                                                                            @Nullable PsiExpression context) {
    return null;
  }

  @NotNull
  public abstract String ruleFromClass();

  @NotNull
  public abstract String ruleToClass();

  @NotNull
  protected Set<String> getAdditionalUtilityClasses() {
    return Collections.emptySet();
  }

  @Nullable
  @Override
  public final TypeConversionDescriptorBase findConversion(@Nullable PsiType from,
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
        final TypeConversionDescriptorBase descriptor = mySimpleDescriptors.getValue().get(methodName);
        if (descriptor != null) {
          return descriptor;
        }
      }
      return findConversionForMethod(from, to, method, methodName, context, labeler);
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
          return findConversionForVariableReference((PsiReferenceExpression)context, (PsiVariable)resolvedElement, context);
        }
      }
    }
    return null;
  }

  @Nullable
  protected TypeConversionDescriptorBase findConversionForAnonymous(@NotNull PsiAnonymousClass anonymousClass,
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
