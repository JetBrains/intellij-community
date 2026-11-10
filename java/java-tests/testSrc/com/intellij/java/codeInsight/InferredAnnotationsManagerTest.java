// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.InferredAnnotationsManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public final class InferredAnnotationsManagerTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_25);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testNoInferenceOnOptionalOf() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ReadAction.runBlocking(() -> {
      PsiClass optionalClass = myFixture.findClass(CommonClassNames.JAVA_UTIL_OPTIONAL);
      assertNotNull(optionalClass);
      PsiMethod[] ofMethods = optionalClass.findMethodsByName("of", false);
      assertEquals(1, ofMethods.length);
      PsiMethod method = ofMethods[0];
      PsiAnnotation[] annotations = InferredAnnotationsManager.getInstance(myFixture.getProject()).findInferredAnnotations(method);
      // Should not infer NotNull, as there's an external annotation
      assertEquals(1, annotations.length);
      assertEquals("org.jetbrains.annotations.Contract", annotations[0].getQualifiedName());
    });
  }
}
