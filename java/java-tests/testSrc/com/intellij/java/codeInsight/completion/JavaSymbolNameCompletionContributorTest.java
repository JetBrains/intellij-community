// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.completion.PlainPrefixMatcher;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.SymbolNameCompletionContributor;
import com.intellij.codeInsight.completion.SymbolNameCompletionContributorEP;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import one.util.streamex.StreamEx;

import java.util.Arrays;
import java.util.Collection;

public class JavaSymbolNameCompletionContributorTest extends LightJavaCodeInsightFixtureTestCase {
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
    checkCompletion(file, "Foo::", 0, "Foo::fooMethod");
    checkCompletion(file, "Foo#", 0, "Foo#fooMethod", "Foo#fooField");
    checkCompletion(file, "Foo.", 0, "Foo.fooMethod", "Foo.fooField", "Foo.FooClass");
    checkCompletion(file, "B", 1, "Bar", "Baz");
  }

  private static void checkCompletion(PsiFile file, String prefix, int invocationCount, String... expected) {
    SymbolNameCompletionContributor contributor = SymbolNameCompletionContributorEP.forLanguage(JavaLanguage.INSTANCE);
    assertNotNull(contributor);
    Collection<LookupElement> options = contributor.getLookupElements(file, invocationCount, prefix);
    PrefixMatcher matcher = new PlainPrefixMatcher(prefix);
    assertEquals(Arrays.asList(expected),
                 StreamEx.of(options).filter(matcher::prefixMatches).map(LookupElement::getLookupString).toList());
  }
}
