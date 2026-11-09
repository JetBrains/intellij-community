// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.abstraction;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class PublicMethodNotExposedInInterfaceInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_11);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new PublicMethodNotExposedInInterfaceInspection());

  @Test
  public void testPublicMethodNotExposedInInterface() { inspectionFixture.get().doTest(); }
  @Test
  public void testPublicMethodNotExposedInInterfaceEnum() { inspectionFixture.get().doTest(); }
  @Test
  public void testRecursiveError() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final GlobalSearchScope scope = ProjectScope.getLibrariesScope(myFixture.getProject());
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myFixture.getProject());
    ReadAction.runBlocking(() -> {
      assertNotNull(facade.findClass("java.io.DataInput", scope));
      assertNotNull(facade.findClass("java.io.DataInputStream", scope));
      assertNotNull(facade.findClass("java.io.FilterInputStream", scope));
      assertNotNull(facade.findClass("java.io.InputStream", scope));
    });
    // without these classes present the following test will always succeed
    inspectionFixture.get().doTest();
  }
}
