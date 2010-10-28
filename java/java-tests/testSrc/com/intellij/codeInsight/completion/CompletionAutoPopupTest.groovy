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
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.ui.UIUtil

 /**
 * @author peter
 */
class CompletionAutoPopupTest extends LightCodeInsightFixtureTestCase {
  @Override protected void setUp() {
    UIUtil.invokeAndWaitIfNeeded(new Runnable(){
                                 @Override
                                 void run() {
                                   superSetUp()
                                 }

                                 })
    CompletionAutoPopupHandler.ourTestingAutopopup = true
  }
  void superSetUp() {
    super.setUp()
  }
  void superTearDown() {
    super.tearDown()
  }

  @Override protected void tearDown() {
    CompletionAutoPopupHandler.ourTestingAutopopup = false
    UIUtil.invokeAndWaitIfNeeded(new Runnable(){
                                 @Override
                                 void run() {
                                   superTearDown()
                                 }

                                 })
  }

  private type(String s) {
    myFixture.type(s)
    UIUtil.invokeAndWaitIfNeeded(({} as Runnable))
  }

  @Override protected void runTest() {
    runTestBare()
  }

  @Override
  void runBare() {
    superRunBare()
  }


  @Override protected void invokeTestRunnable(Runnable runnable) {
    runnable.run()
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
    assertSameElements myFixture.lookupElementStrings, "if", "iterable", "int"
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

  public void testNoAutopopupInTheMiddleOfIdentifier() {
    myFixture.configureByText("a.java", """
      class Foo {
        String foo(String iterable) {
          return it<caret>rable;
        }
      }
    """)
    type 'e'
    assertNull LookupManager.getActiveLookup(myFixture.getEditor())
  }

  public void testGenerallyFocusLookupInJavaMethod() {
        myFixture.configureByText("a.java", """
      class Foo {
        String foo(String iterable) {
          return it<caret>;
        }
      }
    """)
    type 'e'
    final def lookup = LookupManager.getActiveLookup(myFixture.getEditor())
    assertNotNull lookup
    assertTrue lookup.focused
  }

  public void testNoLookupFocusInJavaVariable() {
        myFixture.configureByText("a.java", """
      class Foo {
        String foo(String st<caret>) {
        }
      }
    """)
    type 'r'
    final def lookup = LookupManager.getActiveLookup(myFixture.getEditor())
    assertNotNull lookup
    assertFalse lookup.focused
  }

}
