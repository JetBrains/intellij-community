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

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author Dmitry Batkovich
 */
public class GuavaFunctionConversionRule extends BaseGuavaTypeConversionRule {
  public static final String JAVA_UTIL_FUNCTION_FUNCTION = "java.util.function.Function";

  @Override
  protected void fillSimpleDescriptors(Map<String, TypeConversionDescriptorBase> descriptorsMap) {
    descriptorsMap.put("apply", new TypeConversionDescriptorBase());
  }

  @NotNull
  @Override
  public String ruleFromClass() {
    return "com.google.common.base.Function";
  }

  @NotNull
  @Override
  public String ruleToClass() {
    return JAVA_UTIL_FUNCTION_FUNCTION;
  }
}
