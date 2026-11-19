// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static com.intellij.java.codeInsight.completion.NormalCompletionFixtureKt.normalCompletionFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
@IndexingModeTemplate
public class Normal11CompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_11);
  private final TestFixture<NormalCompletionFixture> completion = normalCompletionFixture(projectFixture);

  @Test
  public void test_local_var() {
    EdtTestUtil.runInEdtAndWait(() -> assertNotNull(completeVar("{ <caret> }")));
  }

  @Test
  public void test_local_final_var() {
    EdtTestUtil.runInEdtAndWait(() -> assertNotNull(completeVar("{ final <caret> }")));
  }

  @Test
  public void test_local_var_before_identifier() {
    EdtTestUtil.runInEdtAndWait(() -> assertNotNull(completeVar("{ <caret>name }")));
  }

  @Test
  public void test_resource_var() {
    EdtTestUtil.runInEdtAndWait(() -> assertNotNull(completeVar("{ try (<caret>) }")));
  }

  @Test
  public void test_empty_foreach_var() {
    EdtTestUtil.runInEdtAndWait(() -> assertNotNull(completeVar("{ for (<caret>) }")));
  }

  @Test
  public void test_non_empty_foreach_var() {
    EdtTestUtil.runInEdtAndWait(() -> assertNotNull(completeVar("{ for (<caret>x y: z) }")));
  }

  @Test
  public void test_lambda_parameter_var() {
    EdtTestUtil.runInEdtAndWait(() -> assertNotNull(completeVar("I f = (v<caret>x) -> {})")));
  }

  @Test
  public void test_no_var_in_lambda_parameter_name() {
    EdtTestUtil.runInEdtAndWait(() -> assertNull(completeVar("I f = (String v<caret>x) -> {};")));
  }

  @Test
  public void test_no_var_in_method_parameters() {
    EdtTestUtil.runInEdtAndWait(() -> assertNull(completeVar("void foo(<caret>) {}")));
  }

  @Test
  public void test_no_var_in_catch() {
    EdtTestUtil.runInEdtAndWait(() -> assertNull(completeVar("{ try {} catch(<caret>) {} }")));
  }

  @Test
  @NeedsIndex.ForStandardLibrary(reason = "To display 'equals' suggestion")
  public void test_var_initializer_type() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    String source = "class X {boolean test() {var x = <caret>; return x;}}";
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", source);
      myFixture.complete(CompletionType.SMART);
      assertEquals(myFixture.getLookupElementStrings(), Arrays.asList("equals", "false", "true", "test"));
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary(reason = "To display 'equals' suggestion")
  public void test_var_initializer_type_3() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    String source = "class X {boolean test() {var x = <caret>;var y = x;var z = y; return z;}}";
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", source);
      myFixture.complete(CompletionType.SMART);
      assertEquals(myFixture.getLookupElementStrings(), Arrays.asList("equals", "false", "true", "test"));
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary(reason = "To display 'equals' suggestion")
  public void test_var_initializer_type_4() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    String source = "class X {boolean test() {var x = <caret>;var y = x;var z = y;var w = z;return w;}}";
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", source);
      myFixture.complete(CompletionType.SMART);
      // too many hops to track `x` type
      assertEquals(myFixture.getLookupElementStrings(), List.of());
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary(reason = "To display 'equals' suggestion")
  public void test_var_initializer_type_used_twice() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    String source = "class X {boolean test() {var x = <caret>;if (Math.random() > 0.5) return x;return x;}}";
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", source);
      myFixture.complete(CompletionType.SMART);
      assertEquals(myFixture.getLookupElementStrings(),Arrays.asList("equals", "false", "true", "test"));
    });
  }

  private LookupElement completeVar(String text) {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    String fullText = "class F { " + text + " }";
    myFixture.configureByText("a.java", fullText);
    LookupElement var = ContainerUtil.find(myFixture.completeBasic(), it -> it.getLookupString().equals("var"));
    myFixture.checkResult(fullText);
    return var;
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testToUnmodifiable() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "collect(Collectors.toUnmodifiableList())", "collect(Collectors.toUnmodifiableSet())");
    });
  }
}
