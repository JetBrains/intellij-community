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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public enum GuavaLambda {
  PREDICATE("com.google.common.base.Predicate", "java.util.function.Predicate", "apply", "test"),
  FUNCTION("com.google.common.base.Function", "java.util.function.Function", "apply", "apply"),
  SUPPLIER("com.google.common.base.Supplier", "java.util.function.Supplier", "get", "get");

  private final String myClassQName;
  private final String myJavaAnalogueClassQName;
  private final String mySamName;
  private final String myJavaAnalogueSamName;

  GuavaLambda(String classQName, String javaAnalogueClassQName, String samName, String javaAnalogueSamName) {
    myClassQName = classQName;
    myJavaAnalogueClassQName = javaAnalogueClassQName;
    mySamName = samName;
    myJavaAnalogueSamName = javaAnalogueSamName;
  }

  public String getClassQName() {
    return myClassQName;
  }

  public String getJavaAnalogueClassQName() {
    return myJavaAnalogueClassQName;
  }

  public String getSamName() {
    return mySamName;
  }

  public String getJavaAnalogueSamName() {
    return myJavaAnalogueSamName;
  }

  @Nullable
  static GuavaLambda findFor(@Nullable PsiType type) {
    final PsiClass aClass = PsiTypesUtil.getPsiClass(type);
    if (aClass == null) return null;
    for (GuavaLambda lambda : values()) {
      if (InheritanceUtil.isInheritor(aClass, lambda.getClassQName())) {
        return lambda;
      }
    }
    return null;
  }

  @Nullable
  static GuavaLambda findJavaAnalogueFor(@Nullable PsiType type) {
    final PsiClass aClass = PsiTypesUtil.getPsiClass(type);
    if (aClass == null) return null;
    for (GuavaLambda lambda : values()) {
      if (InheritanceUtil.isInheritor(aClass, lambda.getJavaAnalogueClassQName())) {
        return lambda;
      }
    }
    return null;
  }
}
