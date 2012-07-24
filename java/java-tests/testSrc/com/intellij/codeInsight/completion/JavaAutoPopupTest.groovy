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

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.ide.DataManager
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.impl.CurrentEditorProvider
import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import junit.framework.Test
import junit.framework.TestSuite

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
    assertContains("if", "iterable", "int")

    type('t')
    assertContains "iterable"
    assertEquals 'iterable', lookup.currentItem.lookupString

    type('er')
    assertContains("iter", "iterable")
    assertEquals 'iterable', lookup.currentItem.lookupString
    assert lookup.focused

    type 'a'
    assert lookup.focused
  }

  def assertContains(String... items) {
    assert myFixture.lookupElementStrings.containsAll(items as List)
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
    assertContains "iter", "iterable"

    type '\b'
    assertContains "iterable"

    type '\b'
    assertContains "itaa", "iterable"
    type "a"
    assertContains "itaa"
    type '\b'
    assertContains "itaa", "iterable"
    type "e"
    assertContains "iterable"

    type "r"
    assertContains "iter", "iterable"
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
    assertContains "iterable", "iterable2"

    assertEquals 'iterable', lookup.currentItem.lookupString
    edt { myFixture.performEditorAction IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN }
    assertEquals 'iterable2', lookup.currentItem.lookupString

    type "r"
    assertContains "iter", "iterable", 'iterable2'
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
    assertContains "iterable", "iterable2"

    assertEquals 'iterable', lookup.currentItem.lookupString
    lookup.currentItem = lookup.items[1]
    assertEquals 'iterable2', lookup.currentItem.lookupString

    type "r"
    assertContains "iter", "iterable", 'iterable2'
    assertEquals 'iterable2', lookup.currentItem.lookupString

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
    type 'x'
    assert !myFixture.lookupElementStrings
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

    assertContains 'abcd', 'abce'
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


  public void testFocusInJavadoc() {
    myFixture.configureByText("a.java", """
    /**
    * {@link AIO<caret>}
    */
      class Foo {}
    """)
    type 'O'
    assert lookup.focused

  }

  public void testPrefixLengthDependentSorting() {
    myFixture.addClass("package foo; public class PsiJavaCodeReferenceElement {}")
    myFixture.configureByText("a.java", """
    import foo.PsiJavaCodeReferenceElement;

    class PsiJavaCodeReferenceElementImpl {
      { <caret> }
    }
    """)
    type 'PJCR'
    assertContains 'PsiJavaCodeReferenceElement', 'PsiJavaCodeReferenceElementImpl'

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
      CommandProcessor.instance.executeCommand project, {lookup.finishLookup Lookup.NORMAL_SELECT_CHAR}, null, null

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
      joinAutopopup() // completion started
      boolean tooQuick = false
      edt {
        tooQuick = lookup == null
        myFixture.type 'IO'
      }
      joinAutopopup() //I
      joinAutopopup() //O
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
    type 'AIOO.'
    assert myFixture.file.text.contains("ArrayIndexOutOfBoundsException.")
    assert lookup
  }

  public void testDotAfterClassNameInParameter() {
    myFixture.configureByText("a.java", """
    class A {
      void foo(<caret>) {}
    }
    """)
    type 'AIOO...'
    assert myFixture.editor.document.text.contains("ArrayIndexOutOfBoundsException...")
    assert !lookup
  }

  void testArrow(boolean up, boolean cycleScrolling, boolean lookupAbove, int index) {
    myFixture.configureByText("a.java", """
    class A {
      { ArrayIndexOutOfBoundsException <caret> }
    }
    """)

    type 'ind'
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

  void testArrows(boolean cycleScrolling, boolean lookupAbove, int indexDown, int indexUp) {
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
    type 'x'
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

  public void testDoubleLiteralInField() {
    myFixture.configureByText "a.java", """
public interface Test {
  double FULL = 1.0<caret>
}"""
    type 'd'
    assert !lookup
  }

  public void testCancellingDuringCalculation() {
    myFixture.configureByText "a.java", """
class Aaaaaaa {}
public interface Test {
  <caret>
}"""
    edt { myFixture.type 'A' }
    joinAutopopup()
    def first = lookup
    assert first
    edt {
      assert first == lookup
      lookup.hide()
      myFixture.type 'a'
    }
    joinAutopopup()
    joinAutopopup()
    joinAutopopup()
    assert lookup != first
  }

  static class LongReplacementOffsetContributor extends CompletionContributor {
    @Override
    void duringCompletion(CompletionInitializationContext cxt) {
      Thread.sleep 500
      ProgressManager.checkCanceled()
      cxt.replacementOffset--;
    }
  }

  static class LongContributor extends CompletionContributor {

    @Override
    void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
      result.runRemainingContributors(parameters, true)
      Thread.sleep 1000
    }
  }

  public void testDuringCompletionMustFinish() {
    registerContributor(LongReplacementOffsetContributor)

    edt { myFixture.addFileToProject 'directory/foo.txt', '' }
    myFixture.configureByText "a.java", 'public interface Test { RuntiExce<caret>xxx }'
    myFixture.completeBasic()
    while (!lookup.items) {
      Thread.sleep(10)
      edt { lookup.refreshUi(false, false) }
    }
    edt { myFixture.type '\t' }
    myFixture.checkResult 'public interface Test { RuntimeException<caret>x }'
 }

  private def registerContributor(final Class contributor, LoadingOrder order = LoadingOrder.LAST) {
    def ep = Extensions.rootArea.getExtensionPoint("com.intellij.completion.contributor")
    def bean = new CompletionContributorEP(language: 'JAVA', implementationClass: contributor.name)
    ep.registerExtension(bean, order)
    disposeOnTearDown({ ep.unregisterExtension(bean) } as Disposable)
  }

  public void testLeftRightMovements() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(String iterable) {
          <caret>ter   x
        }
      }
    """)
    type('i')
    def offset = myFixture.editor.caretModel.offset
    assertContains "if", "iterable", "int"

    edt { myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT) }
    assert myFixture.editor.caretModel.offset == offset + 1
    joinAutopopup()
    joinCompletion()
    assert !lookup.calculating
    assertContains "iterable"
    assertEquals 'iterable', lookup.currentItem.lookupString

    edt { myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT) }
    assert myFixture.editor.caretModel.offset == offset
    joinAutopopup()
    joinCompletion()
    assert !lookup.calculating
    assertContains "if", "iterable", "int"
    assertEquals 'iterable', lookup.currentItem.lookupString

    edt { myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT) }
    joinAutopopup()
    joinCompletion()
    assert !lookup.calculating
    assert lookup.items.size() > 3

    for (i in 0.."iter".size()) {
      edt { myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT) }
    }
    assert !lookup
  }

  public void testTypingInAnotherEditor() {
    myFixture.configureByText("a.java", "")
    type 'c'
    assert lookup

    Editor another = null
    def wca = new WriteCommandAction.Simple(getProject(), new PsiFile[0]) {
      @Override
      protected void run() {
        EditorActionManager.instance.getTypedAction().handler.execute(another, (char) 'x', DataManager.instance.dataContext)
      }
    }

    try {
      edt {
        assert !lookup.calculating
        lookup.hide()
        def file = myFixture.addFileToProject("b.java", "")
        another = EditorFactory.instance.createEditor(file.viewProvider.document, project)
        wca.execute()
        assert 'x' == another.document.text
      }
      joinAutopopup()
      joinCompletion()
      LookupImpl l1 = LookupManager.getActiveLookup(another)
      if (l1) {
        printThreadDump()
        println l1.items
        println l1.calculating
        println myFixture.editor
        println another
        println CompletionServiceImpl.completionPhase
        assert false : l1.items
      }
      type 'l'
      assert lookup
    }
    finally {
      edt { EditorFactory.instance.releaseEditor(another) }
    }

  }

  public void testExplicitCompletionOnEmptyAutopopup() {
    myFixture.configureByText("a.java", "<caret>")
    type 'cccccc'
    myFixture.completeBasic()
    joinCompletion()
    assert !lookup
  }

  public void testNoSingleTemplateLookup() {
    myFixture.configureByText 'a.java', 'class Foo {{ ite<caret> }}'
    type 'r'
    assert !lookup
  }

  public void testTemplatesWithNonImportedClasses() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
    myFixture.addClass("package foo.bar; public class ToArray {}")
    try {
      myFixture.configureByText 'a.java', 'class Foo {{ foo(<caret>) }}'
      type 'toar'
      assert lookup
      assert 'toar' in myFixture.lookupElementStrings
      assert 'ToArray' in myFixture.lookupElementStrings
    }
    finally {
      CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER
    }
  }

  public void testTemplateSelectionByComma() {
    myFixture.configureByText("a.java", """
class Foo {
    int itea = 2;
    int itera = 2;

    {
        it<caret>
    }
}
""")
    type 'e'
    assertContains "itea", "itera"
    type 'r'
    assertContains "iter", "itera"
    type ','
    assert !lookup
    assert myFixture.editor.document.text.contains('itera,')
  }

  public void testTemplateSelectionBySpace() {
    myFixture.configureByText("a.java", """
class Foo {
    int ITER = 2;
    int itea = 2;

    {
        it<caret>
    }
}
""")
    type 'er '
    assert myFixture.editor.document.text.contains('iter ')
  }

  public void testNewClassParenthesis() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
    try {
      myFixture.configureByText("a.java", """ class Foo { { new <caret> } } """)
      type 'aioo('
      assert myFixture.editor.document.text.contains('new ArrayIndexOutOfBoundsException()')
    }
    finally {
      CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER
    }
  }

  public void testUnknownMethodParenthesis() {
    myFixture.configureByText("a.java", """ class Foo { { <caret> } } """)
    type 'filinpstr('
    assert myFixture.editor.document.text.contains('filinpstr()')
  }

  public void testNoAutopopupAfterSpace() {
    myFixture.configureByText("a.java", """ class Foo { { int newa; <caret> } } """)
    edt { myFixture.type('new ') }
    joinAutopopup()
    joinCompletion()
    assert !lookup
  }

  public void testRestartAndTypingDuringCopyCommit() {
    registerContributor(LongReplacementOffsetContributor)

    myFixture.configureByText("a.java", """ class Foo { { int newa; <caret> } } """)
    myFixture.type 'n'
    joinAutopopup()
    myFixture.type 'e'
    joinCommit() // original commit
    myFixture.type 'w'
    joinAutopopup()
    joinCompletion()
    myFixture.type '\n'
    myFixture.checkResult(" class Foo { { int newa; new <caret> } } ")
    assert !lookup
  }

  public void testAutoRestartAndTypingDuringCopyCommit() {
    registerContributor(LongReplacementOffsetContributor)

    myFixture.configureByText("a.java", """ class Foo { { int iteraaa; <caret> } } """)
    type 'ite'
    assert !('iter' in myFixture.lookupElementStrings)
    myFixture.type 'r'
    joinCommit()
    myFixture.type 'a'
    joinAutopopup()
    joinCompletion()
    myFixture.type '\n'
    myFixture.checkResult(" class Foo { { int iteraaa; iteraaa<caret> } } ")
    assert !lookup
  }
  
  public void testChoosingItemDuringCopyCommit() {
    registerContributor(LongReplacementOffsetContributor)

    myFixture.configureByText("a.java", """ class Foo { { int iteraaa; <caret> } } """)
    type 'ite'
    assert !('iter' in myFixture.lookupElementStrings)
    assert 'iteraaa' in myFixture.lookupElementStrings
    myFixture.type 'r'
    joinCommit()
    myFixture.type 'a.'
    myFixture.checkResult(" class Foo { { int iteraaa; iteraaa.<caret> } } ")
  }

  public void testRestartWithInvisibleLookup() {
    registerContributor(LongReplacementOffsetContributor)

    myFixture.configureByText("a.java", """ class Foo { { int abcdef; <caret> } } """)
    myFixture.type 'a'
    joinAutopopup()
    assert lookup
    edt { myFixture.type 'bc' }
    joinAutopopup()
    joinAutopopup()
    joinCompletion()
    assert lookup
    assert lookup.shown
  }

  public void testRestartWithVisibleLookup() {
    registerContributor(LongContributor, LoadingOrder.FIRST)

    myFixture.configureByText("a.java", """ class Foo { { int abcdef, abcdefg; ab<caret> } } """)
    myFixture.completeBasic()
    while (!lookup.shown) {
      Thread.sleep(1)
    }
    def l = lookup
    edt {
      assert lookup.calculating
      myFixture.type 'c'
    }
    joinCommit {
      myFixture.type 'd'
    }
    joinAutopopup()
    joinCompletion()
    assert lookup == l
    assert !lookup.calculating
    assert lookup.shown
  }

  private void joinSomething(int degree) {
    if (degree == 0) return
    joinAlarm()
    if (degree == 1) return
    joinCommit()
    if (degree == 2) return
    joinCommit()
    if (degree == 3) return
    edt {}
    if (degree == 4) return
    joinCompletion()
  }

  public void testEveryPossibleWayToTypeIf() {
    def src = "class Foo { { int ifa; <caret> } }"
    def result = "class Foo { { int ifa; if <caret> } }"
    int actions = 5

    for (a1 in 0..actions) {
      println "a1 = $a1"
      for (a2 in 0..actions) {
        myFixture.configureByText("$a1 $a2 .java", src)
        myFixture.type 'i'
        joinSomething(a1)
        myFixture.type 'f'
        joinSomething(a2)
        myFixture.type ' '

        joinAutopopup()
        joinCompletion()
        myFixture.checkResult(result)
        assert !lookup
      }
    }

    for (a1 in 0..actions) {
      myFixture.configureByText("$a1 if .java", src)
      edt { myFixture.type 'if' }
      joinSomething(a1)

      myFixture.type ' '

      joinAutopopup()
      joinCompletion()
      myFixture.checkResult(result)
      assert !lookup
    }

    for (a1 in 0..actions) {
      myFixture.configureByText("$a1 if .java", src)
      myFixture.type 'i'
      joinSomething(a1)

      edt { myFixture.type 'f ' }

      joinAutopopup()
      joinCompletion()
      myFixture.checkResult(result)
      assert !lookup
    }

  }

  public void testNonFinishedParameterComma() {
    myFixture.configureByText("a.java", """ class Foo { void foo(int aaa, int aaaaa) { foo(<caret>) } } """)
    type 'a,'
    assert myFixture.editor.document.text.contains('foo(aaa, )')
  }

  public void testFinishedParameterComma() {
    myFixture.configureByText("a.java", """ class Foo { void foo(int aaa, int aaaaa) { foo(<caret>) } } """)
    type 'aaa,'
    assert myFixture.editor.document.text.contains('foo(aaa,)')
  }

  public void testNonFinishedVariableEq() {
    myFixture.configureByText("a.java", """ class Foo { void foo(int aaa, int aaaaa) { <caret> } } """)
    type 'a='
    assert myFixture.editor.document.text.contains('aaa = ')
  }

  public void testFinishedVariableEq() {
    myFixture.configureByText("a.java", """ class Foo { void foo(int aaa, int aaaaa) { <caret> } } """)
    type 'aaa='
    assert myFixture.editor.document.text.contains('aaa=')
  }

  public void testCompletionWhenLiveTemplateAreNotSufficient() {
    ((TemplateManagerImpl)TemplateManager.getInstance(getProject())).setTemplateTesting(true);
    try {
      myFixture.configureByText("a.java", """
  class Foo {
      {
          Iterable<String> l1 = null;
          Iterable<String> l2 = null;
          Object asdf = null;
          iter<caret>
      }
  }
  """)
      type '\t'
      assert myFixture.lookupElementStrings == ['l2', 'l1']
      type 'as'
      assert lookup
      assertContains 'asdf', 'assert'
      type '\n.'
      assert lookup
      assert 'hashCode' in myFixture.lookupElementStrings
      assert myFixture.file.text.contains('asdf.')
    }
    finally {
      ((TemplateManagerImpl)TemplateManager.getInstance(getProject())).setTemplateTesting(false);
    }

  }

  public void testNoWordCompletionAutoPopup() {
    myFixture.configureByText "a.java", 'class Bar { void foo() { "f<caret>" }}'
    type 'o'
    assert !lookup
  }

  public void testMethodNameRestart() {
    myFixture.configureByText "a.java", '''
public class Bar {
    private static List<java.io.File> getS<caret>

}
'''
    type 'ta'
    assert !lookup
  }

  public void testTargetElementInLookup() {
    myFixture.configureByText 'a.java', '''
class Foo {
  void x__foo() {}
  void bar() {
    <caret>
  }
  void x__goo() {}
}
'''
    def cls = ((PsiJavaFile)myFixture.file).getClasses()[0]
    def foo = cls.methods[0]
    def goo = cls.methods[2]
    type('x')
    assertContains 'x__foo', 'x__goo'
    edt {
      assert foo == TargetElementUtil.instance.findTargetElement(myFixture.editor, TargetElementUtil.LOOKUP_ITEM_ACCEPTED)
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
      assert goo == TargetElementUtil.instance.findTargetElement(myFixture.editor, TargetElementUtil.LOOKUP_ITEM_ACCEPTED)
    }

    type('_')
    assertContains 'x__foo', 'x__goo'
    edt {
      assert goo == TargetElementUtil.instance.findTargetElement(myFixture.editor, TargetElementUtil.LOOKUP_ITEM_ACCEPTED)
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP)
      assert foo == TargetElementUtil.instance.findTargetElement(myFixture.editor, TargetElementUtil.LOOKUP_ITEM_ACCEPTED)
    }
  }

  public void testExplicitAutocompletionAfterAutoPopup() {
    myFixture.configureByText 'a.java', 'class Foo <caret>'
    type 'ext'

    CompletionAutoPopupHandler.ourTestingAutopopup = false
    edt {
      myFixture.completeBasic()
    }
    myFixture.checkResult 'class Foo extends <caret>'
  }

  public void testExplicitMultipleVariantCompletionAfterAutoPopup() {
    myFixture.configureByText 'a.java', 'class Foo {<caret>}'
    type 'pr'

    CompletionAutoPopupHandler.ourTestingAutopopup = false
    edt {
      myFixture.completeBasic()
    }
    myFixture.checkResult 'class Foo {pr<caret>}'

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      void run() {
        getLookup().items.each {
          getLookup().list.cellRenderer.getListCellRendererComponent(getLookup().list, it, 0, false, false)
        }
      }
    });
    assert myFixture.lookupElementStrings.containsAll(['private', 'protected'])
  }

  public void testArrays() {
    myFixture.configureByText "a.java", "class Foo {{ <caret> }}"
    type 'Arrays.'
    myFixture.checkResult "class Foo {{ Arrays.<caret> }}"
    assert 'Arrays.asList' in myFixture.lookupElementStrings
  }


  public void testExactMatchesFirst() {
    myFixture.configureByText("a.java", """
public class UTest {
    void nextWord() {}

    void foo() {
        n<caret>
    }
}""")
    type 'ew'
    assert myFixture.lookupElementStrings == ['new', 'nextWord']
  }

  public void testUpdatePrefixMatchingOnTyping() {
    myFixture.addClass("class CertificateEncodingException {}")
    myFixture.addClass("class CertificateException {}")
    myFixture.configureByText 'a.java', 'class Foo {<caret>}'
    type 'CertificateExce'
    assert myFixture.lookupElementStrings == ['CertificateException', 'CertificateEncodingException']
  }

  public void testNoClassesInUnqualifiedImports() {
    myFixture.addClass("package xxxxx; public class Xxxxxxxxx {}")
    myFixture.configureByText 'a.java', 'package foo; import <caret>'
    type 'xxx'
    assert !lookup
  }

  public void testPopupAfterDotAfterPackage() {
    myFixture.configureByText 'a.java', '<caret>'
    type 'import jav'
    assert lookup
    type '.'
    assert lookup
  }

  public void testSamePrefixIgnoreCase() {
    myFixture.addClass("package xxxxx; public class SYSTEM_EXCEPTION {}")
    myFixture.configureByText "a.java", "import xxxxx.*; class Foo { S<caret> }"
    type 'Ystem'
    assert 'java.lang.System' == ((JavaPsiClassReferenceElement) myFixture.lookupElements[0]).qualifiedName
    assert 'xxxxx.SYSTEM_EXCEPTION' == ((JavaPsiClassReferenceElement) myFixture.lookupElements[1]).qualifiedName
  }

  public void testSamePrefixIgnoreCase2() {
    myFixture.addClass("package xxxxx; public class SYSTEM_EXCEPTION {}")
    myFixture.addClass("package xxxxx; public class SYstem {}")
    myFixture.configureByText "a.java", "import xxxxx.*; class Foo { S<caret> }"
    type 'Ystem'
    assert 'xxxxx.SYstem' == ((JavaPsiClassReferenceElement) myFixture.lookupElements[0]).qualifiedName
    assert 'java.lang.System' == ((JavaPsiClassReferenceElement) myFixture.lookupElements[1]).qualifiedName
    assert 'xxxxx.SYSTEM_EXCEPTION' == ((JavaPsiClassReferenceElement) myFixture.lookupElements[2]).qualifiedName
  }

  private FileEditor openEditorForUndo() {
    FileEditor editor;
    edt { editor = FileEditorManager.getInstance(project).openFile(myFixture.file.virtualFile, false)[0] }
    def manager = (UndoManagerImpl) UndoManager.getInstance(project)
    def old = manager.editorProvider
    manager.editorProvider = new CurrentEditorProvider() {
      @Override
      public FileEditor getCurrentEditor() {
        return editor;
      }
    };
    disposeOnTearDown ({ manager.editorProvider = old } as Disposable)
    return editor
  }

  public void _testCharSelectionUndo() {
    myFixture.configureByText "a.java", "class Foo {{ <caret> }}"
    def editor = openEditorForUndo();
    type('ArrStoExce.')
    edt { UndoManager.getInstance(project).undo(editor) }
    assert myFixture.editor.document.text.contains('ArrStoExce.')
  }

  public void testAutopopupTypingUndo() {
    myFixture.configureByText "a.java", "class Foo {{ <caret> }}"
    def editor = openEditorForUndo();
    type 'aioobeeee'
    edt { UndoManager.getInstance(project).undo(editor) }
    assert !myFixture.editor.document.text.contains('aioo')
  }

  public void testNoLiveTemplatesAfterDot() {
    myFixture.configureByText "a.java", "class Foo {{ Iterable t; t.<caret> }}"
    type 'iter'
    assert myFixture.lookupElementStrings == ['iterator']
  }

  public void testTypingNonImportedClassName() {
    myFixture.addClass("package foo; public class Foo239 {} ")
    myFixture.addClass("class Foo239Util {} ")
    myFixture.addClass("class Foo239Util2 {} ")

    myFixture.configureByText "a.java", "class Foo {{ <caret> }}"
    type 'Foo239 '
    assert myFixture.file.text.contains('Foo239 ')
    
    myFixture.configureByText "a.java", "class Foo {{ <caret> }}"
    type 'Foo239'
    edt { myFixture.performEditorAction IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN }
    type ' '
    assert myFixture.file.text.contains('Foo239Util2 ')

    myFixture.configureByText "a.java", "class Foo {{ <caret> }}"
    type 'new Foo239('
    assert myFixture.file.text.contains('new Foo239()')
  }

  public void testTypingFirstVarargDot() {
    myFixture.configureByText "a.java", "class Foo { void foo(Foo<caret>[] a) { }; class Bar {}}"
    type '.'
    assert !lookup
  }

  public void testBlockSelection() {
    doTestBlockSelection """
class Foo {{
  <caret>tx;
  tx;
}}""", '\n', '''
class Foo {{
  toString()x;
  toString()<caret>x;
}}'''
  }

  public void testBlockSelectionTab() {
    doTestBlockSelection """
class Foo {{
  <caret>tx;
  tx;
}}""", '\t', '''
class Foo {{
  toString();
  toString()<caret>;
}}'''
  }

  public void testBlockSelectionBackspace() {
    doTestBlockSelection """
class Foo {{
  <caret>t;
  t;
}}""", '\b\t', '''
class Foo {{
  toString();
  toString()<caret>;
}}'''
  }

  private doTestBlockSelection(final String textBefore, final String toType, final String textAfter) {
    myFixture.configureByText "a.java", textBefore
    edt {
      def caret = myFixture.editor.offsetToLogicalPosition(myFixture.editor.caretModel.offset)
      myFixture.editor.selectionModel.setBlockSelection(caret, new LogicalPosition(caret.line + 1, caret.column + 1))
    }
    type 'toStr'
    assert lookup
    type toType
    myFixture.checkResult textAfter
    def start = myFixture.editor.selectionModel.blockStart
    def end = myFixture.editor.selectionModel.blockEnd
    assert start.line == end.line - 1
    assert start.column == end.column
    assert end == myFixture.editor.caretModel.logicalPosition
  }

  public void testNonImportedClass() {
    myFixture.addClass("package foo; public class Abcdefg {}")
    myFixture.configureByText 'a.java', 'class Foo extends <caret>'
    type 'Abcde '
    myFixture.checkResult 'import foo.Abcdefg;\n\nclass Foo extends Abcdefg <caret>'
  }

  public void testTwoNonImportedClasses() {
    myFixture.addClass("package foo; public class Abcdefg {}")
    myFixture.addClass("package bar; public class Abcdefg {}")
    myFixture.configureByText 'a.java', 'class Foo extends <caret>'
    type 'Abcde '
    myFixture.checkResult 'class Foo extends Abcdefg <caret>'
  }

  public void testClassNameInProperties() {
    myFixture.addClass("package java.langa; public class Abcdefg {}")
    myFixture.configureByText 'a.properties', 'key.11=java<caret>'
    type '.'
    assert lookup
    type 'lang'
    assert myFixture.lookupElementStrings.size() >= 2
    type '.'
    assert lookup
    edt { myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.text.indexOf('lang')) }
    assert !lookup
    type 'i'
    assert 'io' in myFixture.lookupElementStrings
  }

  public void testEnteringLabel() {
    myFixture.configureByText 'a.java', '''class Foo {{
  <caret>
}}
'''
    type 'FIS:'
    assert myFixture.file.text.contains('FIS:')
  }

  public void testSoutvTemplate() {
    ((TemplateManagerImpl)TemplateManager.getInstance(getProject())).setTemplateTesting(true);
    try {
      myFixture.configureByText 'a.java', 'class Foo {{ <caret> }}'
      type 'soutv\tgetcl.'
      myFixture.checkResult '''class Foo {{
    System.out.println("getClass(). = " + getClass().<caret>); }}'''
    }
    finally {
      ((TemplateManagerImpl)TemplateManager.getInstance(getProject())).setTemplateTesting(false);
    }
  }

  public void testReturnLParen() {
    myFixture.configureByText 'a.java', 'class Foo { int foo() { <caret> }}'
    type 're('
    myFixture.checkResult 'class Foo { int foo() { re(<caret>) }}'
  }

  public void testAmbiguousClassQualifier() {
    myFixture.addClass("package foo; public class Util { public static void foo() {} }")
    myFixture.addClass("package bar; public class Util { public static void bar() {} }")
    myFixture.configureByText 'a.java', 'class Foo {{ <caret> }}'
    type 'Util.'
    assert myFixture.lookupElementStrings == ['Util.bar', 'Util.foo']
    type 'fo\n'
    myFixture.checkResult '''import foo.Util;

class Foo {{
    Util.foo();<caret> }}'''
  }

  public void testPackageQualifier() {
    myFixture.addClass("package com.too; public class Util {}")
    myFixture.configureByText 'a.java', 'class Foo { void foo(Object command) { <caret> }}'
    type 'com.t'
    assert myFixture.lookupElementStrings.containsAll(['too', 'command.toString'])
  }

  public void testUnfinishedString() {
    myFixture.configureByText 'a.java', '''
// Date
class Foo {
  String s = "<caret>
  String s2 = s;
}
'''
    type 'D'
    assert !lookup
  }

  public void testVarargParenthesis() {
    myFixture.configureByText 'a.java', '''
class Foo {
  void foo(File... files) { }
  { foo(new <caret>) }
}
'''
    type 'File('
    assert myFixture.file.text.contains('new File()')
  }

  public void "test inaccessible class in another package shouldn't prevent choosing by space"() {
    myFixture.addClass("package foo; class b {}")
    myFixture.configureByText 'a.java', 'class Foo {{ <caret> }}'
    type 'b'
    assert lookup?.currentItem?.lookupString == 'boolean'
    type ' '
    myFixture.checkResult 'class Foo {{ boolean <caret> }}'
  }

  @Override
  protected void tearDown() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER
    super.tearDown()
  }

  public void testBackspaceShouldShowPreviousVariants() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
    myFixture.addClass("class OuterX { static class TrueLine {} }")
    myFixture.configureByText 'a.java', 'class Foo{ void foo(int truex) { return tr<caret> }}'
    type 'ue'
    assert myFixture.lookupElementStrings == ['true', 'truex']
    type 'l'
    assert myFixture.lookupElementStrings == ['TrueLine']
    type '\b'
    assert myFixture.lookupElementStrings == ['true', 'truex']
  }

}
