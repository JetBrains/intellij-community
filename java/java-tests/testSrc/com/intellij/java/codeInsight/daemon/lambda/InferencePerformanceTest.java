// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import one.util.streamex.IntStreamEx;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;

public class InferencePerformanceTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/performance";

  public void testGenericMethodCallPassedToVarargs() {
    Benchmark.newBenchmark("200 poly method calls passed to Map.entry with type arguments", this::doTest).start();
  }

  public void testPolyMethodCallArgumentPassedToVarargs() {
    Benchmark.newBenchmark("50 poly method calls passed to Arrays.asList", this::doTest).start();
  }

  public void testDiamondConstructorCallPassedToVarargs() {
    Benchmark.newBenchmark("50 diamond constructor calls passed to Arrays.asList", this::doTest).start();
  }

  public void testDiamondConstructorCallPassedToEnumConstantWithVarargs() {
    Benchmark.newBenchmark("10 enum constants with vararg diamonds", this::doTest).start();
  }

  public void testLeastUpperBoundWithLotsOfSupers() {
    Benchmark.newBenchmark("7 unrelated intersection conjuncts", this::doTest).start();
  }

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
    configureFromFileText("Test.java", template.replace("$entries$", entries));
    Benchmark.newBenchmark(count + " arguments to Map.ofEntries", () -> doHighlighting())
      .setup(() -> PsiManager.getInstance(getProject()).dropPsiCaches())
      .start();
    assertEmpty(highlightErrors());
  }

  public void testLongQualifierChainInsideLambda() {
    Benchmark.newBenchmark("long qualifier chain", this::doTest).start();
  }

  public void testLongQualifierChainInsideLambdaWithOverloads() {
    Benchmark.newBenchmark("long qualifier chain", () -> {
      configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
      PsiMethodCallExpression callExpression =
        PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiMethodCallExpression.class);
      assertNotNull(callExpression);
      assertNotNull(callExpression.getText(), callExpression.getType());
    }).start();
  }

  public void testLongQualifierChainInsideLambdaInTypeCast() {
    enableInspectionTool(new RedundantCastInspection());
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
    configureFromFileText("Test.java", template.replace("$chain$", entries));
    Benchmark.newBenchmark(count + " chain in type cast", () -> doHighlighting())
      .setup(() -> PsiManager.getInstance(getProject()).dropPsiCaches())
      .start();
    assertEmpty(highlightErrors());
  }

  private void doTest() {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, getModule(), getTestRootDisposable());
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", false, false);
  }
}
