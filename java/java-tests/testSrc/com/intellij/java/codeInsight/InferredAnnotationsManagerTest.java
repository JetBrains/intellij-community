// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.InferredAnnotationsManager;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public final class InferredAnnotationsManagerTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_25;
  }

  public void testNoInferenceOnOptionalOf() {
    PsiClass optionalClass = myFixture.findClass(CommonClassNames.JAVA_UTIL_OPTIONAL);
    assertNotNull(optionalClass);
    PsiMethod[] ofMethods = optionalClass.findMethodsByName("of", false);
    assertEquals(1, ofMethods.length);
    PsiMethod method = ofMethods[0];
    PsiAnnotation[] annotations = InferredAnnotationsManager.getInstance(getProject()).findInferredAnnotations(method);
    // Should not infer NotNull, as there's an external annotation
    assertEquals(1, annotations.length);
    assertEquals("org.jetbrains.annotations.Contract", annotations[0].getQualifiedName());
  }
}
