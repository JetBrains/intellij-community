// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.completion.LightCompletionFixture;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.ThrowableRunnable;
import one.util.streamex.IntStreamEx;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionFixture;
import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PerformanceUnitTest
@StressTestApplication
@IndexingModeTemplate
public final class JavaCompletionPerformanceTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture();
  private final TestFixture<LightCompletionFixture> completion = lightCompletionFixture(projectFixture);

  @Test
  public void testPerformanceWithManyNonMatchingDeclarations(TestInfo testInfo) {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      for (int i = 0; i < 10; i++) {
        myFixture.addClass("class Foo" + i + " {\n" +
                           IntStreamEx.range(100).mapToObj(n -> "static int FOO" + n + " = 3;\n").joining() +
                           "}");
      }
      String text = IntStreamEx.range(10).mapToObj(i -> "import static Foo" + i + ".*;\n").joining() +
                    "class C {\n" +
                    IntStreamEx.range(100).mapToObj(n -> "String method" + n + "() {}\n").joining() +
                    "{ " +
                    "int localVariable = 2;\n" +
                    "localV<caret>x }" +
                    "}";
      myFixture.configureByText("a.java", text);
      Benchmark.newBenchmark(testInfo.getTestMethod().orElseThrow().getName(), () -> {
        assertEquals(1, myFixture.completeBasic().length);
      }).setup(() -> {
        LookupImpl lookup = completion.get().getLookup();
        if (lookup != null) lookup.hideLookup(true);
        myFixture.type("\bV");
        PsiManager.getInstance(myFixture.getProject()).dropPsiCaches();
        assertNull(completion.get().getLookup());
      }).start();
    });
  }

  @Test
  public void testPerformanceWithManyMatchingStaticallyImportedDeclarations(TestInfo testInfo) {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    var fieldCount = 7000;

    @Language("JAVA") String constantClass =
      "interface Constants {" +
      IntStreamEx.range(fieldCount).mapToObj(n -> "String field" + n + " = \"x\";\n").joining() +
      "}";
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass(constantClass);
      myFixture.configureByText("a.java", "import static Constants.*; class C { { field<caret>x } }");
      Benchmark.newBenchmark(testInfo.getTestMethod().orElseThrow().getName(), () -> {
        int length = myFixture.completeBasic().length;
        assertTrue(length > 100, String.valueOf(length));
      }).setup(() -> {
        LookupImpl lookup = completion.get().getLookup();
        if (lookup != null) lookup.hideLookup(true);
        myFixture.type("\bd");
        PsiManager.getInstance(myFixture.getProject()).dropPsiCaches();
        assertNull(completion.get().getLookup());
      }).start();
    });
  }

  @Test
  public void testChainingPerformance() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      Benchmark.newBenchmark(myFixture.getTestName(false), (ThrowableRunnable<?>)() -> {
        myFixture.configureByText("Test.java", """
          class Foo extends javax.swing.JComponent {
        
              {
                  String cl = <caret>
              }
        
          }
          """);
        completion.get().complete();
        assertNotNull(completion.get().getItems());
        LookupManager.getInstance(myFixture.getProject()).hideActiveLookup();
      }).start();

    });
  }
}
