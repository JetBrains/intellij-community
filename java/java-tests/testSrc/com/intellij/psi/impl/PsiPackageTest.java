// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiPackage;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class PsiPackageTest extends LightJavaCodeInsightFixtureTestCase {
  private static final LightProjectDescriptor PROJECT_DESCRIPTOR = new DefaultLightProjectDescriptor(() -> IdeaTestUtil.getMockJdk21(), List.of("com.google.guava:guava:32.1.2-jre"));
  
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return PROJECT_DESCRIPTOR;
  }

  public void testPackageAnnotation() {
    PsiPackage psiPackage = JavaPsiFacade.getInstance(getProject()).findPackage("com.google.common.collect");
    assertNotNull(psiPackage);
    PsiModifierList list = psiPackage.getAnnotationList();
    assertNotNull(list);
    PsiAnnotation[] annotations = list.getAnnotations();
    assertSize(2, annotations);
  }
}
