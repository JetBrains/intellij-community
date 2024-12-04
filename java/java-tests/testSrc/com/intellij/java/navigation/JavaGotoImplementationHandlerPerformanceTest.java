// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.navigation;

import com.intellij.codeInsight.navigation.GotoTargetHandler;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.intellij.lang.annotations.Language;

public class JavaGotoImplementationHandlerPerformanceTest extends JavaCodeInsightFixtureTestCase {
  public void testToStringOnUnqualifiedPerformance() {
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

     Benchmark.newBenchmark(getTestName(false), () -> {
       PsiElement[] impls = getTargets(file);
       assertEquals(3, impls.length);
     }).start();
  }

  public void testToStringOnQualifiedPerformance() {
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

    Benchmark.newBenchmark(getTestName(false), () -> {
      PsiElement[] impls = getTargets(file);
      assertEquals(3, impls.length);
    }).start();
  }

  private PsiElement[] getTargets(PsiFile file) {
    GotoTargetHandler.GotoData gotoData = CodeInsightTestUtil.gotoImplementation(myFixture.getEditor(), file);
    assertNotNull(gotoData);
    return gotoData.targets;
  }
}