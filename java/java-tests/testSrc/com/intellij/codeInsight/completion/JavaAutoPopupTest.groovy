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

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.CommandProcessor
import com.intellij.ide.ui.UISettings

/**
 * @author peter
 */
class JavaAutoPopupTest extends CompletionAutoPopupTestCase {

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
    assertEquals 'iterable', lookup.currentItem.lookupString

    type('er')
    assertOrderedEquals myFixture.lookupElementStrings, "iter", "iterable"
    assertEquals 'iter', lookup.currentItem.lookupString
    assert !lookup.focused

    type 'a'
    assert lookup.focused
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

  public void testExplicitSelectionShouldSurvive() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(String iterable) {
          int iterable2;
          it<caret>
        }
      }
    """)
    type "e"
    assertOrderedEquals myFixture.lookupElementStrings, "iterable", "iterable2"

    assertEquals 'iterable', lookup.currentItem.lookupString
    myFixture.performEditorAction IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN
    assertEquals 'iterable2', lookup.currentItem.lookupString

    type "r"
    assertOrderedEquals myFixture.lookupElementStrings, "iter", "iterable", 'iterable2'
    assertEquals 'iterable2', lookup.currentItem.lookupString

  }

  public void testExplicitMouseSelectionShouldSurvive() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(String iterable) {
          int iterable2;
          it<caret>
        }
      }
    """)
    type "e"
    assertOrderedEquals myFixture.lookupElementStrings, "iterable", "iterable2"

    assertEquals 'iterable', lookup.currentItem.lookupString
    lookup.currentItem = lookup.items[1]
    assertEquals 'iterable2', lookup.currentItem.lookupString

    type "r"
    assertOrderedEquals myFixture.lookupElementStrings, "iter", "iterable", 'iterable2'
    assertEquals 'iterable2', lookup.currentItem.lookupString

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
    assertNull lookup
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
    assertFalse lookup.focused
  }

  public void testNoStupidNameSuggestions() {
    myFixture.configureByText("a.java", """
      class Foo {
        String foo(String <caret>) {
        }
      }
    """)
    type 'r'
    assertNull lookup
  }

  public void testExplicitSelectionShouldBeHonoredFocused() {
    myFixture.configureByText("a.java", """
      class Foo {
        String foo() {
          int abcd;
          int abce;
          a<caret>
        }
      }
    """)
    type 'b'
    assert lookup.focused
    type 'c'

    assertOrderedEquals myFixture.lookupElementStrings, 'abcd', 'abce'
    assertEquals 'abcd', lookup.currentItem.lookupString
    lookup.currentItem = lookup.items[1]
    assertEquals 'abce', lookup.currentItem.lookupString

    type '\t'
    myFixture.checkResult """
      class Foo {
        String foo() {
          int abcd;
          int abce;
          abce<caret>
        }
      }
    """
  }

  public void _testHideAutopopupIfItContainsExactMatch() {
    myFixture.configureByText("a.java", """
      class Foo {
        String foo() {
          int abcd;
          int abcde;
          int abcdefg;
          ab<caret>
        }
      }
    """)
    type 'c'
    assert lookup
    type 'd'
    assert !lookup
    type 'e'
    assert !lookup
    type 'f'
    assert lookup
  }

  public void testFocusInJavadoc() {
    myFixture.configureByText("a.java", """
    /**
    * {@link ArrLi<caret>}
    */
      class Foo {}
    """)
    type 's'
    assert lookup.focused

  }

  public void testPrefixLengthDependentSorting() {
    myFixture.addClass("package foo; public class PsiJavaCodeReferenceElement {}")
    myFixture.configureByText("a.java", """
    class PsiJavaCodeReferenceElementImpl {
      { <caret> }
    }
    """)
    type 'PJCR'
    assertOrderedEquals myFixture.lookupElementStrings, 'PsiJavaCodeReferenceElement', 'PsiJavaCodeReferenceElementImpl'

  }

  public void testQuickSelectAfterReuse() {
    myFixture.configureByText("a.java", """
    class A { Iterable iterable;
      { <caret> }
    }
    """)
    type 'ite'
    edt {
      myFixture.type 'r'
      lookup.markReused()
      lookup.currentItem = lookup.items[0]
      CommandProcessor.instance.executeCommand project, ({lookup.finishLookup Lookup.NORMAL_SELECT_CHAR} as Runnable), null, null

    }
    myFixture.checkResult """
    class A { Iterable iterable;
      { iterable<caret> }
    }
    """
  }

  public void testQuickSelectAfterReuseAndBackspace() {
    myFixture.configureByText("a.java", """
    class A { Iterable iterable;
      { <caret> }
    }
    """)
    type 'ite'
    edt {
      myFixture.type 'r'
      lookup.markReused()
      myFixture.type '\b\b'
      lookup.currentItem = lookup.items[0]
      CommandProcessor.instance.executeCommand project, ({lookup.finishLookup Lookup.NORMAL_SELECT_CHAR} as Runnable), null, null
    }
    myFixture.checkResult """
    class A { Iterable iterable;
      { iterable<caret> }
    }
    """
  }

  public void testQuickSelectLiveTemplate() {
    myFixture.configureByText("a.java", """
    class A {
      { <caret> }
    }
    """)
    type 'th'
    edt { myFixture.type 'r\t'}
    myFixture.checkResult """
    class A {
      { throw new <caret> }
    }
    """
  }

  public void testTwoQuickRestartsAfterHiding() {
    for (i in 0..10) {
      myFixture.configureByText("a${i}.java", """
      class A {
        { <caret> }
      }
      """)
      edt { myFixture.type 'A' }
      joinAlarm() // completion started
      boolean tooQuick = false
      edt {
        tooQuick = lookup == null
        myFixture.type 'IO'
      }
      joinAlarm() //I
      joinAlarm() //O
      joinCompletion()
      assert lookup
      assert 'ArrayIndexOutOfBoundsException' in myFixture.lookupElementStrings
      if (!tooQuick) {
        return
      }
      edt {
        LookupManager.getInstance(project).hideActiveLookup()
        CompletionProgressIndicator.cleanupForNextTest()
      }
    }
    fail "too many too quick attempts"
  }

  public void testTypingDuringExplicitCompletion() {
    myFixture.configureByText("a.java", """
    class A {
      { Runnable r = new <caret> }
    }
    """)
    myFixture.complete CompletionType.SMART
    edt { myFixture.type 'Thr' }
    joinCompletion()
    assert lookup
    assert 'Thread' in myFixture.lookupElementStrings
  }

  public void testDotAfterVariable() {
    myFixture.configureByText("a.java", """
    class A {
      { Object ooo; <caret> }
    }
    """)
    type 'o.'
    assert myFixture.file.text.contains("ooo.")
    assert lookup
  }

  public void testDotAfterCall() {
    myFixture.configureByText("a.java", """
    class A {
      { <caret> }
    }
    """)
    type 'tos.'
    assert myFixture.file.text.contains("toString().")
    assert lookup
  }

  public void testDotAfterClassName() {
    myFixture.configureByText("a.java", """
    class A {
      { <caret> }
    }
    """)
    type 'FilInpStr.'
    assert myFixture.file.text.contains("FileInputStream.")
    assert lookup
  }

  public void testDotAfterClassNameInParameter() {
    myFixture.configureByText("a.java", """
    class A {
      void foo(<caret>) {}
    }
    """)
    type 'FilInpStr...'
    assert myFixture.editor.document.text.contains("FileInputStream...")
    assert !lookup
  }

  void testArrow(boolean up, boolean cycleScrolling, boolean lookupAbove, int index) {
    myFixture.configureByText("a.java", """
    class A {
      { ArrayIndexOutOfBoundsException <caret> }
    }
    """)

    type 'o'
    assert lookup
    assert !lookup.focused
    assert lookup.items.size() == 2

    lookup.positionedAbove = lookupAbove
    UISettings.instance.CYCLE_SCROLLING = cycleScrolling

    def action = up ? IdeActions.ACTION_EDITOR_MOVE_CARET_UP : IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN
    try {
      edt { myFixture.performEditorAction(action) }
      if (lookup) {
        assert lookup.focused
        assert index >= 0
        assert lookup.items[index] == lookup.currentItem
        edt { lookup.hide() }
      } else {
        assert index == -1
      }
      type '\b'
    }
    finally {
      UISettings.instance.CYCLE_SCROLLING = true
    }

  }

  void testArrows(boolean cycleScrolling, boolean lookupAbove, int indexDown, indexUp) {
    testArrow true, cycleScrolling, lookupAbove, indexUp
    testArrow false, cycleScrolling, lookupAbove, indexDown
  }

  public void testVerticalArrows() {
    testArrows false, false, 0, -1
    testArrows false, true, 0, -1
    testArrows true, false, 0, 1
    testArrows true, true, 0, 1
  }

  public void testHideOnOnePrefixVariant() {
    myFixture.configureByText("a.java", """
    class A {
      Object foo() { return nu<caret> }
    }
    """)
    type 'll'
    assert !lookup
  }

  public void testResumeAfterBackspace() {
    myFixture.configureByText("a.java", """
    class A {
      Object foo() { this<caret> }
    }
    """)
    type '.'
    assert lookup
    type 'a'
    assert !lookup
    type '\b'
    assert !lookup
    type 'c'
    assert lookup
  }

  public void testHideOnInvalidSymbolAfterBackspace() {
    myFixture.configureByText("a.java", """
    class A {
      Object foo() { this<caret> }
    }
    """)
    type '.'
    assert lookup
    type 'c'
    assert lookup
    type '\b'
    assert lookup
    type 'x'
    assert !lookup
  }


}
