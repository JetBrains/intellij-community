/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.codeInsight.completion

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.PsiTypeLookupItem
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.template.JavaCodeContextType
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.*
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
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.psi.*
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.psi.statistics.impl.StatisticsManagerImpl
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.NotNull

import java.awt.event.KeyEvent

/**
 * @author peter
 */
class JavaAutoPopupTest extends CompletionAutoPopupTestCase {

  void testNewItemsOnLongerPrefix() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(String iterable) {
          <caret>
        }
      }
    """)
    type('i')
    def les = myFixture.lookupElementStrings
    assert 'iterable' in les
    assert 'if' in les
    assert 'int' in les

    type('t')
    assertContains "iterable"
    assertEquals 'iterable', lookup.currentItem.lookupString

    type('er')
    les = myFixture.lookupElementStrings
    assert 'iterable' in les
    assert 'iter' in les
    assertEquals 'iter', lookup.currentItem.lookupString
    assert lookup.focused

    type 'a'
    assert lookup.focused
  }

  void testAfterDblColon() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo() {
          Runnable::<caret>
        }
      }
    """)
    type('r')
    def les = myFixture.lookupElementStrings
    assert 'run' in les
    assert lookup.focused
  }

  def assertContains(String... items) {
    assert myFixture.lookupElementStrings.containsAll(items as List)
  }

  void testRecalculateItemsOnBackspace() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(String iterable) {
          int itaa;
          ite<caret>
        }
      }
    """)
    type "r"
    myFixture.assertPreferredCompletionItems 0, "iter", "iterable"

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
    myFixture.assertPreferredCompletionItems 0, "iter", "iterable"
  }

  void testExplicitSelectionShouldSurvive() {
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
    assert lookup.currentItem.lookupString == 'iterable2'

    type "r"
    assert lookup.items[0].lookupString == 'iter'
    assert lookup.currentItem.lookupString == 'iterable2'

  }

  void testExplicitMouseSelectionShouldSurvive() {
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
    edt { lookup.currentItem = lookup.items[1] }
    assertEquals 'iterable2', lookup.currentItem.lookupString

    type "r"
    myFixture.assertPreferredCompletionItems 2, "iter", "iterable", 'iterable2'

  }

  void testGenerallyFocusLookupInJavaMethod() {
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

  void testNoStupidNameSuggestions() {
    myFixture.configureByText("a.java", """
      class Foo {
        String foo(String <caret>) {
        }
      }
    """)
    type 'x'
    assert !myFixture.lookupElementStrings
  }

  void testExplicitSelectionShouldBeHonoredFocused() {
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
    edt { lookup.currentItem = lookup.items[1] }
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

  void "test popup in javadoc reference"() {
    myFixture.configureByText("a.java", """
    /**
    * {@link AIO<caret>}
    */
      class Foo {}
    """)
    type 'O'
    assert lookup
  }

  void "test popup after hash in javadoc"() {
    myFixture.configureByText("a.java", """
    /**
    * {@link String<caret>}
    */
      class Foo {}
    """)
    type '#'
    assert lookup
  }

  void "test popup in javadoc local reference"() {
    myFixture.configureByText("a.java", """
    /**
    * {@link #<caret>}
    */
      class Foo {
        void foo() {}
      }
    """)
    type 'f'
    assert lookup
  }

  void "test autopopup in javadoc tag name"() {
    myFixture.configureByText("a.java", """
    /**
    * @a<caret>
    */
      class Foo {}
    """)
    type 'u'
    assert lookup
  }

  void "test no autopopup in javadoc parameter descriptions"() {
    myFixture.configureByText("a.java", """
class Foo {
  /**
  * @param o some sentence
  */
  void foo(Object o) {}

  /**
  * @param o s<caret>
  */
  void foo2(Object o) {}
}
    """)
    type 'o'
    assert !lookup
  }

  void "test autopopup in javadoc parameter name"() {
    myFixture.configureByText("a.java", """
class Foo {
  /**
  * @param <caret>
  */
  void foo2(Object oooooooo) {}
}
    """)
    type 'o'
    assert lookup
  }

  void "test no autopopup at the end of line comment"() {
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable())
    myFixture.configureByText "a.java", """
