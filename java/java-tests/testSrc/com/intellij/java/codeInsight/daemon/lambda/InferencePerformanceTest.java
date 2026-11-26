// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import one.util.streamex.IntStreamEx;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PerformanceUnitTest
@StressTestApplication
public class InferencePerformanceTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/performance";

  @Test
  public void testGenericMethodCallPassedToVarargs() {
    Benchmark.newBenchmark("200 poly method calls passed to Map.entry with type arguments", this::doTest).start();
  }

  @Test
  public void testPolyMethodCallArgumentPassedToVarargs() {
    Benchmark.newBenchmark("50 poly method calls passed to Arrays.asList", this::doTest).start();
  }

  @Test
  public void testDiamondConstructorCallPassedToVarargs() {
    Benchmark.newBenchmark("50 diamond constructor calls passed to Arrays.asList", this::doTest).start();
  }

  @Test
  public void testDiamondConstructorCallPassedToEnumConstantWithVarargs() {
    Benchmark.newBenchmark("10 enum constants with vararg diamonds", this::doTest).start();
  }

  @Test
  public void testLeastUpperBoundWithLotsOfSupers() {
    Benchmark.newBenchmark("7 unrelated intersection conjuncts", this::doTest).start();
  }

  @Test
  public void testVarArgPoly() {
    @Language("JAVA")
    String template = """
      import java.util.Map;

      class X {
        public void foo() {
          Map<Integer, Class<?>> map = ofEntries(
            $entries$
          );
        }

        static native <K, V> Map<K, V> ofEntries(Map.Entry<? extends K, ? extends V>... entries);
        static native <K, V> Map.Entry<K, V> entry(K k, V v);
      }
      """;
    int count = 70;
    String entries = IntStreamEx.range(count).mapToObj(i -> "entry(" + i + ", String.class)").joining(",\n      ");
    daemonFixture.get().configureFromFileText("Test.java", template.replace("$entries$", entries));
    Benchmark.newBenchmark(count + " arguments to Map.ofEntries", () -> daemonFixture.get().doHighlighting())
      .setup(() -> EdtTestUtil.runInEdtAndWait(() -> PsiManager.getInstance(daemonFixture.get().getFixture().getProject()).dropPsiCaches()))
      .start();
    List<HighlightInfo> errors = daemonFixture.get().highlightErrors();
    assertTrue(errors.isEmpty(), errors.toString());
  }

  @Test
  public void testLongQualifierChainInsideLambda() {
    Benchmark.newBenchmark("long qualifier chain", this::doTest).start();
  }

  @Test
  public void testLongQualifierChainInsideLambdaWithOverloads() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    Benchmark.newBenchmark("long qualifier chain", () -> {
      daemonFixture.get().configureByFile(BASE_PATH + "/" + myFixture.getTestName(false) + ".java");
      ReadAction.runBlocking(() -> {
        PsiMethodCallExpression callExpression =
          PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()),
                                      PsiMethodCallExpression.class);
        assertNotNull(callExpression);
        assertNotNull(callExpression.getType(), callExpression.getText());
      });
    }).start();
  }

  @Test
  public void testLongQualifierChainInsideLambdaInTypeCast() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    myFixture.enableInspections(new RedundantCastInspection());
     @Language("JAVA")
    String template = """
       import java.util.function.Function;
       abstract class Snippet {
           abstract <V> V valueOf(V var2);
           abstract <V> V valueOf(java.util.List<? extends V> var2);
           static final class Builder {
               public Builder foo(Foo foo) {
                   return this;
               }
               public Snippet build() {
                   return null;
               }
           }
           public static final class Foo { }


           public static final Function<Snippet, Snippet> _snippet = snippet -> {
               Foo foo = new Foo();

               return new Builder().
                       $chain$                .build();
           };

       }""";
    int count = 70;
    String entries = "foo(snippet.valueOf(foo))\n" +//to trick injection 
                     StringUtil.repeat(".foo(snippet.valueOf(foo))\n", count);
    daemonFixture.get().configureFromFileText("Test.java", template.replace("$chain$", entries));
    Benchmark.newBenchmark(count + " chain in type cast", () -> daemonFixture.get().doHighlighting())
      .setup(() -> EdtTestUtil.runInEdtAndWait(() -> PsiManager.getInstance(myFixture.getProject()).dropPsiCaches()))
      .start();
    List<HighlightInfo> errors = daemonFixture.get().highlightErrors();
    assertTrue(errors.isEmpty(), errors.toString());
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, myFixture.getModule(), myFixture.getTestRootDisposable());
    daemonFixture.get().doTest(BASE_PATH + "/" + myFixture.getTestName(false) + ".java", false, false);
  }
}
