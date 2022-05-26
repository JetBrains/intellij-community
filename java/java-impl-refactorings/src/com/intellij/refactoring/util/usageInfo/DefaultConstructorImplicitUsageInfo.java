/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.refactoring.util.usageInfo;

import com.intellij.psi.*;
import com.intellij.usageView.UsageInfo;

/**
 * @author dsl
 */
public class DefaultConstructorImplicitUsageInfo extends UsageInfo {
  private final PsiMethod myOverridingConstructor;
  private final PsiClass myContainingClass;
  private final PsiMethod myBaseConstructor;

  public DefaultConstructorImplicitUsageInfo(PsiMethod overridingConstructor, PsiClass containingClass, PsiMethod baseConstructor) {
    super(overridingConstructor);
    myOverridingConstructor = overridingConstructor;
    myContainingClass = containingClass;
    myBaseConstructor = baseConstructor;
  }

  public PsiMethod getConstructor() {
    return myOverridingConstructor;
  }

  public PsiMethod getBaseConstructor() {
    return myBaseConstructor;
  }

  public PsiClass getContainingClass() {
    return myContainingClass;
  }
}