class Foo {
  // foo bar <caret>
}
"""
    type 't'
    assert !lookup
  }

  void testPrefixLengthDependentSorting() {
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

  void testQuickSelectAfterReuse() {
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

  void testQuickSelectAfterReuseAndBackspace() {
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

  void testQuickSelectLiveTemplate() {
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

  void testTwoQuickRestartsAfterHiding() {
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

  void testTypingDuringExplicitCompletion() {
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

  void testDotAfterVariable() {
    myFixture.configureByText("a.java", """
    class A {
      { Object ooo; <caret> }
    }
    """)
    type 'o.'
    assert myFixture.editor.document.text.contains("ooo.")
    assert lookup
  }

  void testDotAfterCall() {
    myFixture.configureByText("a.java", """
    class A {
      { <caret> }
    }
    """)
    type 'tos.'
    assert myFixture.editor.document.text.contains("toString().")
    assert lookup
  }

  void testDotAfterClassName() {
    myFixture.configureByText("a.java", """
    class A {
      { <caret> }
    }
    """)
    type 'AIOO.'
    assert myFixture.editor.document.text.contains("ArrayIndexOutOfBoundsException.")
    assert lookup
  }

  void testDotAfterClassNameInParameter() {
    myFixture.configureByText("a.java", """
    class A {
      void foo(<caret>) {}
    }
    """)
    type 'AIOO...'
    assert myFixture.editor.document.text.contains("ArrayIndexOutOfBoundsException...")
    assert !lookup
  }

  void testArrows(String toType, LookupImpl.FocusDegree focusDegree, int indexDown, int indexUp) {
    Closure checkArrow = { String action, int expectedIndex ->
      myFixture.configureByText("a.java", """
      class A {
        void foo() {}
        void farObject() {}
        void fzrObject() {}
        { <caret> }
      }
      """)

      type toType
      assert lookup
      lookup.focusDegree = focusDegree

      edt { myFixture.performEditorAction(action) }
      if (lookup) {
        assert lookup.focused
        assert expectedIndex >= 0
        assert lookup.items[expectedIndex] == lookup.currentItem
        edt { lookup.hide() }
      } else {
        assert expectedIndex == -1
      }
      type '\b'
    }

    checkArrow(IdeActions.ACTION_EDITOR_MOVE_CARET_UP, indexUp)
    checkArrow(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN, indexDown)
  }

  void "test vertical arrows in non-focused lookup"() {
    String toType = "ArrayIndexOutOfBoundsException ind"
    testArrows toType, LookupImpl.FocusDegree.UNFOCUSED, 0, 2

    UISettings.instance.cycleScrolling = false
    try {
      testArrows toType, LookupImpl.FocusDegree.UNFOCUSED, 0, -1
    }
    finally {
      UISettings.instance.cycleScrolling = true
    }
  }

  void "test vertical arrows in semi-focused lookup"() {
    CodeInsightSettings.instance.SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS = false
    UISettings.getInstance()setSortLookupElementsLexicographically(true)

    String toType = "fo"
    testArrows toType, LookupImpl.FocusDegree.SEMI_FOCUSED, 2, 0

    UISettings.instance.cycleScrolling = false
    try {
      testArrows toType, LookupImpl.FocusDegree.SEMI_FOCUSED, 2, 0
    }
    finally {
      UISettings.instance.cycleScrolling = true
    }
  }

  void testHideOnOnePrefixVariant() {
    myFixture.configureByText("a.java", """
    class A {
      Object foo() { return nu<caret> }
    }
    """)
    type 'll'
    assert !lookup
  }

  void testResumeAfterBackspace() {
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

  void testHideOnInvalidSymbolAfterBackspace() {
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

  void testDoubleLiteralInField() {
    myFixture.configureByText "a.java", """
