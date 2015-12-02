/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.merge

import com.intellij.diff.DiffContentFactoryImpl
import com.intellij.diff.DiffTestCase
import com.intellij.diff.assertTrue
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.merge.MergeTestBase.SidesState.*
import com.intellij.diff.merge.TextMergeTool.TextMergeViewer
import com.intellij.diff.merge.TextMergeTool.TextMergeViewer.MyThreesideViewer
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.Side
import com.intellij.diff.util.TextDiffType
import com.intellij.diff.util.ThreeSide
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.util.ui.UIUtil

public abstract class MergeTestBase : DiffTestCase() {
  private var projectFixture: IdeaProjectTestFixture? = null
  private var project: Project? = null

  override fun setUp() {
    super.setUp()
    projectFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getTestName(true)).getFixture()
    projectFixture!!.setUp()
    project = projectFixture!!.getProject()
  }

  override fun tearDown() {
    projectFixture?.tearDown()
    project = null
    super.tearDown()
  }

  public fun test1(left: String, base: String, right: String, f: TestBuilder.() -> Unit) {
    test(left, base, right, 1, f)
  }

  public fun test2(left: String, base: String, right: String, f: TestBuilder.() -> Unit) {
    test(left, base, right, 2, f)
  }

  public fun testN(left: String, base: String, right: String, f: TestBuilder.() -> Unit) {
    test(left, base, right, -1, f)
  }

  public fun test(left: String, base: String, right: String, changesCount: Int, f: TestBuilder.() -> Unit) {
    val contentFactory = DiffContentFactoryImpl()
    val leftContent: DocumentContent = contentFactory.create(parseSource(left))
    val baseContent: DocumentContent = contentFactory.create(parseSource(base))
    val rightContent: DocumentContent = contentFactory.create(parseSource(right))
    val outputContent: DocumentContent = contentFactory.create(parseSource(""))
    outputContent.getDocument().setReadOnly(false)

    val context = MockMergeContext(project)
    val request = MockMergeRequest(leftContent, baseContent, rightContent, outputContent)

    val viewer = TextMergeTool.INSTANCE.createComponent(context, request) as TextMergeViewer
    try {
      val toolbar = viewer.init()
      UIUtil.dispatchAllInvocationEvents()

      val builder = TestBuilder(viewer, toolbar.toolbarActions ?: emptyList())
      builder.assertChangesCount(changesCount)
      builder.f()
    } finally {
      Disposer.dispose(viewer)
    }
  }

  public inner class TestBuilder(public val mergeViewer: TextMergeViewer, private val actions: List<AnAction>) {
    public val viewer: MyThreesideViewer = mergeViewer.getViewer()
    public val changes: List<TextMergeChange> = viewer.getAllChanges()
    public val editor: EditorEx = viewer.getEditor(ThreeSide.BASE)
    public val document: Document = editor.getDocument()

    private val textEditor = TextEditorProvider.getInstance().getTextEditor(editor);
    private val undoManager = UndoManager.getInstance(project!!)

    public fun change(num: Int): TextMergeChange {
      if (changes.size() < num) throw Exception("changes: ${changes.size()}, index: $num")
      return changes.get(num)
    }

    public fun activeChanges(): List<TextMergeChange> = viewer.getChanges()

    //
    // Actions
    //

    public fun runActionByTitle(name: String): Boolean {
      val action = actions.filter { name.equals(it.getTemplatePresentation().getText()) }
      assertTrue(action.size() == 1, action.toString())
      return runAction(action.get(0))
    }

    private fun runAction(action: AnAction): Boolean {
      val actionEvent = AnActionEvent.createFromAnAction(action, null, ActionPlaces.MAIN_MENU, editor.getDataContext())
      action.update(actionEvent)
      val success = actionEvent.getPresentation().isEnabledAndVisible()
      if (success) action.actionPerformed(actionEvent)
      return success
    }

    //
    // Modification
    //

    public fun command(affected: TextMergeChange, f: () -> Unit): Unit {
      command(listOf(affected), f)
    }

    public fun command(affected: List<TextMergeChange>? = null, f: () -> Unit): Unit {
      viewer.executeMergeCommand(null, affected, f)
      UIUtil.dispatchAllInvocationEvents()
    }

    public fun write(f: () -> Unit): Unit {
      ApplicationManager.getApplication().runWriteAction({ CommandProcessor.getInstance().executeCommand(project, f, null, null) })
      UIUtil.dispatchAllInvocationEvents()
    }

    public fun Int.ignore(side: Side, modifier: Boolean = false) {
      val change = change(this)
      command(change) { viewer.ignoreChange(change, side, modifier) }
    }

    public fun Int.apply(side: Side, modifier: Boolean = false) {
      val change = change(this)
      command(change) { viewer.replaceChange(change, side, modifier) }
    }

    //
    // Text modification
    //

    public fun insertText(offset: Int, newContent: CharSequence) {
      replaceText(offset, offset, newContent)
    }

    public fun deleteText(startOffset: Int, endOffset: Int) {
      replaceText(startOffset, endOffset, "")
    }

    public fun replaceText(startOffset: Int, endOffset: Int, newContent: CharSequence) {
      write { document.replaceString(startOffset, endOffset, parseSource(newContent)) }
    }

    public fun insertText(offset: LineCol, newContent: CharSequence) {
      replaceText(offset.toOffset(), offset.toOffset(), newContent)
    }

    public fun deleteText(startOffset: LineCol, endOffset: LineCol) {
      replaceText(startOffset.toOffset(), endOffset.toOffset(), "")
    }

    public fun replaceText(startOffset: LineCol, endOffset: LineCol, newContent: CharSequence) {
      write { replaceText(startOffset.toOffset(), endOffset.toOffset(), newContent) }
    }

    public fun replaceText(oldContent: CharSequence, newContent: CharSequence) {
      write {
        val range = findRange(parseSource(oldContent))
        replaceText(range.first, range.second, newContent)
      }
    }

    public fun deleteText(oldContent: CharSequence) {
      write {
        val range = findRange(parseSource(oldContent))
        replaceText(range.first, range.second, "")
      }
    }

    public fun insertTextBefore(oldContent: CharSequence, newContent: CharSequence) {
      write { insertText(findRange(parseSource(oldContent)).first, newContent) }
    }

    public fun insertTextAfter(oldContent: CharSequence, newContent: CharSequence) {
      write { insertText(findRange(parseSource(oldContent)).second, newContent) }
    }

    private fun findRange(oldContent: CharSequence): Couple<Int> {
      val text = document.getImmutableCharSequence()
      val index1 = StringUtil.indexOf(text, oldContent)
      assertTrue(index1 >= 0, "content - '\n$oldContent\n'\ntext - '\n$text'")
      val index2 = StringUtil.indexOf(text, oldContent, index1 + 1)
      assertTrue(index2 == -1, "content - '\n$oldContent\n'\ntext - '\n$text'")
      return Couple(index1, index1 + oldContent.length())
    }

    //
    // Undo
    //

    public fun undo(count: Int = 1) {
      if (count == -1) {
        while (undoManager.isUndoAvailable(textEditor)) {
          undoManager.undo(textEditor)
        }
      }
      else {
        for (i in 1..count) {
          assertTrue(undoManager.isUndoAvailable(textEditor))
          undoManager.undo(textEditor)
        }
      }
    }

    public fun redo(count: Int = 1) {
      if (count == -1) {
        while (undoManager.isRedoAvailable(textEditor)) {
          undoManager.redo(textEditor)
        }
      }
      else {
        for (i in 1..count) {
          assertTrue(undoManager.isRedoAvailable(textEditor))
          undoManager.redo(textEditor)
        }
      }
    }

    public fun checkUndo(count: Int = -1, f: TestBuilder.() -> Unit) {
      val initialState = ViewerState.recordState(viewer)
      f()
      UIUtil.dispatchAllInvocationEvents()

      val afterState = ViewerState.recordState(viewer)
      undo(count)
      UIUtil.dispatchAllInvocationEvents()

      val undoState = ViewerState.recordState(viewer)
      redo(count)
      UIUtil.dispatchAllInvocationEvents()

      val redoState = ViewerState.recordState(viewer)

      assertEquals(initialState, undoState)
      assertEquals(afterState, redoState)
    }

    //
    // Checks
    //

    public fun assertChangesCount(expected: Int) {
      if (expected == -1) return
      val actual = activeChanges().size()
      assertEquals(expected, actual)
    }

    public fun Int.assertType(type: TextDiffType, changeType: SidesState) {
      assertType(type)
      assertType(changeType)
    }

    public fun Int.assertType(type: TextDiffType) {
      val change = change(this)
      assertEquals(change.getDiffType(), type)
    }

    public fun Int.assertType(changeType: SidesState) {
      assertTrue(changeType != NONE)
      val change = change(this)
      val actual = change.getType()
      val isLeftChange = changeType != RIGHT
      val isRightChange = changeType != LEFT
      assertEquals(Pair(isLeftChange, isRightChange), Pair(actual.isLeftChange(), actual.isRightChange()))
    }

    public fun Int.assertResolved(type: SidesState) {
      val change = change(this)
      val isLeftResolved = type == LEFT || type == BOTH
      val isRightResolved = type == RIGHT || type == BOTH
      assertEquals(Pair(isLeftResolved, isRightResolved), Pair(change.isResolved(Side.LEFT), change.isResolved(Side.RIGHT)))
    }

    public fun Int.assertRange(start: Int, end: Int) {
      val change = change(this)
      assertEquals(Pair(start, end), Pair(change.getStartLine(ThreeSide.BASE), change.getEndLine(ThreeSide.BASE)))
    }

    public fun Int.assertContent(expected: String, start: Int, end: Int) {
      assertContent(expected)
      assertRange(start, end)
    }

    public fun Int.assertContent(expected: String) {
      val change = change(this)
      val document = editor.getDocument()
      val actual = DiffUtil.getLinesContent(document, change.getStartLine(ThreeSide.BASE), change.getEndLine(ThreeSide.BASE))
      assertEquals(parseSource(expected), actual)
    }

    public fun assertContent(expected: String) {
      val actual = viewer.getEditor(ThreeSide.BASE).getDocument().getImmutableCharSequence()
      assertEquals(parseSource(expected), actual)
    }

    //
    // Helpers
    //

    public operator fun Int.not(): LineColHelper = LineColHelper(this)
    public operator fun LineColHelper.minus(col: Int): LineCol = LineCol(this.line, col)

    public inner class LineColHelper(val line: Int) {
    }

    public inner class LineCol(val line: Int, val col: Int) {
      public fun toOffset(): Int = editor.getDocument().getLineStartOffset(line) + col
    }
  }

  private class MockMergeContext(private val myProject: Project?) : MergeContext() {
    override fun getProject(): Project? = myProject

    override fun isFocused(): Boolean = false

    override fun requestFocus() {
    }

    override fun finishMerge(result: MergeResult) {
    }
  }

  private class MockMergeRequest(val left: DocumentContent,
                                 val base: DocumentContent,
                                 val right: DocumentContent,
                                 val output: DocumentContent) : TextMergeRequest() {
    override fun getTitle(): String? = null

    override fun applyResult(result: MergeResult) {
    }

    override fun getContents(): List<DocumentContent> = listOf(left, base, right)

    override fun getOutputContent(): DocumentContent = output

    override fun getContentTitles(): List<String?> = listOf(null, null, null)
  }

  public enum class SidesState {
    LEFT, RIGHT, BOTH, NONE
  }

  private data class ViewerState private constructor(private val content: CharSequence,
                                                     private val changes: List<ViewerState.ChangeState>) {
    companion object {
      public fun recordState(viewer: MyThreesideViewer): ViewerState {
        val content = viewer.getEditor(ThreeSide.BASE).getDocument().getImmutableCharSequence()
        val changes = viewer.getAllChanges().map { recordChangeState(viewer, it) }
        return ViewerState(content, changes)
      }

      private fun recordChangeState(viewer: MyThreesideViewer, change: TextMergeChange): ChangeState {
        val document = viewer.getEditor(ThreeSide.BASE).getDocument();
        val content = DiffUtil.getLinesContent(document, change.getStartLine(ThreeSide.BASE), change.getEndLine(ThreeSide.BASE))

        val resolved = if (change.isResolved()) BOTH else if (change.isResolved(Side.LEFT)) LEFT else if (change.isResolved(Side.RIGHT)) RIGHT else NONE

        val starts = Trio.from { change.getStartLine(it) }
        val ends = Trio.from { change.getStartLine(it) }

        return ChangeState(content, starts, ends, resolved)
      }
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is ViewerState) return false

      if (!StringUtil.equals(content, other.content)) return false
      if (!changes.equals(other.changes)) return false
      return true
    }

    override fun hashCode(): Int = StringUtil.hashCode(content)

    private data class ChangeState(private val content: CharSequence,
                                   private val starts: Trio<Int>,
                                   private val ends: Trio<Int>,
                                   private val resolved: SidesState) {
      override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChangeState) return false

        if (!StringUtil.equals(content, other.content)) return false
        if (!starts.equals(other.starts)) return false
        if (!ends.equals(other.ends)) return false
        if (!resolved.equals(other.resolved)) return false
        return true
      }
    }
  }
}
