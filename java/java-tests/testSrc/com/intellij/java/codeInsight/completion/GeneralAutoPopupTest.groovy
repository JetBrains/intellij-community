// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionAutoPopupTestCase

/**
 * For tests checking platform behavior not related to Java language (but they may still use Java for code samples)
 */
class GeneralAutoPopupTest extends CompletionAutoPopupTestCase {
  void "test no autopopup in the middle of word when the only variant is already in the editor"() {
    myFixture.configureByText 'a.java', 'class Foo { private boolean ignoredProperty; public boolean isIgnoredP<caret>operty() {}}'
    type 'r'
    assert !lookup
  }
}
