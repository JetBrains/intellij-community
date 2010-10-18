/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author peter
 */
class LookupReuseTest extends LightCodeInsightFixtureTestCase {
  @Override protected void setUp() {
    super.setUp()
    CompletionAutoPopupHandler.ourTestingAutopopup = true
  }

  @Override protected void tearDown() {
    CompletionAutoPopupHandler.ourTestingAutopopup = false
    super.tearDown()
  }

  private type(String s) {
    myFixture.type(s)
    ApplicationManager.application.invokeAndWait(({} as Runnable), ModalityState.current())
  }

  @Override protected void runTest() {
    runTestBare()
  }

  public void testNewItemsOnLongerPrefix() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(String iterable) {
          <caret>
        }
      }
    """)
    type('i')
    assertSameElements myFixture.lookupElementStrings, "iterable", "int"
    type('t')
    assertOrderedEquals myFixture.lookupElementStrings, "iterable"
    type('er')
    assertOrderedEquals myFixture.lookupElementStrings, "iter", "iterable"
  }

  public void testRecalculateItemsOnBackspace() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(String iterable) {
          int itaa;
          ite<caret>
        }
      }
    """)
    type "r"
    assertOrderedEquals myFixture.lookupElementStrings, "iter", "iterable"
    type '\b'
    assertOrderedEquals myFixture.lookupElementStrings, "iterable"
    type '\b'
    assertOrderedEquals myFixture.lookupElementStrings, "itaa", "iterable"
    type "a"
    assertOrderedEquals myFixture.lookupElementStrings, "itaa"
    type '\b'
    assertOrderedEquals myFixture.lookupElementStrings, "itaa", "iterable"
    type "e"
    assertOrderedEquals myFixture.lookupElementStrings, "iterable"
    type "r"
    assertOrderedEquals myFixture.lookupElementStrings, "iter", "iterable"
  }

}
