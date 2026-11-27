// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.navigation;

import com.intellij.codeInsight.navigation.GotoTargetHandler;
import com.intellij.openapi.application.PathManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@PerformanceUnitTest
@StressTestApplication
public class JavaGotoImplementationHandlerPerformanceTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture = javaModuleCodeInsightFixture(PathManager.getHomePath());

  private String testName;

  @BeforeEach
  void setUpTestName(TestInfo testInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), false);
  }

  @Test
  public void testToStringOnUnqualifiedPerformance() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA") @SuppressWarnings("ALL")
    String fileText = "public class Fix {\n" +
                  "    {\n" +
                  "        <caret>toString();\n" +
                  "    }\n" +
                  "}\n" +
                  "class FixImpl1 extends Fix {\n" +
                  "    @Override\n" +
                  "    public String toString() {\n" +
                  "        return \"Impl1\";\n" +
                  "    }\n" +
                  "}\n" +
                  "class FixImpl2 extends Fix {\n" +
                  "    @Override\n" +
                  "    public String toString() {\n" +
                  "        return \"Impl2\";\n" +
                  "    }\n" +
                  "}";
    final PsiFile file = myFixture.addFileToProject("Foo.java", fileText);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

     Benchmark.newBenchmark(testName, () -> {
       PsiElement[] impls = getTargets(file);
       assertEquals(3, impls.length);
     }).start();
  }

  @Test
  public void testToStringOnQualifiedPerformance() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @SuppressWarnings("ALL") @Language("JAVA")
    String fileText = "public class Fix {\n" +
                  "    {\n" +
                  "        Fix ff = getFix();\n" +
                  "        ff.<caret>toString();\n" +
                  "    }\n" +
                  "    \n" +
                  "    Fix getFix() {return new FixImpl1();}\n" +
                  "}\n" +
                  "class FixImpl1 extends Fix {\n" +
                  "    @Override\n" +
                  "    public String toString() {\n" +
                  "        return \"Impl1\";\n" +
                  "    }\n" +
                  "}\n" +
                  "class FixImpl2 extends Fix {\n" +
                  "    @Override\n" +
                  "    public String toString() {\n" +
                  "        return \"Impl2\";\n" +
                  "    }\n" +
                  "}";
    final PsiFile file = myFixture.addFileToProject("Foo.java", fileText);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    Benchmark.newBenchmark(testName, () -> {
      PsiElement[] impls = getTargets(file);
      assertEquals(3, impls.length);
    }).start();
  }

  private PsiElement[] getTargets(PsiFile file) {
    GotoTargetHandler.GotoData gotoData = EdtTestUtil.runInEdtAndGet(() -> CodeInsightTestUtil.gotoImplementation(codeInsightFixture.get().getEditor(), file));
    assertNotNull(gotoData);
    return gotoData.targets;
  }
}