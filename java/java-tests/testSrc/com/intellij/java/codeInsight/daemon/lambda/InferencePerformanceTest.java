/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import one.util.streamex.IntStreamEx;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;

public class InferencePerformanceTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/performance";

  public void testPolyMethodCallArgumentPassedToVarargs() {
    PlatformTestUtil.startPerformanceTest("50 poly method calls passed to Arrays.asList", 4000, this::doTest).usesAllCPUCores().assertTiming();
  }

  public void testDiamondConstructorCallPassedToVarargs() {
    PlatformTestUtil.startPerformanceTest("50 diamond constructor calls passed to Arrays.asList", 12000, this::doTest).usesAllCPUCores().assertTiming();
  }

  public void testDiamondConstructorCallPassedToEnumConstantWithVarargs() {
    PlatformTestUtil.startPerformanceTest("10 enum constants with vararg diamonds", 12000, this::doTest).usesAllCPUCores().assertTiming();
  }

  public void testLeastUpperBoundWithLotsOfSupers() {
    PlatformTestUtil.startPerformanceTest("7 unrelated intersection conjuncts", 12000, this::doTest).usesAllCPUCores().assertTiming();
  }

  public void testVarArgPoly() {
    @Language("JAVA")
    String template = "import java.util.Map;\n" +
                      "\n" +
                      "class X {\n" +
                      "  " +
                      "public void foo() {\n" +
                      "    Map<Integer, Class<?>> map = ofEntries(\n" +
                      "      $entries$\n" +
                      "    );\n" +
                      "  }\n" +
                      "\n" +
                      "  static native <K, V> Map<K, V> ofEntries(Map.Entry<? extends K, ? extends V>... entries);\n" +
                      "  static native <K, V> Map.Entry<K, V> entry(K k, V v);\n" +
                      "}\n";
    int count = 70;
    String entries = IntStreamEx.range(count).mapToObj(i -> "entry(" + i + ", String.class)").joining(",\n      ");
    configureFromFileText("Test.java", template.replace("$entries$", entries));
    PlatformTestUtil.startPerformanceTest(count + " arguments to Map.ofEntries", 3000, () -> doHighlighting())
      .setup(() -> PsiManager.getInstance(getProject()).dropPsiCaches())
      .usesAllCPUCores().assertTiming();
    assertEmpty(highlightErrors());
  }

  public void testLongQualifierChainInsideLambda() {
    PlatformTestUtil.startPerformanceTest("long qualifier chain", 12000, this::doTest).usesAllCPUCores().assertTiming();
  }

  public void testLongQualifierChainInsideLambdaWithOverloads() {
    PlatformTestUtil.startPerformanceTest("long qualifier chain", 12000, () -> {
      configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
      PsiMethodCallExpression callExpression =
        PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiMethodCallExpression.class);
      assertNotNull(callExpression);
      assertNotNull(callExpression.getText(), callExpression.getType());
    }).assertTiming();
  }

  public void testLongQualifierChainInsideLambdaInTypeCast() {
    enableInspectionTool(new RedundantCastInspection());
     @Language("JAVA")
    String template = "import java.util.function.Function;\n" +
                      "abstract class Snippet {\n" +
                      "    abstract <V> V valueOf(V var2);\n" +
                      "    abstract <V> V valueOf(java.util.List<? extends V> var2);\n" +
                      "    static final class Builder {\n" +
                      "        public Builder foo(Foo foo) {\n" +
                      "            return this;\n" +
                      "        }\n" +
                      "        public Snippet build() {\n" +
                      "            return null;\n" +
                      "        }\n" +
                      "    }\n" +
                      "    public static final class Foo { }\n" +
                      "\n" +
                      "\n" +
                      "    public static final Function<Snippet, Snippet> _snippet = snippet -> {\n" +
                      "        Foo foo = new Foo();\n" +
                      "\n" +
                      "        return new Builder().\n" +
                      "                $chain$" +
                      "                .build();\n" +
                      "    };\n" +
                      "\n" +
                      "}";
    int count = 70;
    String entries = "foo(snippet.valueOf(foo))\n" +//to trick injection 
                     StringUtil.repeat(".foo(snippet.valueOf(foo))\n", count);
    configureFromFileText("Test.java", template.replace("$chain$", entries));
    PlatformTestUtil.startPerformanceTest(count + " chain in type cast", 3000, () -> doHighlighting())
      .setup(() -> PsiManager.getInstance(getProject()).dropPsiCaches())
      .usesAllCPUCores().assertTiming();
    assertEmpty(highlightErrors());
  }

  private void doTest() {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, getModule(), getTestRootDisposable());
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", false, false);
  }
}
