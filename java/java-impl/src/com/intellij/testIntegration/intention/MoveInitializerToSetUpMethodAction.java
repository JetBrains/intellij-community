// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testIntegration.intention;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInsight.intention.impl.BaseMoveInitializerToMethodAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.testIntegration.JavaTestFramework;
import com.intellij.testIntegration.TestFramework;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public final class MoveInitializerToSetUpMethodAction extends BaseMoveInitializerToMethodAction {
  private static final Logger LOG = Logger.getInstance(MoveInitializerToSetUpMethodAction.class);

  @Override
  @NotNull
  public String getText() {
    return JavaBundle.message("intention.move.initializer.to.set.up");
  }

  @Override
  public boolean isAvailable(@NotNull PsiField field) {
    if (!super.isAvailable(field)) return false;
    final PsiClass aClass = field.getContainingClass();
    LOG.assertTrue(aClass != null);
    TestFramework testFramework = TestFrameworks.detectFramework(aClass);
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(aClass.getProject());
    if (!(testFramework instanceof JavaTestFramework javaTestFramework)) return false;
    try {
      javaTestFramework.createSetUpPatternMethod(elementFactory);
      return testFramework.isTestClass(aClass) || testFramework.findSetUpMethod(aClass) instanceof PsiMethod;
    }
    catch (Exception e) {
      return false;
    }
  }

  @NotNull
  @Override
  protected Collection<String> getUnsuitableModifiers() {
    return Arrays.asList(PsiModifier.STATIC, PsiModifier.FINAL);
  }

  @NotNull
  @Override
  protected Collection<PsiMethod> getOrCreateMethods(@NotNull PsiClass aClass) {
    TestFramework testFramework = TestFrameworks.detectFramework(aClass);
    PsiElement setUpMethod = null;
    if (testFramework != null) {
      setUpMethod = testFramework.findSetUpMethod(aClass);
      if (setUpMethod == null) {
        setUpMethod = testFramework.findOrCreateSetUpMethod(aClass);
      }
    }
    return setUpMethod instanceof PsiMethod ? Collections.singletonList((PsiMethod)setUpMethod) : Collections.emptyList();
  }
}
