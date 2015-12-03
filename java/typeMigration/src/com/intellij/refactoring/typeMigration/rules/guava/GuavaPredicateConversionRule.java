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

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
/**
 * @author Dmitry Batkovich
 */
public class GuavaPredicateConversionRule extends BaseGuavaTypeConversionRule {
  public static final String GUAVA_PREDICATE = "com.google.common.base.Predicate";
  public static final String JAVA_PREDICATE = "java.util.function.Predicate";

  @NotNull
  @Override
  protected Set<String> getAdditionalUtilityClasses() {
    return Collections.singleton("com.google.common.base.Predicates");
  }

  @Override
  protected void fillSimpleDescriptors(Map<String, TypeConversionDescriptorBase> descriptorsMap) {
    descriptorsMap.put("apply", new FunctionalInterfaceTypeConversionDescriptor("apply", "test"));
  }

  @Nullable
  @Override
  protected TypeConversionDescriptorBase findConversionForVariableReference(@NotNull PsiReferenceExpression referenceExpression,
                                                                            @NotNull PsiVariable psiVariable, PsiExpression context) {
    return new TypeConversionDescriptor("$p$", "$p$::test");
  }

  @Nullable
  @Override
  protected TypeConversionDescriptorBase findConversionForMethod(PsiType from,
                                                                 PsiType to,
                                                                 @NotNull PsiMethod method,
                                                                 @NotNull String methodName,
                                                                 PsiExpression context,
                                                                 TypeMigrationLabeler labeler) {
    if (!(context instanceof PsiMethodCallExpression)) {
      return null;
    }
    if (method.getParameterList().getParametersCount() == 1) {
      final PsiParameter parameter = method.getParameterList().getParameters()[0];
      final PsiClass psiClass = PsiTypesUtil.getPsiClass(parameter.getType().getDeepComponentType());
      if (psiClass == null || CommonClassNames.JAVA_LANG_ITERABLE.equals(psiClass.getQualifiedName())) {
        return null;
      }
    }
    if (methodName.equals("or")) {
      //TODO
      return null;
    }
    else if (methodName.equals("and")) {
      //TODO
      return null;
    }
    else if (methodName.equals("not")) {
      //TODO
      return null;
    }
    return null;
  }

  @NotNull
  @Override
  public String ruleFromClass() {
    return GUAVA_PREDICATE;
  }

  @NotNull
  @Override
  public String ruleToClass() {
    return JAVA_PREDICATE;
  }
}
