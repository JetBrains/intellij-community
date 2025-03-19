// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.ThrowableRunnable;
import one.util.streamex.IntStreamEx;
import org.intellij.lang.annotations.Language;

public final class JavaCompletionPerformanceTest extends LightFixtureCompletionTestCase {
  public void testPerformanceWithManyNonMatchingDeclarations() {
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
    Benchmark.newBenchmark(getName(), () -> {
      assertEquals(1, myFixture.completeBasic().length);
    }).setup(() -> {
      LookupImpl lookup = getLookup();
      if (lookup != null) lookup.hideLookup(true);
      myFixture.type("\bV");
      getPsiManager().dropPsiCaches();
      assertNull(getLookup());
    }).start();
  }

  public void testPerformanceWithManyMatchingStaticallyImportedDeclarations() {
    var fieldCount = 7000;

    @Language("JAVA") String constantClass =
      "interface Constants {" +
      IntStreamEx.range(fieldCount).mapToObj(n -> "String field" + n + " = \"x\";\n").joining() +
      "}";
    myFixture.addClass(constantClass);
    myFixture.configureByText("a.java", "import static Constants.*; class C { { field<caret>x } }");
    Benchmark.newBenchmark(getName(), () -> {
      int length = myFixture.completeBasic().length;
      assertTrue(String.valueOf(length), length > 100);
    }).setup(() -> {
      LookupImpl lookup = getLookup();
      if (lookup != null) lookup.hideLookup(true);
      myFixture.type("\bd");
      getPsiManager().dropPsiCaches();
      assertNull(getLookup());
    }).start();
  }

  public void testChainingPerformance() {
    Benchmark.newBenchmark(getTestName(false), (ThrowableRunnable<?>)() -> {
      myFixture.configureByText("Test.java", """
        class Foo extends javax.swing.JComponent {
        
            {
                String cl = <caret>
            }
        
        }
        """);
      complete();
      assertNotNull(myItems);
      LookupManager.getInstance(getProject()).hideActiveLookup();
    }).start();

  }
}
