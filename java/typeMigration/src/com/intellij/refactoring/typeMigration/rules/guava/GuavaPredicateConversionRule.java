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

import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
/**
 * @author Dmitry Batkovich
 */
public class GuavaPredicateConversionRule extends BaseGuavaTypeConversionRule {
  public static final String GUAVA_PREDICATE = "com.google.common.base.Predicate";

  @Override
  protected void fillSimpleDescriptors(Map<String, TypeConversionDescriptorBase> descriptorsMap) {
    descriptorsMap.put("apply", new FunctionalInterfaceTypeConversionDescriptor("apply", "test"));
  }

  @Nullable
  @Override
  protected TypeConversionDescriptorBase findConversionForVariableReference(@NotNull PsiReferenceExpression referenceExpression,
                                                                            @NotNull PsiVariable psiVariable) {
    return new TypeConversionDescriptor("$p$", "$p$::test");
  }

  @NotNull
  @Override
  public String ruleFromClass() {
    return GUAVA_PREDICATE;
  }

  @NotNull
  @Override
  public String ruleToClass() {
    return "java.util.function.Predicate";
  }
}
