/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.patterns;

import com.intellij.psi.PsiClass;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiClassNamePatternCondition extends PatternCondition<PsiClass> {

  private final ElementPattern<String> namePattern;

  public PsiClassNamePatternCondition(ElementPattern<String> pattern) {
    this("withQualifiedName", pattern);
  }

  public PsiClassNamePatternCondition(@Nullable String debugMethodName, ElementPattern<String> pattern) {
    super(debugMethodName);
    namePattern = pattern;
  }

  @Override
  public boolean accepts(@NotNull PsiClass aClass, ProcessingContext context) {
    return namePattern.accepts(aClass.getQualifiedName(), context);
  }

  public ElementPattern<String> getNamePattern() {
    return namePattern;
  }
}
