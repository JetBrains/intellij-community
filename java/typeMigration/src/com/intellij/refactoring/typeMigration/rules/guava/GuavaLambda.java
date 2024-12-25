// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  PREDICATE("com.google.common.base.Predicate", "java.util.function.Predicate", "apply", "test", 1),
  FUNCTION("com.google.common.base.Function", "java.util.function.Function", "apply", "apply", 1),
  SUPPLIER("com.google.common.base.Supplier", "java.util.function.Supplier", "get", "get", 0);

  private final String myClassQName;
  private final String myJavaAnalogueClassQName;
  private final String mySamName;
  private final String myJavaAnalogueSamName;
  private final int myParametersCount;

  GuavaLambda(String classQName, String javaAnalogueClassQName, String samName, String javaAnalogueSamName, int count) {
    myClassQName = classQName;
    myJavaAnalogueClassQName = javaAnalogueClassQName;
    mySamName = samName;
    myJavaAnalogueSamName = javaAnalogueSamName;
    myParametersCount = count;
  }

  public int getParametersCount() {
    return myParametersCount;
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

  static @Nullable GuavaLambda findFor(@Nullable PsiType type) {
    final PsiClass aClass = PsiTypesUtil.getPsiClass(type);
    if (aClass == null) return null;
    for (GuavaLambda lambda : values()) {
      if (InheritanceUtil.isInheritor(aClass, lambda.getClassQName())) {
        if (PREDICATE != lambda && InheritanceUtil.isInheritor(aClass, lambda.getJavaAnalogueClassQName())) {
          return null;
        }
        return lambda;
      }
    }
    return null;
  }

  static @Nullable GuavaLambda findJavaAnalogueFor(@Nullable PsiType type) {
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
