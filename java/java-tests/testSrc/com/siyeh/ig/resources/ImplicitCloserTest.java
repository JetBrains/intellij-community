// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.resources;

import com.intellij.codeInspection.resources.ImplicitResourceCloser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiVariable;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class ImplicitCloserTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new AutoCloseableResourceInspection());

  private static final ImplicitResourceCloser NAME_RESOURCE_CLOSER = new ImplicitResourceCloser() {
    @Override
    public boolean isSafelyClosed(@NotNull PsiVariable variable) {
      return "closed".equals(variable.getName());
    }
  };

  @Test
  public void testImplicitCloser() {
    inspectionFixture.get().doTest("""
             import java.io.*;

             class X {
               private static void example(int a) throws IOException {
                 FileInputStream closed = new FileInputStream("file1");
                 FileInputStream another = new <warning descr="'FileInputStream' used without 'try'-with-resources statement">FileInputStream</warning>("file2");
               }
             }""");
  }

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), ImplicitResourceCloser.EP_NAME, NAME_RESOURCE_CLOSER, myFixture.getTestRootDisposable());
  }
}
