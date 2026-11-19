// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.completion.PlainPrefixMatcher;
import com.intellij.codeInsight.completion.PlainTextSymbolCompletionContributor;
import com.intellij.codeInsight.completion.PlainTextSymbolCompletionContributorEP;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import one.util.streamex.StreamEx;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
@IndexingModeTemplate
public class JvmPlainTextSymbolCompletionContributorTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testBasics() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiFile file = myFixture.configureByText("Test.java", """
        class Foo {
          void fooMethod() {}
          int fooField = 0;
          class FooClass {}
        }
        class Bar {
          class Baz {
            void quxMethod() {}
          }
        }""");
      checkCompletion(file, "", 0, "Foo", "Bar");
      checkCompletion(file, "F", 1, "Foo", "fooMethod", "fooField", "FooClass");
      checkCompletion(file, "Foo::", 0, "Foo::fooMethod");
      checkCompletion(file, "Foo#", 0, "Foo#fooMethod", "Foo#fooField");
      checkCompletion(file, "Foo.", 0, "Foo.fooMethod", "Foo.fooField", "Foo.FooClass");
      checkCompletion(file, "B", 1, "Bar", "Baz");
      checkCompletion(file, "Bar.", 1, "Bar.Baz");
      checkCompletion(file, "Bar.Baz#", 1, "Bar.Baz#quxMethod");
    });
  }

  private static void checkCompletion(PsiFile file, String prefix, int invocationCount, String... expected) {
    PlainTextSymbolCompletionContributor contributor = PlainTextSymbolCompletionContributorEP.forLanguage(JavaLanguage.INSTANCE);
    assertNotNull(contributor);
    Collection<LookupElement> options = contributor.getLookupElements(file, invocationCount, prefix);
    PrefixMatcher matcher = new PlainPrefixMatcher(prefix);
    assertEquals(Arrays.asList(expected),
                 StreamEx.of(options).filter(matcher::prefixMatches).map(LookupElement::getLookupString).toList());
  }
}
