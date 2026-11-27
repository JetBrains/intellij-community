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
package com.intellij.java.psi;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.impl.java.stubs.FunctionalExpressionStub;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightSubFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class JavaFunctionalExpressionPresentationTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture =
    heavyJavaCodeInsightSubFixture("javaFunctionalExpressionPresentationFixture", context ->
      new HeavyJavaCodeInsightFixture(context, JavaTestUtil.getJavaTestDataPath()) {
        @Override
        protected @NotNull Sdk getTestProjectJdk() {
          return IdeaTestUtil.getMockJdk18();
        }

        @Override
        protected @NotNull LanguageLevel getProjectLanguageLevel() {
          return LanguageLevel.JDK_1_8;
        }
      });

  @Test
  public void testLambdaPresentation() {
    doTest("import java.util.*;" +
           "import java.util.function.BiFunction;" +
           "class A { BiFunction<Map<? extends String, ? super List<String>>, String, String> f = " +
           "(@NotNull Map<? extends String, ? super List<String>> x, final java.lang.String y) -> \"\";}",
           "(Map<? extends String, ? super List<String>> x, String y) -> {...}");
  }

  @Test
  public void testLambdaPresentation2() {
    doTest("import java.util.*;" +
           "import java.util.function.Function;" +
           "class A { Function<String, String> f = " +
           "(str) -> \"\";}",
           "(str) -> {...}");

  }

  @Test
  public void testLambdaPresentation3() {
    doTest("import java.util.*;" +
           "import java.util.function.Function;" +
           "class A { Function<String, String> f = " +
           "str -> \"\";}",
           "str -> {...}");

  }

  @Test
  public void testLambdaEllipsisParameter() {
    doTest("import java.util.*;" +
           "import java.util.function.Function;" +
           "class A { Function<?, ?> f = " +
           "(String... s) -> s;}",
           "(String... s) -> {...}");
  }

  @Test
  public void testLambdaArrayParameter() {
    doTest("import java.util.*;" +
           "import java.util.function.Function;" +
           "class A { Function<?, ?> f = " +
           "(String[] is) -> null;}",
           "(String[] is) -> {...}");
  }

  @Test
  public void testLambdaWithPrimitiveParameter() {
    doTest("import java.util.*;" +
           "import java.util.function.Function;" +
           "class A { Function<?, ?> f = " +
           "(int[] i) -> null;}",
           "(int[] i) -> {...}");
  }

  @Test
  public void testMethodReferencePresentation() {
    doTest("import java.util.Supplier; " +
           "class A { Supplier<String> s = java.util.Collections.<String>emptyEnumeration()::toString; }",
           "java.util.Collections.emptyEnumeration()::toString");
  }

  @Test
  public void testMethodReferencePresentation2() {
    doTest("import java.util.Supplier; " +
           "class A { final static Integer NUMBER = 10; Supplier<String> r2 = A.NUMBER::toString;}",
           "A.NUMBER::toString");
  }

  private void doTest(@Language("JAVA") @NotNull String funExprText, @NotNull String expectedPresentableString) {
    final PsiFileImpl file = assertInstanceOf(PsiFileImpl.class, heavyFixture.get().configureByText(JavaFileType.INSTANCE, funExprText));

    ReadAction.runBlocking(() -> {
      //stub based test
      final FunctionalExpressionStub functionalExpressionStub = StreamEx
        .of(file.calcStubTree().getPlainList())
        .select(FunctionalExpressionStub.class)
        .collect(MoreCollectors.onlyOne())
        .orElse(null);
      assertNotNull(functionalExpressionStub);
      assertEquals(expectedPresentableString, functionalExpressionStub.getPresentableText(), "Comparing with stub based rendering");

      //ast based test
      final PsiExpression psi = assertInstanceOf(PsiExpression.class, functionalExpressionStub.getPsi());
      assertEquals(expectedPresentableString, PsiExpressionTrimRenderer.render(psi), "Comparing with AST based rendering");
    });
  }
}