public interface Test {
  double FULL = 1.0<caret>
}"""
    type 'd'
    assert !lookup
  }

  void testCancellingDuringCalculation() {
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
    void duringCompletion(@NotNull CompletionInitializationContext cxt) {
      Thread.sleep 500
      ProgressManager.checkCanceled()
      cxt.replacementOffset--
    }
  }

  static class LongContributor extends CompletionContributor {

    @Override
    void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
      result.runRemainingContributors(parameters, true)
      Thread.sleep 500
    }
  }

  void testDuringCompletionMustFinish() {
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
    registerCompletionContributor(contributor, myFixture.testRootDisposable, order)
  }
  static def registerCompletionContributor(final Class contributor, Disposable parentDisposable, LoadingOrder order) {
    def ep = Extensions.rootArea.getExtensionPoint("com.intellij.completion.contributor")
    def bean = new CompletionContributorEP(language: 'JAVA', implementationClass: contributor.name)
    ep.registerExtension(bean, order)
    Disposer.register(parentDisposable, { ep.unregisterExtension(bean) } as Disposable)
  }

  void testLeftRightMovements() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(String iterable) {
          <caret>ter   x
        }
      }
    """)
    type('i')
    def offset = getCaretOffset()
    assertContains "iterable", "if", "int"

    edt { myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT) }
    assert getCaretOffset() == offset + 1
    joinAutopopup()
    joinCompletion()
    assert !lookup.calculating
    assertContains "iterable"
    assertEquals 'iterable', lookup.currentItem.lookupString

    edt { myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT) }
    assert getCaretOffset() == offset
    joinAutopopup()
    joinCompletion()
    assert !lookup.calculating
    assertContains "iterable", "if", "int"
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

  private int getCaretOffset() {
    def fixture = myFixture
    return ApplicationManager.getApplication().runReadAction (new Computable<Integer>() {
      @Override
      Integer compute() {
        return fixture.editor.caretModel.offset
      }
    })
  }

  void testMulticaretLeftRightMovements() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(String iterable) {
          <caret>ter   x
          <caret>ter   x
        }
      }
    """)
    type('i')
    assert lookup

    edt { myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT) }
    myFixture.checkResult """
      class Foo {
        void foo(String iterable) {
          it<caret>er   x
          it<caret>er   x
        }
      }
    """
    joinAutopopup()
    joinCompletion()
    assert lookup
    assert !lookup.calculating

    edt { myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT) }
    myFixture.checkResult """
      class Foo {
        void foo(String iterable) {
          i<caret>ter   x
          i<caret>ter   x
        }
      }
    """
    joinAutopopup()
    joinCompletion()
    assert lookup
    assert !lookup.calculating
  }

  void testMulticaretRightMovementWithOneCaretAtDocumentEnd() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(String iterable) {
          ter   x
        }
      }
    <caret>""")
    edt {
      int primaryCaretOffset = myFixture.editor.document.text.indexOf("ter   x")
      myFixture.editor.caretModel.addCaret(myFixture.editor.offsetToVisualPosition(primaryCaretOffset))
    }

    type('i')
    assert lookup

    edt { myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT) }
    myFixture.checkResult """
      class Foo {
        void foo(String iterable) {
          it<caret>er   x
        }
      }
    i<caret>"""
  }

  void testTypingInAnotherEditor() {
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

  void testExplicitCompletionOnEmptyAutopopup() {
    myFixture.configureByText("a.java", "<caret>")
    type 'cccccc'
    myFixture.completeBasic()
    joinCompletion()
    assert !lookup
  }

  void testNoSingleTemplateLookup() {
    myFixture.configureByText 'a.java', 'class Foo { psv<caret> }'
    type 'm'
    assert !lookup : myFixture.lookupElementStrings
  }

  void testTemplatesWithNonImportedClasses() {
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

  void testTemplateSelectionBySpace() {
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
    assert !myFixture.editor.document.text.contains('for ')
  }

  void testNewClassParenthesis() {
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

  void testUnknownMethodParenthesis() {
    myFixture.configureByText("a.java", """ class Foo { { <caret> } } """)
    type 'filinpstr('
    assert myFixture.editor.document.text.contains('filinpstr()')
  }

  void testNoAutopopupAfterSpace() {
    myFixture.configureByText("a.java", """ class Foo { { int newa; <caret> } } """)
    edt { myFixture.type('new ') }
    joinAutopopup()
    joinCompletion()
    assert !lookup
  }

  void testRestartAndTypingDuringCopyCommit() {
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

  void testAutoRestartAndTypingDuringCopyCommit() {
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

  void testChoosingItemDuringCopyCommit() {
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

  void testRestartWithInvisibleLookup() {
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

  void testRestartWithVisibleLookup() {
    registerContributor(LongContributor, LoadingOrder.FIRST)

    myFixture.configureByText("a.java", """ class Foo { { int abcdef, abcdefg; ab<caret> } } """)
    myFixture.completeBasic()
    while (!lookup.shown) {
      Thread.sleep(1)
    }
    def l = lookup
    edt {
      if (!lookup.calculating) println "testRestartWithVisibleLookup couldn't be faster than LongContributor"
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
    joinCommit()
    if (degree == 1) return
    joinCommit()
    if (degree == 2) return
    edt {}
    if (degree == 3) return
    joinCompletion()
  }

  void testEveryPossibleWayToTypeIf() {
    def src = "class Foo { { int ifa; <caret> } }"
    def result = "class Foo { { int ifa; if <caret> } }"
    int actions = 4

    for (a1 in 0..actions) {
      for (a2 in 0..actions) {
        myFixture.configureByText("$a1 $a2 .java", src)
        myFixture.editor.document.addDocumentListener(new DocumentListener() {
          @Override
          void documentChanged(DocumentEvent e) {
            if (e.newFragment.toString().contains("a")) {
              fail(e.toString())
            }
          }
        })
        myFixture.type 'i'
        joinSomething(a1)
        myFixture.type 'f'
        joinSomething(a2)
        myFixture.type ' '

        joinAutopopup()
        joinCompletion()
        try {
          myFixture.checkResult(result)
        }
        catch (e) {
          println "actions: $a1 $a2"
          throw e
        }
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
      try {
        myFixture.checkResult(result)
      }
      catch (e) {
        println "actions: $a1"
        throw e
      }
      assert !lookup
    }

    for (a1 in 0..actions) {
      myFixture.configureByText("$a1 if .java", src)
      myFixture.type 'i'
      joinSomething(a1)

      edt { myFixture.type 'f ' }

      joinAutopopup()
      joinCompletion()
      try {
        myFixture.checkResult(result)
      }
      catch (e) {
        println "actions: $a1"
        throw e
      }
      assert !lookup
    }

  }

  void testNonFinishedParameterComma() {
    myFixture.configureByText("a.java", """
class Foo {
  void foo(int aaa, int aaaaa) { }
  void bar(int aaa, int aaaaa) { foo(<caret>) }
} """)
    type 'a'
    type 'a'
    type ','
    assert myFixture.editor.document.text.contains('foo(aaa, )')
  }

  void testFinishedParameterComma() {
    myFixture.configureByText("a.java", """ class Foo { void foo(int aaa, int aaaaa) { foo(<caret>) } } """)
    type 'aaa,'
    assert myFixture.editor.document.text.contains('foo(aaa,)')
  }

  void testNonFinishedVariableEq() {
    myFixture.configureByText("a.java", """ class Foo { void foo(int aaa, int aaaaa) { <caret> } } """)
    type 'a='
    assert myFixture.editor.document.text.contains('aaa = ')
  }

  void testFinishedVariableEq() {
    myFixture.configureByText("a.java", """ class Foo { void foo(int aaa, int aaaaa) { <caret> } } """)
    type 'aaa='
    assert myFixture.editor.document.text.contains('aaa=')
  }

  void testCompletionWhenLiveTemplateAreNotSufficient() {
    TemplateManagerImpl.setTemplateTesting(getProject(), myFixture.getTestRootDisposable())
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
    assert myFixture.editor.document.text.contains('asdf.')
  }

  void testNoWordCompletionAutoPopup() {
    myFixture.configureByText "a.java", 'class Bar { void foo() { "f<caret>" }}'
    type 'o'
    assert !lookup
  }

  void testMethodNameRestart() {
    myFixture.configureByText "a.java", '''
public class Bar {
    private static List<java.io.File> getS<caret>

}
'''
    type 'ta'
    assert !lookup
  }

  void testTargetElementInLookup() {
    myFixture.configureByText 'a.java', '''
class Foo {
  void x__foo() {}
  void bar() {
    <caret>
  }
  void x__goo() {}
}
'''
    PsiClass cls = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
          @Override
          PsiClass compute() {
            return ((PsiJavaFile)myFixture.file).getClasses()[0]
          }
    })

    PsiMethod[] methods = ApplicationManager.getApplication().runReadAction(new Computable<PsiMethod[]>() {
              @Override
              PsiMethod[] compute() {
                return cls.methods
              }
        })
    def foo = methods[0]
    def goo = methods[2]
    type('x')
    assertContains 'x__foo', 'x__goo'
    edt {
      assert foo == TargetElementUtil.instance.findTargetElement(myFixture.editor, TargetElementUtil.LOOKUP_ITEM_ACCEPTED)
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
      assert goo == TargetElementUtil.instance.findTargetElement(myFixture.editor, TargetElementUtil.LOOKUP_ITEM_ACCEPTED)
    }

    type('_')
    myFixture.assertPreferredCompletionItems 1, 'x__foo', 'x__goo'
    edt {
      assert goo == TargetElementUtil.instance.findTargetElement(myFixture.editor, TargetElementUtil.LOOKUP_ITEM_ACCEPTED)
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP)
      assert foo == TargetElementUtil.instance.findTargetElement(myFixture.editor, TargetElementUtil.LOOKUP_ITEM_ACCEPTED)
    }
  }

  void testExplicitAutocompletionAfterAutoPopup() {
    myFixture.configureByText 'a.java', 'class Foo <caret>'
    type 'ext'

    CompletionAutoPopupHandler.ourTestingAutopopup = false
    edt {
      myFixture.completeBasic()
    }
    assert !lookup : myFixture.lookupElementStrings
    myFixture.checkResult 'class Foo extends <caret>'
  }

  void testExplicitMultipleVariantCompletionAfterAutoPopup() {
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
    })
    assert myFixture.lookupElementStrings.containsAll(['private', 'protected'])
  }

  void testExactMatchesFirst() {
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

  void testExactMatchesTemplateFirst() {
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable())
    myFixture.configureByText("a.java", """
public class Test {
    void itar() {}

    void foo() {
        ita<caret>
    }
}""")
    type 'r'
    assert myFixture.lookupElementStrings == ['itar', 'itar']
    assert myFixture.lookup.currentItem instanceof LiveTemplateLookupElement
  }

  void testMoreRecentExactMatchesTemplateFirst() {
    TemplateManager manager = TemplateManager.getInstance(getProject())
    Template template = manager.createTemplate("itar", "myGroup", null)
    JavaCodeContextType contextType = ContainerUtil.findInstance(TemplateContextType.EP_NAME.getExtensions(), JavaCodeContextType.Statement)
    ((TemplateImpl)template).templateContext.setEnabled(contextType, true)
    CodeInsightTestUtil.addTemplate(template, myFixture.testRootDisposable)
    
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.testRootDisposable)
    myFixture.configureByText("a.java", """
public class Test {
    void foo() {
        ita<caret>
    }
}""")
    type 'r'
    myFixture.assertPreferredCompletionItems(0, 'itar', 'itar')
  }


  void testUpdatePrefixMatchingOnTyping() {
    myFixture.addClass("class CertificateEncodingException {}")
    myFixture.addClass("class CertificateException {}")
    myFixture.configureByText 'a.java', 'class Foo {<caret>}'
    type 'CertificateExce'
    assert myFixture.lookupElementStrings == ['CertificateException', 'CertificateEncodingException']
  }

  void testNoClassesInUnqualifiedImports() {
    myFixture.addClass("package xxxxx; public class Xxxxxxxxx {}")
    myFixture.configureByText 'a.java', 'package foo; import <caret>'
    type 'xxx'
    assert !lookup
  }

  void testPopupAfterDotAfterPackage() {
    myFixture.configureByText 'a.java', '<caret>'
    type 'import jav'
    assert lookup
    type '.'
    assert lookup
  }

  void testSamePrefixIgnoreCase() {
    myFixture.addClass("package xxxxx; public class SYSTEM_EXCEPTION {}")
    myFixture.configureByText "a.java", "import xxxxx.*; class Foo { S<caret> }"
    type 'Ystem'
    myFixture.assertPreferredCompletionItems 1, 'System', 'SYSTEM_EXCEPTION'
  }

  void testSamePrefixIgnoreCase2() {
    myFixture.addClass("package xxxxx; public class SYSTEM_EXCEPTION {}")
    myFixture.addClass("package xxxxx; public class SYstem {}")
    myFixture.configureByText "a.java", "import xxxxx.*; class Foo { S<caret> }"
    type 'Ystem'
    myFixture.assertPreferredCompletionItems 0, 'SYstem', 'System', 'SYSTEM_EXCEPTION'
  }

  private FileEditor openEditorForUndo() {
    FileEditor editor
    edt { editor = FileEditorManager.getInstance(project).openFile(myFixture.file.virtualFile, false)[0] }
    def manager = (UndoManagerImpl) UndoManager.getInstance(project)
    def old = manager.editorProvider
    manager.editorProvider = new CurrentEditorProvider() {
      @Override
      FileEditor getCurrentEditor() {
        return editor
      }
    }
    disposeOnTearDown ({ manager.editorProvider = old } as Disposable)
    return editor
  }

  void testAutopopupTypingUndo() {
    myFixture.configureByText "a.java", "class Foo {{ <caret> }}"
    def editor = openEditorForUndo()
    type 'aioobeeee'
    edt { UndoManager.getInstance(project).undo(editor) }
    assert !myFixture.editor.document.text.contains('aioo')
  }

  void testNoLiveTemplatesAfterDot() {
    myFixture.configureByText "a.java", "import java.util.List; class Foo {{ List t; t.<caret> }}"
    type 'toar'
    assert myFixture.lookupElementStrings == ['toArray', 'toArray']
  }

  void testTypingFirstVarargDot() {
    myFixture.configureByText "a.java", "class Foo { void foo(Foo<caret>[] a) { }; class Bar {}}"
    type '.'
    assert !lookup
  }

  void testMulticaret() {
    doTestMulticaret """
class Foo {{
  <selection>t<caret></selection>x;
  <selection>t<caret></selection>x;
}}""", '\n', '''
class Foo {{
  toString()<caret>x;
  toString()<caret>x;
}}'''
  }

  void testMulticaretTab() {
    doTestMulticaret """
class Foo {{
  <selection>t<caret></selection>x;
  <selection>t<caret></selection>x;
}}""", '\t', '''
class Foo {{
  toString()<caret>;
  toString()<caret>;
}}'''
  }

  void testMulticaretBackspace() {
    doTestMulticaret """
class Foo {{
  <selection>t<caret></selection>;
  <selection>t<caret></selection>;
}}""", '\b\t', '''
class Foo {{
  toString()<caret>;
  toString()<caret>;
}}'''
  }

  private doTestMulticaret(final String textBefore, final String toType, final String textAfter) {
    myFixture.configureByText "a.java", textBefore
    type 'toStr'
    assert lookup
    type toType
    myFixture.checkResult textAfter
  }

  void "test two non-imported classes when space selects first autopopup item"() {
    myFixture.addClass("package foo; public class Abcdefg {}")
    myFixture.configureByText 'a.java', 'class Foo extends <caret>'
    type 'Abcde '
    myFixture.checkResult 'import foo.Abcdefg;\n\nclass Foo extends Abcdefg <caret>'
  }

  void "test two non-imported classes when space does not select first autopopup item"() {
    CodeInsightSettings.instance.SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS = false

    myFixture.addClass("package foo; public class Abcdefg {}")
    myFixture.addClass("package bar; public class Abcdefg {}")
    myFixture.configureByText 'a.java', 'class Foo extends <caret>'
    type 'Abcde'
    assert lookup.items.size() == 2
    edt { myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN) }
    type ' '
    myFixture.checkResult '''import foo.Abcdefg;

class Foo extends Abcdefg <caret>'''
  }

  void testTwoNonImportedClasses_() {
    myFixture.addClass("package foo; public class Abcdefg {}")
    myFixture.addClass("package bar; public class Abcdefg {}")
    myFixture.configureByText 'a.java', 'class Foo extends <caret>'
    type 'Abcde'
    assert lookup.items.size() == 2
  }

  void testClassNameInProperties() {
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

  void testEnteringLabel() {
    myFixture.configureByText 'a.java', '''class Foo {{
  <caret>
}}
'''
    type 'FIS:'
    assert myFixture.editor.document.text.contains('FIS:')
  }

  void testSoutvTemplate() {
    TemplateManagerImpl.setTemplateTesting(getProject(), myFixture.getTestRootDisposable())
    myFixture.configureByText 'a.java', 'class Foo {{ <caret> }}'
    type 'soutv\tgetcl.'
    myFixture.checkResult '''\
class Foo {{
    System.out.println("getClass(). = " + getClass().<caret>);
}}'''
  }

  void testReturnLParen() {
    myFixture.configureByText 'a.java', 'class Foo { int foo() { <caret> }}'
    type 're('
    myFixture.checkResult 'class Foo { int foo() { re(<caret>) }}'
  }

  void testAmbiguousClassQualifier() {
    myFixture.addClass("package foo; public class Util<T> { public static void foo() {} public static final int CONSTANT = 2; }")
    myFixture.addClass("package bar; public class Util { public static void bar() {} }")
    myFixture.configureByText 'a.java', 'class Foo {{ Util<caret> }}'
    type '.'
    assert myFixture.lookupElementStrings as Set == ['Util.bar', 'Util.CONSTANT', 'Util.foo'] as Set

    def constant = myFixture.lookupElements.find { it.lookupString == 'Util.CONSTANT' }
    LookupElementPresentation p = ApplicationManager.application.runReadAction ({ LookupElementPresentation.renderElement(constant) } as Computable<LookupElementPresentation>)
    assert p.itemText == 'Util.CONSTANT'
    assert p.tailText == ' ( = 2) (foo)'
    assert p.typeText == 'int'

    type 'fo\n'
    myFixture.checkResult '''import foo.Util;

class Foo {{
    Util.foo();<caret> }}'''
  }

  void testPackageQualifier() {
    CodeInsightSettings.instance.SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS = false

    myFixture.addClass("package com.too; public class Util {}")
    myFixture.configureByText 'a.java', 'class Foo { void foo(Object command) { <caret> }}'
    type 'com.t'
    assert myFixture.lookupElementStrings.containsAll(['too', 'command.toString'])
  }

  void testUnfinishedString() {
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

  void testVarargParenthesis() {
    myFixture.configureByText 'a.java', '''
class Foo {
  void foo(File... files) { }
  { foo(new <caret>) }
}
'''
    type 'File('
    assert myFixture.editor.document.text.contains('new File()')
  }

  void "test inaccessible class in another package shouldn't prevent choosing by space"() {
    myFixture.addClass("package foo; class b {}")
    myFixture.configureByText 'a.java', 'class Foo {{ <caret> }}'
    type 'b'
    assert lookup?.currentItem?.lookupString == 'boolean'
    type ' '
    myFixture.checkResult 'class Foo {{ boolean <caret> }}'
  }

  @Override
  protected void setUp() {
    super.setUp()
    CodeInsightSettings.instance.SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS = true
  }

  @Override
  protected void tearDown() {
    CodeInsightSettings.instance.SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS = false
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER
    UISettings.getInstance()setSortLookupElementsLexicographically(false)

    super.tearDown()
  }

  void testBackspaceShouldShowPreviousVariants() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
    myFixture.configureByText 'a.java', 'class Foo{ void foo(int itera, int itex) { it<caret> }}'
    type 'e'
    myFixture.assertPreferredCompletionItems 0, 'itera', 'itex'
    type 'r'
    myFixture.assertPreferredCompletionItems 0, 'iter', 'itera'
    type '\b'
    myFixture.assertPreferredCompletionItems 0, 'itera', 'itex'
  }

  void testBackspaceUntilDot() {
    myFixture.configureByText 'a.java', 'class Foo{ void foo(String s) { s<caret> }}'
    type '.sub'
    assert myFixture.lookupElementStrings
    type '\b\b\b'
    assert lookup
    type '\b'
    assert !lookup
  }

  void testReplaceTypedPrefixPart() {
    ((StatisticsManagerImpl)StatisticsManager.getInstance()).enableStatistics(myFixture.getTestRootDisposable())
    myFixture.configureByText 'a.java', 'class Foo{ { <caret> }}'
    for (i in 0..StatisticsManager.OBLIVION_THRESHOLD) {
      type 'System.out.printl\n\n'
    }
    type 'System.out.pr'
    assert lookup.currentItem.lookupString == 'println'
    type '\n2'
    assert myFixture.editor.document.text.contains('.println();2')
  }

  void testQuickBackspaceEnter() {
    myFixture.configureByText 'a.java', '<caret>'
    type 'cl'
    assert myFixture.lookupElementStrings == ['class']
    myFixture.type('\b\n')
    myFixture.checkResult('class <caret>')
  }

  void joinCompletion() {
    myTester.joinCompletion()
  }

  protected def joinCommit(Closure c1={}) {
    myTester.joinCommit(c1)
  }

  protected void joinAutopopup() {
    myTester.joinAutopopup()
  }

  void "test new primitive array in Object variable"() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
    myFixture.configureByText 'a.java', '''
class Foo {
  void foo() {
    Object o = new <caret>
  }
}
'''
    type 'int'
    myFixture.assertPreferredCompletionItems 0, 'int', 'Integer'
    assert ((PsiTypeLookupItem) myFixture.lookupElements[0]).bracketsCount == 1
    type '['
    myFixture.checkResult '''
class Foo {
  void foo() {
    Object o = new int[<caret>]
  }
}
'''
  }

  void "test middle matching and overwrite"() {
    myFixture.configureByText 'a.java', '''
class ListConfigKey {
  void foo() {
    <caret>
  }
}
'''
    type 'CK\t'
    myFixture.checkResult '''
class ListConfigKey {
  void foo() {
    ListConfigKey<caret>
  }
}
'''

  }

  void testPreselectMostRelevantInTheMiddleAlpha() {
    UISettings.getInstance().setSortLookupElementsLexicographically(true)
    CodeInsightSettings.instance.SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS = false

    myFixture.configureByText 'a.java', '''
class Foo {
  void setText() {}
  void setHorizontalText() {}
  void foo() {
    <caret>
  }

}
'''
    type 'sette'
    myFixture.assertPreferredCompletionItems 1, 'setHorizontalText', 'setText'
    edt { myFixture.performEditorAction IdeActions.ACTION_EDITOR_MOVE_CARET_UP }
    myFixture.assertPreferredCompletionItems 0, 'setHorizontalText', 'setText'
  }

  void "test pressing enter while autopopup is calculating variants should cancel autopopup"() {
    registerContributor(LongContributor, LoadingOrder.FIRST)
    myFixture.configureByText "a.java", "class Foo {{ <caret> }}"
    myFixture.type('a')
    joinAutopopup()
    type('\n')
    assert !lookup
  }

  void "test pressing enter and a letter while autopopup is calculating variants should restart autopopup"() {
    registerContributor(LongContributor, LoadingOrder.FIRST)
    myFixture.configureByText "a.java", "class Foo {{ <caret> }}"
    myFixture.type('a')
    joinAutopopup()
    myFixture.type('\nf')
    joinCompletion()
    assert lookup
  }

  void "test a random write action shouldn't cancel autopopup"() {
    registerContributor(LongContributor, LoadingOrder.FIRST)
    myFixture.configureByText "a.java", "class Foo {{ <caret> }}"
    myFixture.type('a')
    joinAutopopup()
    edt { ApplicationManager.application.runWriteAction {} }
    joinCompletion()
    assert lookup
  }

  void "test typing during restart commit document"() {
    def longText = "\nfoo(); bar();" * 100
    myFixture.configureByText "a.java", "class Foo { void foo(int ab, int abde) { <caret>; $longText }}"
    myFixture.type('a')
    joinAutopopup()
    myFixture.type('b')
    myTester.joinCommit()
    myFixture.type('c')
    joinCompletion()
    assert !lookup
  }

  void "test no name autopopup in live template"() {
    TemplateManagerImpl.setTemplateTesting(getProject(), myFixture.getTestRootDisposable())
    myFixture.configureByText 'a.java', '''class F {
  String nameContainingIdentifier;
<caret>
}'''

    final TemplateManager manager = TemplateManager.getInstance(getProject())
    final Template template = manager.createTemplate("m", "user", 'void foo(String $V1$) {}')
    template.addVariable("V1", "", '"s"', true)

    edt {
      CommandProcessor.instance.executeCommand project, {manager.startTemplate(myFixture.editor, template)}, null, null
    }

    type('name')
    assert !myFixture.lookupElementStrings
    assert !lookup
  }

  void "test template prefix is better than middle matches"() {
    myFixture.configureByText "a.java", """
class Cls {
  void foo() {
    <caret>
  }
  void mySout() {}
}
""" 
    type('sout')
    myFixture.assertPreferredCompletionItems 0, 'sout', 'mySout'
  }

  void "test single overriding getter"() {
    myFixture.configureByText "a.java", """
public class Foo {
    public int getField() {}
}

class X extends Foo {
    int field;

    <caret>
}
"""
    type 'getf'
    assert myFixture.lookupElementStrings == ['public int getField']
  }

  void "test live template quick doc"() {
    myFixture.configureByText "a.java", """
class Cls {
  void foo() {
    <caret>
  }
  void mySout() {}
}
""" 
    type('sout')
    assert lookup
    assert 'sout' in myFixture.lookupElementStrings

    def docProvider = new LiveTemplateDocumentationProvider()
    def docElement = docProvider.getDocumentationElementForLookupItem(myFixture.psiManager, lookup.currentItem, null)
    assert (docElement as NavigatablePsiElement).presentation.presentableText == 'sout'
    assert docProvider.generateDoc(docElement, docElement).contains('System.out')
  }

  void "test finishing class reference property value completion with dot opens autopopup"() {
    myFixture.configureByText "a.properties", "myprop=ja<caret>"
    type 'v'
    myFixture.assertPreferredCompletionItems 0, 'java'
    lookup.focusDegree = LookupImpl.FocusDegree.FOCUSED
    type '.'
    myFixture.checkResult 'myprop=java.<caret>'
    assert lookup
  }

  void "test live template without description"() {
    final TemplateManager manager = TemplateManager.getInstance(getProject())
    final Template template = manager.createTemplate("tpl", "user", null)
    final JavaCodeContextType contextType =
      ContainerUtil.findInstance(TemplateContextType.EP_NAME.getExtensions(), JavaCodeContextType.Statement)
    ((TemplateImpl)template).getTemplateContext().setEnabled(contextType, true)
    CodeInsightTestUtil.addTemplate(template, myFixture.testRootDisposable)
    
    myFixture.configureByText 'a.java', '''
class Foo {
 int tplMn;
 
 { <caret> }
}
'''
    type 'tpl'
    myFixture.assertPreferredCompletionItems 0, 'tpl', 'tplMn'

    LookupElementPresentation p = LookupElementPresentation.renderElement(myFixture.lookupElements[0])
    assert p.itemText == 'tpl'
    assert !p.tailText
    def tabKeyPresentation = KeyEvent.getKeyText(TemplateSettings.TAB_CHAR as int)
    assert p.typeText == "  [$tabKeyPresentation] "
  }

  void "test autopopup after package completion"() {
    myFixture.addClass("package foo.bar.goo; class Foo {}")
    myFixture.configureByText "a.java", "class Foo { { foo.b<caret> } }"
    def items = myFixture.completeBasic()
    joinCompletion()
    if (items != null) { // completion took a bit longer, and the single item wasn't inserted automatically 
      assert myFixture.lookupElementStrings == ['bar']
      myFixture.type('\n')
    }
    assert myFixture.editor.document.text.contains('foo.bar. ')
    joinAutopopup()
    joinCompletion()
    assert lookup
    assert myFixture.lookupElementStrings == ['goo']
  }

  void "test in column selection mode"() {
    myFixture.configureByText "a.java", """
class Foo {{
  <caret>
}}"""
    edt { ((EditorEx)myFixture.editor).setColumnMode(true) }
    type 'toStr'
    assert lookup
  }

  void "test show popup with single live template if show_live_tempate_in_completion option is enabled"() {
    LiveTemplateCompletionContributor.setShowTemplatesInTests(false, myFixture.getTestRootDisposable())
    myFixture.configureByText "a.java", """
class Foo {{
ita<caret>
"""
    type 'r'
    assert lookup == null
    
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable())
    type '\br'
    assert lookup
    assert myFixture.lookupElementStrings == ['itar']
  }

  void "test expand class list when typing more or moving caret"() {
    myFixture.addClass 'package foo; public class KimeFamilyRange {}'
    myFixture.addClass 'package foo; public class FamiliesRangesMetaData {}'
    myFixture.addClass 'public class KSomethingInCurrentPackage {}'
    myFixture.configureByText 'a.java', 'class Foo { <caret> }'

    type 'F'
    assert !myFixture.lookupElementStrings.contains('KimeFamilyRange')

    type 'aRa'
    myFixture.assertPreferredCompletionItems 0, 'FamiliesRangesMetaData', 'KimeFamilyRange'

    4.times {
      edt { myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT) }
      joinCompletion()
    }
    assert !myFixture.lookupElementStrings.contains('KimeFamilyRange')

    type 'K'

    4.times {
      edt { myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT) }
      joinCompletion()
    }

    myFixture.assertPreferredCompletionItems 0, 'KimeFamilyRange'
  }

  void "test show autopopup when typing digit after letter"() {
    myFixture.configureByText 'a.java', 'class Foo {{ int a42; a<caret> }}'
    type '4'
    assert lookup
  }

  void "test don't close lookup when starting a new line"() {
    myFixture.configureByText 'a.java', 'class Foo {{ "abc"<caret> }}'
    type '.'
    assert lookup
    edt {
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_START_NEW_LINE)
      assert lookup
      assert lookup.lookupStart == myFixture.editor.caretModel.offset
      assert myFixture.editor.document.text.contains('\n')
    }
  }

}
