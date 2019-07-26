// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.TopLevelCompletionContributor;
import com.intellij.codeInsight.completion.TopLevelCompletionContributorEP;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JavaTopLevelCompletionContributorTest extends LightJavaCodeInsightFixtureTestCase {
  public void testBasics() {
    PsiFile file = myFixture.configureByText("Test.java", "class Foo {\n" +
                                                          "  void fooMethod() {}\n" +
                                                          "  int fooField = 0;\n" +
                                                          "  class FooClass {}\n" +
                                                          "}\n" +
                                                          "class Bar {\n" +
                                                          "  class Baz {}\n" +
                                                          "}");
    checkCompletion(file, "", 0, "Foo", "Bar");
    checkCompletion(file, "F", 1, "Foo", "fooMethod", "fooField", "FooClass");
    checkCompletion(file, "Foo::", 0, "fooMethod");
    checkCompletion(file, "Foo#", 0, "fooMethod", "fooField");
    checkCompletion(file, "Foo.", 0, "fooMethod", "fooField", "FooClass");
    checkCompletion(file, "B", 1, "Bar", "Baz");
  }

  private static void checkCompletion(PsiFile file, String prefix, int invocationCount, String... expected) {
    TopLevelCompletionContributor contributor = TopLevelCompletionContributorEP.forLanguage(JavaLanguage.INSTANCE);
    assertNotNull(contributor);
    List<String> options = new ArrayList<>();
    CompletionResultSet resultSet = CompletionServiceImpl.createResultSetForTest(
      option -> options.add(option.getLookupElement().getLookupString()));
    resultSet = resultSet.withPrefixMatcher(prefix);
    contributor.addLookupElements(file, invocationCount, resultSet);
    assertEquals(Arrays.asList(expected), options);
  }
}
