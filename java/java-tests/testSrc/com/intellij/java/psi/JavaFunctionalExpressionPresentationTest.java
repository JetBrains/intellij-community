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

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.impl.java.stubs.FunctionalExpressionStub;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.testFramework.IdeaTestUtil;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

public class JavaFunctionalExpressionPresentationTest extends CodeInsightTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(myProject).setLanguageLevel(LanguageLevel.JDK_1_8);
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return IdeaTestUtil.getMockJdk18();
  }

  public void testLambdaPresentation() {
    doTest("import java.util.*;" +
           "import java.util.function.BiFunction;" +
           "class A { BiFunction<Map<? extends String, ? super List<String>>, String, String> f = " +
           "(@NotNull Map<? extends String, ? super List<String>> x, final java.lang.String y) -> \"\";}",
           "(Map<? extends String, ? super List<String>> x, String y) -> {...}");
  }

  public void testLambdaPresentation2() {
    doTest("import java.util.*;" +
           "import java.util.function.Function;" +
           "class A { Function<String, String> f = " +
           "(str) -> \"\";}",
           "(str) -> {...}");

  }

  public void testLambdaPresentation3() {
    doTest("import java.util.*;" +
           "import java.util.function.Function;" +
           "class A { Function<String, String> f = " +
           "str -> \"\";}",
           "str -> {...}");

  }

  public void testLambdaEllipsisParameter() {
    doTest("import java.util.*;" +
           "import java.util.function.Function;" +
           "class A { Function<?, ?> f = " +
           "(String... s) -> s;}",
           "(String... s) -> {...}");
  }

  public void testLambdaArrayParameter() {
    doTest("import java.util.*;" +
           "import java.util.function.Function;" +
           "class A { Function<?, ?> f = " +
           "(String[] is) -> null;}",
           "(String[] is) -> {...}");
  }

  public void testLambdaWithPrimitiveParameter() {
    doTest("import java.util.*;" +
           "import java.util.function.Function;" +
           "class A { Function<?, ?> f = " +
           "(int[] i) -> null;}",
           "(int[] i) -> {...}");
  }

  public void testMethodReferencePresentation() {
    doTest("import java.util.Supplier; " +
           "class A { Supplier<String> s = java.util.Collections.<String>emptyEnumeration()::toString; }",
           "java.util.Collections.emptyEnumeration()::toString");
  }

  public void testMethodReferencePresentation2() {
    doTest("import java.util.Supplier; " +
           "class A { final static Integer NUMBER = 10; Supplier<String> r2 = A.NUMBER::toString;}",
           "A.NUMBER::toString");
  }

  private void doTest(@Language("JAVA") @NotNull String funExprText, @NotNull String expectedPresentableString) {
    final PsiFileImpl file = assertInstanceOf(configureByText(JavaFileType.INSTANCE, funExprText), PsiFileImpl.class);

    //stub based test
    final FunctionalExpressionStub functionalExpressionStub = StreamEx
      .of(file.calcStubTree().getPlainList())
      .select(FunctionalExpressionStub.class)
      .collect(MoreCollectors.onlyOne())
      .orElse(null);
    assertNotNull(functionalExpressionStub);
    assertEquals("Comparing with stub based rendering", expectedPresentableString, functionalExpressionStub.getPresentableText());

    //ast based test
    final PsiExpression psi = assertInstanceOf(functionalExpressionStub.getPsi(), PsiExpression.class);
    assertEquals("Comparing with AST based rendering", expectedPresentableString, PsiExpressionTrimRenderer.render(psi));
  }
}
