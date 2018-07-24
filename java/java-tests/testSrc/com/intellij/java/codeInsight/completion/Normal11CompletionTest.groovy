// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.testFramework.LightProjectDescriptor 

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

  private LookupElement completeVar(String text) {
    def fullText = "class F { $text }"
    myFixture.configureByText "a.java", fullText
    def var = myFixture.completeBasic().find { it.lookupString == 'var' }
    myFixture.checkResult(fullText)
    return var
  }
}