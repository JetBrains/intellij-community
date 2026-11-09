// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.inheritance;

import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class AbstractMethodWithMissingImplementationsInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new AbstractMethodWithMissingImplementationsInspection());

  @Test
  public void testAbstractMethodWithMissingImplementations() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testDuplicateClass() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testSearchReturnsUnrelatedClass() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    ClassInheritorsSearch.EP_NAME.getPoint().registerExtension(
      (p, consumer) -> ContainerUtil.process(((PsiClassOwner)p.getClassToProcess().getContainingFile()).getClasses(), consumer), myFixture.getTestRootDisposable());
    inspectionFixture.get().doTest();
  }
}
