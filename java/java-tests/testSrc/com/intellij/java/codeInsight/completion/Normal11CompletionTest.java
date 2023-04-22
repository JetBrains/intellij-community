// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class Normal11CompletionTest extends NormalCompletionTestCase {
  public void test_local_var() {
    assertNotNull(completeVar("{ <caret> }"));
  }

  public void test_local_final_var() {
    assertNotNull(completeVar("{ final <caret> }"));
  }

  public void test_local_var_before_identifier() {
    assertNotNull(completeVar("{ <caret>name }"));
  }

  public void test_resource_var() {
    assertNotNull(completeVar("{ try (<caret>) }"));
  }

  public void test_empty_foreach_var() {
    assertNotNull(completeVar("{ for (<caret>) }"));
  }

  public void test_non_empty_foreach_var() {
    assertNotNull(completeVar("{ for (<caret>x y: z) }"));
  }

  public void test_lambda_parameter_var() {
    assertNotNull(completeVar("I f = (v<caret>x) -> {})"));
  }

  public void test_no_var_in_lambda_parameter_name() {
    assertNull(completeVar("I f = (String v<caret>x) -> {};"));
  }

  public void test_no_var_in_method_parameters() {
    assertNull(completeVar("void foo(<caret>) {}"));
  }

  public void test_no_var_in_catch() {
    assertNull(completeVar("{ try {} catch(<caret>) {} }"));
  }

  @NeedsIndex.ForStandardLibrary(reason = "To display 'equals' suggestion")
  public void test_var_initializer_type() {
    String source = "class X {boolean test() {var x = <caret>; return x;}}";
    myFixture.configureByText("Test.java", source);
    myFixture.complete(CompletionType.SMART);
    assertEquals(myFixture.getLookupElementStrings(), Arrays.asList("equals", "false", "true", "test"));
  }

  @NeedsIndex.ForStandardLibrary(reason = "To display 'equals' suggestion")
  public void test_var_initializer_type_3() {
    String source = "class X {boolean test() {var x = <caret>;var y = x;var z = y; return z;}}";
    myFixture.configureByText("Test.java", source);
    myFixture.complete(CompletionType.SMART);
    assertEquals(myFixture.getLookupElementStrings(), Arrays.asList("equals", "false", "true", "test"));
  }

  @NeedsIndex.ForStandardLibrary(reason = "To display 'equals' suggestion")
  public void test_var_initializer_type_4() {
    String source = "class X {boolean test() {var x = <caret>;var y = x;var z = y;var w = z;return w;}}";
    myFixture.configureByText("Test.java", source);
    myFixture.complete(CompletionType.SMART);
    // too many hops to track `x` type
    assertEquals(myFixture.getLookupElementStrings(), List.of());
  }

  @NeedsIndex.ForStandardLibrary(reason = "To display 'equals' suggestion")
  public void test_var_initializer_type_used_twice() {
    String source = "class X {boolean test() {var x = <caret>;if (Math.random() > 0.5) return x;return x;}}";
    myFixture.configureByText("Test.java", source);
    myFixture.complete(CompletionType.SMART);
    assertEquals(myFixture.getLookupElementStrings(),Arrays.asList("equals", "false", "true", "test"));
  }

  private LookupElement completeVar(String text) {
    String fullText = "class F { " + text + " }";
    myFixture.configureByText("a.java", fullText);
    LookupElement var = ContainerUtil.find(myFixture.completeBasic(), it -> it.getLookupString().equals("var"));
    myFixture.checkResult(fullText);
    return var;
  }

  @NeedsIndex.ForStandardLibrary
  public void testToUnmodifiable() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "collect(Collectors.toUnmodifiableList())", "collect(Collectors.toUnmodifiableSet())");
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_11;
  }
}
