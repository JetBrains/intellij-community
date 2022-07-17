// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.NeedsIndex
import groovy.transform.CompileStatic

@CompileStatic
class Normal11CompletionTest extends NormalCompletionTestCase {
  final LightProjectDescriptor projectDescriptor = JAVA_11

  void "test local var"() {
    assert completeVar("{ <caret> }")
  }

  void "test local final var"() {
    assert completeVar("{ final <caret> }")
  }

  void "test local var before identifier"() {
    assert completeVar("{ <caret>name }")
  }

  void "test resource var"() {
    assert completeVar("{ try (<caret>) }")
  }

  void "test empty foreach var"() {
    assert completeVar("{ for (<caret>) }")
  }

  void "test non-empty foreach var"() {
    assert completeVar("{ for (<caret>x y: z) }")
  }

  void "test lambda parameter var"() {
    assert completeVar("I f = (v<caret>x) -> {};")
  }

  void "test no var in lambda parameter name"() {
    assert !completeVar("I f = (String v<caret>x) -> {};")
  }

  void "test no var in method parameters"() {
    assert !completeVar("void foo(<caret>) {}")
  }

  void "test no var in catch"() {
    assert !completeVar("{ try {} catch(<caret>) {} }")
  }

  @NeedsIndex.ForStandardLibrary(reason = "To display 'equals' suggestion")
  void "test var initializer type"() {
    def source = "class X {boolean test() {var x = <caret>; return x;}}"
    myFixture.configureByText "Test.java", source
    myFixture.complete(CompletionType.SMART)
    assert myFixture.lookupElementStrings == ['equals', 'false', 'true', 'test']
  }

  @NeedsIndex.ForStandardLibrary(reason = "To display 'equals' suggestion")
  void "test var initializer type 3"() {
    def source = "class X {boolean test() {var x = <caret>;var y = x;var z = y; return z;}}"
    myFixture.configureByText "Test.java", source
    myFixture.complete(CompletionType.SMART)
    assert myFixture.lookupElementStrings == ['equals', 'false', 'true', 'test']
  }

  @NeedsIndex.ForStandardLibrary(reason = "To display 'equals' suggestion")
  void "test var initializer type 4"() {
    def source = "class X {boolean test() {var x = <caret>;var y = x;var z = y;var w = z;return w;}}"
    myFixture.configureByText "Test.java", source
    myFixture.complete(CompletionType.SMART)
    // too many hops to track `x` type
    assert myFixture.lookupElementStrings == []
  }

  @NeedsIndex.ForStandardLibrary(reason = "To display 'equals' suggestion")
  void "test var initializer type used twice"() {
    def source = "class X {boolean test() {var x = <caret>;if (Math.random() > 0.5) return x;return x;}}"
    myFixture.configureByText "Test.java", source
    myFixture.complete(CompletionType.SMART)
    assert myFixture.lookupElementStrings == ['equals', 'false', 'true', 'test']
  }

  private LookupElement completeVar(String text) {
    def fullText = "class F { $text }"
    myFixture.configureByText "a.java", fullText
    def var = myFixture.completeBasic().find { it.lookupString == 'var' }
    myFixture.checkResult(fullText)
    return var
  }

  @NeedsIndex.ForStandardLibrary
  void testToUnmodifiable() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, 'collect(Collectors.toUnmodifiableList())', 'collect(Collectors.toUnmodifiableSet())'
  }

}