// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.console

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.impl.ConsoleViewUtil
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.highlighter.HighlighterFactory
import com.intellij.injected.editor.EditorWindow
import com.intellij.lang.Language
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.ex.ActionUtil.copyRegisteredShortcuts
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.undo.UndoUtil
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.impl.EditorFactoryImpl
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtilBase
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiUtilCore
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.ExperimentalUI.Companion.isNewUI
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollBar
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.DocumentUtil
import com.intellij.util.FileContentUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Adjustable
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.util.*
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollBar
import kotlin.math.max
import kotlin.math.min

/**
 * @author Gregory.Shrago
 * In case of REPL consider to use [LanguageConsoleBuilder]
 */
open class LanguageConsoleImpl(private val myHelper: Helper) : ConsoleViewImpl(
  myHelper.project, GlobalSearchScope.allScope(myHelper.project), true, true), LanguageConsoleView {
  private val consoleExecutionEditor = ConsoleExecutionEditor(myHelper)
  override val historyViewer: EditorEx
  private val myPanel: JPanel = ConsoleEditorsPanel(this)
  private val myScrollBar: JScrollBar = JBScrollBar(Adjustable.HORIZONTAL)
  private val myMergedScrollBarModel: MergedHorizontalScrollBarModel
  private val myDocumentAdapter: DocumentListener = object : DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
      myPanel.revalidate()
    }
  }

  constructor(project: Project, title: String, language: Language) : this(Helper(
    project = project,
    virtualFile = LightVirtualFile(title, language, ""),
  ))

  init {
    @Suppress("LeakingThis")
    Disposer.register(this, consoleExecutionEditor)
    @Suppress("LeakingThis")
    historyViewer = doCreateHistoryEditor()
    historyViewer.document.addDocumentListener(myDocumentAdapter)
    consoleExecutionEditor.document.addDocumentListener(myDocumentAdapter)
    myMergedScrollBarModel = MergedHorizontalScrollBarModel.create(myScrollBar, historyViewer, consoleExecutionEditor.editor)
    myScrollBar.putClientProperty(JBScrollPane.Alignment::class.java, JBScrollPane.Alignment.BOTTOM)
  }

  companion object {
    @JvmStatic
    fun printWithHighlighting(console: LanguageConsoleView, inputEditor: Editor, textRange: TextRange): String {
      val text: String
      val highlighter: EditorHighlighter
      if (inputEditor is EditorWindow) {
        val file = inputEditor.injectedFile
        highlighter =
          HighlighterFactory.createHighlighter(file.virtualFile, EditorColorsManager.getInstance().globalScheme, console.project)
        val fullText = InjectedLanguageUtilBase.getUnescapedText(file, null, null)
        highlighter.setText(fullText)
        text = textRange.substring(fullText)
      }
      else {
        text = inputEditor.document.getText(textRange)
        highlighter = inputEditor.highlighter
      }
      val syntax =
        if (highlighter is LexerEditorHighlighter) highlighter.syntaxHighlighter else null
      val consoleImpl = console as LanguageConsoleImpl
      consoleImpl.doAddPromptToHistory()
      if (syntax != null) {
        ConsoleViewUtil.printWithHighlighting(console, text, syntax) {
          val identPrompt = consoleImpl.consoleExecutionEditor.consolePromptDecorator.indentPrompt
          if (StringUtil.isNotEmpty(identPrompt)) {
            consoleImpl.addPromptToHistoryImpl(identPrompt)
          }
        }
      }
      else {
        console.print(text, ConsoleViewContentType.USER_INPUT)
      }
      console.print("\n", ConsoleViewContentType.NORMAL_OUTPUT)
      return text
    }

    fun duplicateHighlighters(to: MarkupModel, from: MarkupModel, offset: Int, textRange: TextRange, vararg disableAttributes: String?) {
      for (rangeHighlighter in from.allHighlighters) {
        if (!rangeHighlighter.isValid) {
          continue
        }
        val highlightInfo = HighlightInfo.fromRangeHighlighter(rangeHighlighter)
        if (highlightInfo != null) {
          if (highlightInfo.severity !== HighlightSeverity.INFORMATION) {
            continue
          }
          if (highlightInfo.type.attributesKey === EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES) {
            continue
          }

          if (Arrays.stream(disableAttributes).filter { obj: String? ->
              Objects.nonNull(obj)
            }.anyMatch { x: String? -> x == highlightInfo.type.attributesKey.externalName }) continue
        }
        val localOffset = textRange.startOffset
        val start = (max(rangeHighlighter.startOffset.toDouble(), localOffset.toDouble()) - localOffset).toInt()
        val end = (min(rangeHighlighter.endOffset.toDouble(), textRange.endOffset.toDouble()) - localOffset).toInt()
        if (start > end) {
          continue
        }
        val h = to.addRangeHighlighter(start + offset, end + offset, rangeHighlighter.layer,
                                       rangeHighlighter.getTextAttributes(null), rangeHighlighter.targetArea)
        (h as RangeHighlighterEx).isAfterEndOfLine = (rangeHighlighter as RangeHighlighterEx).isAfterEndOfLine
      }
    }
  }

  protected open fun doCreateHistoryEditor(): EditorEx {
    val editorFactory = EditorFactory.getInstance()
    val historyDocument = (editorFactory as EditorFactoryImpl).createDocument(true)
    UndoUtil.disableUndoFor(historyDocument)
    return editorFactory.createViewer(historyDocument, project, EditorKind.CONSOLE) as EditorEx
  }

  override fun doCreateConsoleEditor(): EditorEx {
    return historyViewer
  }

  override fun disposeEditor() {
  }

  override fun createCenterComponent(): JComponent {
    initComponents()
    return myPanel
  }

  override fun getPreferredFocusableComponent(): JComponent {
    return consoleEditor.contentComponent
  }

  private fun initComponents() {
    setupComponents()

    myPanel.layout = EditorMergedHorizontalScrollBarLayout(myScrollBar, historyViewer, consoleExecutionEditor.editor,
                                                           isHistoryViewerForceAdditionalColumnsUsage, minHistoryLineCount)
    myPanel.add(historyViewer.component)
    myPanel.add(consoleExecutionEditor.component)
    myPanel.add(myScrollBar)
    myPanel.background = JBColor.lazy { consoleExecutionEditor.editor.backgroundColor }
  }

  private fun setupComponents() {
    myHelper.setupEditor(consoleExecutionEditor.editor)
    myHelper.setupEditor(historyViewer)

    historyViewer.component.minimumSize = JBUI.emptySize()
    historyViewer.component.preferredSize = JBUI.emptySize()
    historyViewer.setCaretEnabled(false)

    consoleExecutionEditor.initComponent()

    historyViewer.contentComponent.addKeyListener(object : KeyAdapter() {
      override fun keyTyped(event: KeyEvent) {
        if (isConsoleEditorEnabled && UIUtil.isReallyTypedEvent(event)) {
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
            IdeFocusManager.getGlobalInstance().requestFocus(consoleExecutionEditor.editor.contentComponent, true)
          }
          consoleExecutionEditor.editor.processKeyTyped(event)
        }
      }
    })

    copyRegisteredShortcuts(historyViewer.component, consoleExecutionEditor.component)
    historyViewer.putUserData(LanguageConsoleView.EXECUTION_EDITOR_KEY, consoleExecutionEditor)

    if (isNewUI()) {
      historyViewer.settings.isLineMarkerAreaShown = false
      consoleEditor.settings.isLineMarkerAreaShown = true
    }
  }

  override var isConsoleEditorEnabled: Boolean = false
    get() = consoleExecutionEditor.isConsoleEditorEnabled
    set(consoleEditorEnabled) {
      if (field == consoleEditorEnabled) {
        return
      }
      consoleExecutionEditor.isConsoleEditorEnabled = consoleEditorEnabled
      myMergedScrollBarModel.setEnabled(consoleEditorEnabled)
    }

  override var prompt: String?
    get() = consoleExecutionEditor.prompt
    set(prompt) {
      consoleExecutionEditor.setPrompt(prompt)
    }

  override var promptAttributes: ConsoleViewContentType
    get() = consoleExecutionEditor.promptAttributes
    set(textAttributes) {
      consoleExecutionEditor.promptAttributes = textAttributes
    }

  val consolePromptDecorator: ConsolePromptDecorator
    get() = consoleExecutionEditor.consolePromptDecorator

  override var isEditable: Boolean
    get() = consoleExecutionEditor.isEditable
    set(editable) {
      consoleExecutionEditor.isEditable = editable
    }

  override val file: PsiFile
    get() = myHelper.fileSafe

  override val virtualFile: VirtualFile
    get() = consoleExecutionEditor.virtualFile

  override val editorDocument: Document
    get() = consoleExecutionEditor.document

  override val consoleEditor: EditorEx
    get() = consoleExecutionEditor.editor

  override var title: @NlsContexts.TabTitle String
    get() = myHelper.title
    set(title) {
      myHelper.setTitle(title)
    }

  fun addToHistory(textRange: TextRange, editor: EditorEx, preserveMarkup: Boolean): String {
    return addToHistoryInner(textRange, editor, false, preserveMarkup)
  }

  fun prepareExecuteAction(addToHistory: Boolean, preserveMarkup: Boolean, clearInput: Boolean): String {
    val editor = currentEditor
    val document: Document = editor.document
    val text = document.text
    val range = TextRange(0, document.textLength)
    if (!clearInput) {
      editor.selectionModel.setSelection(range.startOffset, range.endOffset)
    }

    if (addToHistory) {
      addToHistoryInner(range, editor, clearInput, preserveMarkup)
    }
    else if (clearInput) {
      setInputText("")
    }
    return text
  }

  protected fun addToHistoryInner(textRange: TextRange, editor: EditorEx, erase: Boolean, preserveMarkup: Boolean): String {
    ThreadingAssertions.assertEventDispatchThread()

    val result = addTextRangeToHistory(textRange, editor, preserveMarkup)
    if (erase) {
      DocumentUtil.writeInRunUndoTransparentAction { editor.document.deleteString(textRange.startOffset, textRange.endOffset) }
    }
    // always scroll to end on user input
    scrollToEnd()
    return result
  }

  fun addTextRangeToHistory(textRange: TextRange, inputEditor: EditorEx, preserveMarkup: Boolean): String {
    return printWithHighlighting(this, inputEditor, textRange)


    //if (preserveMarkup) {
    //  duplicateHighlighters(markupModel, DocumentMarkupModel.forDocument(inputEditor.getDocument(), myProject, true), offset, textRange);
    //  // don't copy editor markup model, i.e. brace matcher, spell checker, etc.
    //  // duplicateHighlighters(markupModel, inputEditor.getMarkupModel(), offset, textRange);
    //}
  }

  open fun doAddPromptToHistory() {
    val prompt = consoleExecutionEditor.prompt
    addPromptToHistoryImpl(prompt)
  }

  override fun dispose() {
    super.dispose()
    // double dispose via RunContentDescriptor and ContentImpl
    if (historyViewer.isDisposed) return

    consoleExecutionEditor.document.removeDocumentListener(myDocumentAdapter)
    historyViewer.document.removeDocumentListener(myDocumentAdapter)
    historyViewer.putUserData(LanguageConsoleView.EXECUTION_EDITOR_KEY, null)

    val editorFactory = EditorFactory.getInstance()
    editorFactory.releaseEditor(historyViewer)

    closeFile()
  }

  protected open fun closeFile() {
    if (project.isOpen) {
      val editorManager = FileEditorManager.getInstance(project)
      if (editorManager.isFileOpen(virtualFile)) {
        editorManager.closeFile(virtualFile)
      }
    }
  }

  override val currentEditor: EditorEx
    get() = consoleExecutionEditor.currentEditor

  override var language: Language
    get() = file.language
    set(language) {
      myHelper.setLanguage(language)
      myHelper.fileSafe
    }

  override fun setInputText(inputText: String) {
    consoleExecutionEditor.setInputText(inputText)
  }

  open val isHistoryViewerForceAdditionalColumnsUsage: Boolean
    get() = true

  protected open val minHistoryLineCount: Int
    get() = 2

  private fun addPromptToHistoryImpl(prompt: String) {
    flushDeferredText()
    val document = historyViewer.document
    val highlighter = historyViewer.markupModel.addRangeHighlighter(null, document.textLength, document.textLength, 0, HighlighterTargetArea.EXACT_RANGE)
    print(prompt, consoleExecutionEditor.promptAttributes)
    highlighter.putUserData(PROMPT_LENGTH_MARKER, prompt.length)
  }

  open class Helper(@JvmField val project: Project, @JvmField val virtualFile: VirtualFile) {
    @JvmField
    var title: @NlsSafe String
    @JvmField
    var file: PsiFile? = null

    init {
      title = virtualFile.name
    }

    fun setTitle(title: String): Helper {
      this.title = title
      return this
    }

    open fun getFile(): PsiFile {
      return ReadAction.compute<PsiFile, RuntimeException> {
        PsiUtilCore.getPsiFile(project, virtualFile)
      }
    }

    fun getDocument(): Document {
      val document = FileDocumentManager.getInstance().getDocument(virtualFile, project)
      if (document == null) {
        val language = if ((virtualFile is LightVirtualFile)) virtualFile.language else null
        throw AssertionError(String.format("no document for: %s (fileType: %s, language: %s, length: %s, valid: %s)",
                                           virtualFile,
                                           virtualFile.fileType, language, virtualFile.length,
                                           virtualFile.isValid))
      }
      return document
    }

    fun setLanguage(language: Language) {
      if (virtualFile !is LightVirtualFile) {
        throw UnsupportedOperationException()
      }
      virtualFile.language = language
      virtualFile.setContent(getDocument(), getDocument().text, false)
      FileContentUtil.reparseFiles(project, listOf<VirtualFile>(virtualFile), false)
    }

    open fun setupEditor(editor: EditorEx) {
      ConsoleViewUtil.setupLanguageConsoleEditor(editor)

      editor.setHorizontalScrollbarVisible(true)
      editor.setVerticalScrollbarVisible(true)
    }

    val fileSafe: PsiFile
      get() = if (file == null || !file!!.isValid) getFile().also { file = it } else file!!
  }

  class ConsoleEditorsPanel(val console: LanguageConsoleImpl) : JPanel(), UiDataProvider {
    override fun uiDataSnapshot(sink: DataSink) {
      DataSink.uiDataSnapshot(sink, console)
      sink[OpenFileDescriptor.NAVIGATE_IN_EDITOR] = console.consoleExecutionEditor.editor
    }
  }
}
