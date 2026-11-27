// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@StressTestApplication
public class PsiModificationStressTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());

  @Test
  public void testManySmallPSIChangesDoNotCauseQuadraticRecomputationsOfWholeFileText() {
    int N = 100_000;
    String text = "  int field;\n".repeat(N);

    ReadAction.runBlocking(() -> {
      PsiClass aClass = PsiElementFactory.getInstance(projectFixture.get().getProject()).createClassFromText(text, null);
      assertFalse(aClass.isPhysical());
      PsiField firstField = aClass.getFields()[0];
      // would pass only if each PSI change does not cause recomputation of the whole file text in com.intellij.psi.impl.source.PsiJavaFileBaseImpl.getLanguageLevelInner
      for (int i = 0; i < N; i++) {
        firstField.setName("f" + i);
      }
      assertFalse(aClass.isPhysical());
      assertEquals(N, aClass.getFields().length);
    });
  }
}
