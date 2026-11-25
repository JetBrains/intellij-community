// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.multiverse

import com.intellij.analysis.AnalysisScope
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewContextVisibility
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture.Companion.heavyJavaCodeInsightSubFixture
import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture
import com.intellij.codeInsight.daemon.CanChangeDocumentDuringHighlighting
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.ProductionDaemonTestUtil
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator
import com.intellij.codeInsight.daemon.impl.EditorTracker
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.TestDaemonCodeAnalyzerImpl
import com.intellij.codeInsight.multiverse.CodeInsightContextProvider.Answer
import com.intellij.codeInspection.DefaultInspectionToolResultExporter
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.codeInspection.reference.RefFile
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.multiverse.LibraryContextImpl
import com.intellij.multiverse.ModuleContextImpl
import com.intellij.multiverse.SdkContextImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorWithTextEditors
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.project.stateStore
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.createGlobalContextForTool
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.Processors
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import java.beans.PropertyChangeListener
import java.lang.ref.Reference
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.time.Duration.Companion.minutes

@TestApplication
@CanChangeDocumentDuringHighlighting
class MultiverseHighlightingTest {
  private val daemonFixture = heavyJavaCodeInsightSubFixture("multiverseDaemonAnalyzerFixture") { MultiverseDaemonAnalyzerFixture(it) }
  private val fixture: DaemonAnalyzerFixture get() = daemonFixture.get()
  private val project: Project get() = fixture.project
  private val module: Module get() = fixture.module
  private val editor: Editor get() = fixture.editor!!
  private val myFile: PsiFile get() = fixture.file!!
  private val testRootDisposable: Disposable get() = fixture.testRootDisposable
  private val psiManager: PsiManager get() = PsiManager.getInstance(project)
  private val myDaemonCodeAnalyzer: DaemonCodeAnalyzerImpl get() = fixture.daemonCodeAnalyzer
  private val myTestDaemonCodeAnalyzer: TestDaemonCodeAnalyzerImpl get() = fixture.testDaemonCodeAnalyzer

  /** The name of the test method. The main module has this name too. */
  private lateinit var name: String

  /** Set it on the EDT. */
  private var context: CodeInsightContext? = null
    @RequiresEdt
    set(value) {
      field = value
      if (value != null) {
        if (fixture.file != null) {
          fixture.file = psiManager.findFile(virtualFile, value)
        }
        else {
          // using random context
          fixture.file = psiManager.findFile(virtualFile)
        }

        if (fixture.editor != null) {
          EditorContextManager.getInstance(project).setEditorContext(editor, SingleEditorContext(value))
        }
      }
    }

  private val virtualFile get() = myFile.virtualFile

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    name = testInfo.testMethod.get().name
  }

  /**
   * Runs [testBody] in the debug mode of the daemon progress indicators.
   */
  private fun runTest(testBody: suspend CoroutineScope.() -> Unit) {
    DaemonProgressIndicator.runInDebugMode<Throwable> {
      timeoutRunBlocking(2.minutes, action = testBody)
    }
  }

  @Test
  fun testLocalInspectionInSeveralContexts(): Unit = runTest {
    fixture.enableInspectionTools(FileLevelInspection(), CommentInspection())

    @Language("JAVA")
    val text = """
      // comment
    """
    fixture.configureByText(JavaFileType.INSTANCE, text)

    val root = readAction { module.rootManager.contentRoots[0] }
    PsiTestUtil.addModule(project, ModuleType.EMPTY, "module2", root)

    val contexts = getContexts()
    assertThat(contexts).hasSize(2)

    assertEquals("module2", contexts[0].getModule()!!.name)
    assertEquals("testLocalInspectionInSeveralContexts", contexts[1].getModule()!!.name)

    // highlighting in "module2" context
    withContext(Dispatchers.EDT) { context = contexts[0] }
    val infos2 = fixture.doHighlighting()
    assertThat(infos2.map { it.description }).containsExactly(
      "file-level module-context module2",
      "Comment warning module-context module2",
    )

    val allInfosBefore = readAction { getAllDocumentHighlights() }
    assertThat(allInfosBefore.map { it.description }).containsExactly(
      "file-level module-context module2",
      "Comment warning module-context module2",
    )

    // highlighting in "testLocalInspectionInSeveralContexts" context
    withContext(Dispatchers.EDT) { context = contexts[1] }
    val infos1 = fixture.doHighlighting()
    assertThat(infos1.map { it.description }).containsExactly(
      "file-level module-context testLocalInspectionInSeveralContexts",
      "Comment warning module-context testLocalInspectionInSeveralContexts",
    )

    val allInfos = readAction { getAllDocumentHighlights() }
    assertThat(allInfos.map { it.description }).containsExactly(
      "file-level module-context module2",
      "file-level module-context testLocalInspectionInSeveralContexts",
      "Comment warning module-context module2",
      "Comment warning module-context testLocalInspectionInSeveralContexts",
    )
  }

  @Test
  fun testBatchInspectionInPreferredContextOnly(): Unit = runTest {
    val descriptions = runBatchCommentInspectionInTwoModules().problemElements.values.map { it.descriptionTemplate }
    assertEquals(1, descriptions.size, "the file must be inspected only in its preferred context: $descriptions")
  }

  @Test
  @RegistryKey(key = "batch.inspections.inspect.all.code.insight.contexts", value = "true")
  fun testBatchInspectionInAllContexts(): Unit = runTest {
    val descriptions = runBatchCommentInspectionInTwoModules().problemElements.values.map { it.descriptionTemplate }
    assertThat(descriptions)
      .describedAs("the file must be inspected in each of its contexts")
      .containsExactlyInAnyOrderElementsOf(listOf("Comment warning module-context module2", "Comment warning module-context $name"))
  }

  @Test
  @RegistryKey(key = "batch.inspections.inspect.all.code.insight.contexts", value = "true")
  fun testBatchInspectionInAllContextsReportsProblemsOnFileOfEachContext(): Unit = runTest {
    val refFiles = runBatchCommentInspectionInTwoModules().problemElements.keys()
    val refFileContexts = readAction {
      refFiles.map { (it as RefFile).psiElement.codeInsightContext }
    }
    assertThat(refFileContexts)
      .describedAs("each context must have its own reference element")
      .containsExactlyInAnyOrderElementsOf(getContexts())
  }

  private suspend fun runBatchCommentInspectionInTwoModules(): DefaultInspectionToolResultExporter {
    @Language("JAVA")
    val text = """
      // comment
    """
    fixture.configureByText(JavaFileType.INSTANCE, text)

    val root = readAction { module.rootManager.contentRoots[0] }
    PsiTestUtil.addModule(project, ModuleType.EMPTY, "module2", root)
    val contexts = getContexts()
    assertEquals(2, contexts.size, "the file must have a context for each module: $contexts")

    val toolWrapper = LocalInspectionToolWrapper(CommentInspection())
    val scope = AnalysisScope(project)
    val globalContext = createGlobalContextForTool(scope, project, listOf(toolWrapper))
    withContext(Dispatchers.EDT) {
      InspectionTestUtil.runTool(toolWrapper, scope, globalContext)
    }
    return globalContext.getPresentation(toolWrapper) as DefaultInspectionToolResultExporter
  }

  // CPP-24696: a file restart dirties every context, but the daemon re-runs only the context of the editor
  @Test
  fun testAnalysisFinishesAfterEditorSwitchesContext(): Unit = runTest {
    fixture.configureByText(JavaFileType.INSTANCE, "class C {}")
    PsiTestUtil.addModule(project, ModuleType.EMPTY, "module2", readAction { module.rootManager.contentRoots[0] })
    val contexts = getContexts()
    assertThat(contexts).hasSize(2)
    val (first, second) = contexts
    val fileStatusMap = myDaemonCodeAnalyzer.fileStatusMap

    withContext(Dispatchers.EDT) {
      ProductionDaemonTestUtil.runTestInProduction(false, myDaemonCodeAnalyzer) {
        EditorTracker.getInstance(project).setActiveEditorsInTests(listOf(editor))
        context = first
        myTestDaemonCodeAnalyzer.waitForDaemonToFinish(myFile)
        assertTrue(myDaemonCodeAnalyzer.isAllAnalysisFinished(myFile), "precondition: analysis under the first context finishes")

        context = second
        myDaemonCodeAnalyzer.restart(myFile, "test")
        assertFalse(fileStatusMap.allDirtyScopesAreNullFor(editor.document, first), "the restart dirties the context the editor left")
        myTestDaemonCodeAnalyzer.waitForDaemonToFinish(myFile)
        assertFalse(fileStatusMap.allDirtyScopesAreNullFor(editor.document, first), "nothing re-analyzes the context the editor left")
        assertTrue(myDaemonCodeAnalyzer.isAllAnalysisFinished(myFile), "analysis under the second context finishes")
        val textEditor = TextEditorProvider.getInstance().getTextEditor(editor)
        assertTrue(DaemonCodeAnalyzerEx.isHighlightingCompleted(textEditor, project), "the editor reports its highlighting as completed")
      }
    }
  }

  // Two split editors show one document in different contexts, and the daemon must highlight each editor in its context
  @Test
  fun testEditHighlightsBothEditorsWithDifferentContexts(): Unit = runTest {
    fixture.enableInspectionTools(CommentInspection())
    val (first, second) = configureCommentInTwoModules()
    val document = editor.document
    val fileStatusMap = myDaemonCodeAnalyzer.fileStatusMap

    withContext(Dispatchers.EDT) {
      val editor2 = EditorFactory.getInstance().createEditor(document, project)
      Disposer.register(testRootDisposable) { EditorFactory.getInstance().releaseEditor(editor2) }
      context = first
      EditorContextManager.getInstance(project).setEditorContext(editor2, SingleEditorContext(second))

      ProductionDaemonTestUtil.runTestInProduction(false, myDaemonCodeAnalyzer) {
        EditorTracker.getInstance(project).setActiveEditorsInTests(listOf(editor, editor2))
        repeat(10) { i ->
          WriteCommandAction.runWriteCommandAction(project) {
            document.insertString(document.text.indexOf("comment") + "comment".length, "x")
          }
          PsiDocumentManager.getInstance(project).commitAllDocuments()
          myTestDaemonCodeAnalyzer.waitForDaemonToFinish(myFile)

          assertTrue(fileStatusMap.allDirtyScopesAreNullFor(document, first), "edit $i: the daemon analyzes the context of the first editor")
          assertTrue(fileStatusMap.allDirtyScopesAreNullFor(document, second), "edit $i: the daemon analyzes the context of the second editor")
          assertThat(getAllDocumentHighlights().map { it.description })
            .describedAs("edit $i: both contexts have a warning")
            .containsExactly("Comment warning module-context module2",
                             "Comment warning module-context $name")
        }
      }
    }
  }

  // Two editors of one file with an Unknown context must not merge in the dedup of the tab editors
  @Test
  fun testDaemonHighlightsEachEditorWithUnknownContext(): Unit = runTest {
    fixture.configureByText(JavaFileType.INSTANCE, "class C {}")
    val document = editor.document
    // Other subsystems resolve the context of a new editor in the background, so drop the contexts right before the daemon reads them.
    // The daemon reads the embedded editors right before it computes their dedup keys.
    var dropContexts = true
    withContext(Dispatchers.EDT) {
      val embeddedEditors = List(2) { createEditor(document) }
      openInEditorWithTextEditors(embeddedEditors) {
        if (dropContexts) {
          project.messageBus.syncPublisher(CodeInsightContextManager.topic).contextsChanged()
        }
      }
      val startedEditors = collectStartedEditors()
      project.messageBus.connect(testRootDisposable).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonCodeAnalyzer.DaemonListener {
        override fun daemonStarting(fileEditors: Collection<FileEditor>) {
          dropContexts = false
        }
      })

      ProductionDaemonTestUtil.runTestInProduction(false, myDaemonCodeAnalyzer) {
        EditorTracker.getInstance(project).setActiveEditorsInTests(emptyList())
        myTestDaemonCodeAnalyzer.waitForDaemonToFinish(myFile)
        assertThat(startedEditors.distinct()).describedAs("the daemon highlights each editor").containsExactlyInAnyOrderElementsOf(embeddedEditors)
      }
    }
  }

  // An invalid embedded editor must not claim the dedup key of a valid editor in the same context
  @Test
  fun testInvalidEmbeddedEditorDoesNotMaskValidEditor(): Unit = runTest {
    fixture.configureByText(JavaFileType.INSTANCE, "class C {}")
    val document = editor.document
    val context = getPreferredContext()
    withContext(Dispatchers.EDT) {
      val invalidEditor = EditorFactory.getInstance().createEditor(document, project)
      val validEditor = createEditor(document)
      for (embeddedEditor in listOf(invalidEditor, validEditor)) {
        EditorContextManager.getInstance(project).setEditorContext(embeddedEditor, SingleEditorContext(context))
      }
      EditorFactory.getInstance().releaseEditor(invalidEditor)
      openInEditorWithTextEditors(listOf(invalidEditor, validEditor))
      val startedEditors = collectStartedEditors()

      ProductionDaemonTestUtil.runTestInProduction(false, myDaemonCodeAnalyzer) {
        EditorTracker.getInstance(project).setActiveEditorsInTests(emptyList())
        myTestDaemonCodeAnalyzer.waitForDaemonToFinish(myFile)
        assertThat(startedEditors.distinct()).describedAs("the daemon highlights the valid editor only").containsExactly(validEditor)
      }
    }
  }

  // A context switch must schedule the highlighting of the new context without an explicit restart.
  // setEditorContext requires the write lock, and the daemon restarts when the write action finishes.
  @Test
  fun testContextSwitchRestartsDaemon(): Unit = runTest {
    val (first, second) = configureCommentInTwoModules()
    val document = editor.document
    val fileStatusMap = myDaemonCodeAnalyzer.fileStatusMap

    withContext(Dispatchers.EDT) {
      context = first

      ProductionDaemonTestUtil.runTestInProduction(false, myDaemonCodeAnalyzer) {
        EditorTracker.getInstance(project).setActiveEditorsInTests(listOf(editor))
        myTestDaemonCodeAnalyzer.waitForDaemonToFinish(myFile)
        assertTrue(fileStatusMap.allDirtyScopesAreNullFor(document, first), "precondition: the daemon analyzes the first context")
        assertFalse(fileStatusMap.allDirtyScopesAreNullFor(document, second), "precondition: the second context is not analyzed")

        WriteAction.run<Throwable> { EditorContextManager.getInstance(project).setEditorContext(editor, SingleEditorContext(second)) }
        // the restart goes through the file status queue before it schedules the update runnable
        myTestDaemonCodeAnalyzer.waitForUpdateFileStatusBackgroundQueueInTests()
        assertTrue(myDaemonCodeAnalyzer.isRunningOrPending, "the context switch restarts the daemon")
        myTestDaemonCodeAnalyzer.waitForDaemonToFinish(myFile)
        assertTrue(fileStatusMap.allDirtyScopesAreNullFor(document, second), "the daemon analyzes the second context")
      }
    }
  }

  // The Problems View hides a context with stale highlighters that no editor shows, until the daemon analyzes that context again
  @Test
  fun testProblemsViewHidesStaleHiddenContext(): Unit = runTest {
    fixture.enableInspectionTools(CommentInspection())
    val (hidden, shown) = configureCommentInTwoModules()
    val document = editor.document
    val fileStatusMap = myDaemonCodeAnalyzer.fileStatusMap

    withContext(Dispatchers.EDT) { context = hidden }
    fixture.doHighlighting()
    withContext(Dispatchers.EDT) { context = shown }
    fixture.doHighlighting()
    assertThat(findHiddenContexts(hidden, shown)).describedAs("the view shows both analyzed contexts").isEmpty()

    editCommentAndAssertHiddenCommentIsInvalid(hidden)
    fixture.doHighlighting()
    assertFalse(fileStatusMap.allDirtyScopesAreNullFor(document, hidden), "precondition: the edit dirties the context that the editor left")
    assertTrue(fileStatusMap.allDirtyScopesAreNullFor(document, shown), "precondition: the daemon analyzes the context of the editor")
    assertEquals(setOf(hidden), findHiddenContexts(hidden, shown), "the view hides the stale context that no editor shows")

    withContext(Dispatchers.EDT) { context = hidden }
    assertThat(findHiddenContexts(hidden, shown)).describedAs("the view shows the context of the editor at once").isEmpty()
    fixture.doHighlighting()
    assertThat(findHiddenContexts(hidden, shown)).describedAs("the view shows the analyzed context").isEmpty()
  }

  // The daemon does not highlight an editor that is not active or selected, so the context of such an editor can be stale
  @Test
  fun testProblemsViewHidesStaleContextOfEditorThatDaemonDoesNotHighlight(): Unit = runTest {
    fixture.enableInspectionTools(CommentInspection())
    val (hidden, shown) = configureCommentInTwoModules()
    val document = editor.document
    withContext(Dispatchers.EDT) { context = hidden }
    fixture.doHighlighting()
    withContext(Dispatchers.EDT) { context = shown }
    fixture.doHighlighting()
    withContext(Dispatchers.EDT) {
      val notHighlightedEditor = createEditor(document)
      EditorContextManager.getInstance(project).setEditorContext(notHighlightedEditor, SingleEditorContext(hidden))
      assertFalse(myDaemonCodeAnalyzer.selectedEditors.any { (it as? TextEditor)?.editor == notHighlightedEditor },
                  "precondition: the daemon does not highlight the second editor")
    }

    editCommentAndAssertHiddenCommentIsInvalid(hidden)
    fixture.doHighlighting()
    assertFalse(myDaemonCodeAnalyzer.fileStatusMap.allDirtyScopesAreNullFor(document, hidden),
                "precondition: the edit dirties the context of the second editor")

    assertEquals(setOf(hidden), findHiddenContexts(hidden, shown),
                 "the view hides the stale context of an editor that the daemon does not highlight")
  }

  // The Problems View shows one context only when the owner of the file does not support simultaneous rendering.
  // Both contexts are analyzed, so only the second rule hides a context.
  @Test
  fun testProblemsViewShowsOneContextOfProviderWithoutSimultaneousRendering(): Unit = runTest {
    fixture.configureByText(JavaFileType.INSTANCE, "class C {}")
    val first = FakeContext("first")
    val second = FakeContext("second")
    registerOwningProvider(virtualFile, Answer.Contexts(listOf(first, second)), supportsSimultaneousRendering = false)
    markAnalyzed(first)
    markAnalyzed(second)

    suspend fun findHiddenContexts() = findHiddenContexts(first, second, supportsSimultaneousRendering = false)

    withContext(Dispatchers.EDT) {
      context = first
      assertEquals(setOf(second), findHiddenContexts(), "the view shows the context of the editor only")
      context = second
      assertEquals(setOf(first), findHiddenContexts(), "the view follows the context of the editor")
    }
  }

  /**
   * @return the contexts of [contexts] that the Problems View hides for the document of the editor. The rule of the view root.
   * [supportsSimultaneousRendering] is the answer of the owner of the file. The root reads it in a background read action,
   * and `ProblemsViewHighlightingFileRootContextsTest` covers that read.
   */
  private suspend fun findHiddenContexts(
    vararg contexts: CodeInsightContext,
    supportsSimultaneousRendering: Boolean = true,
  ): Set<CodeInsightContext> = withContext(Dispatchers.EDT) {
    val document = editor.document
    val preferredContext = if (supportsSimultaneousRendering) null else CodeInsightContextManager.getInstance(project).getPreferredContext(virtualFile)
    val visibility = ProblemsViewContextVisibility.compute(project, document, supportsSimultaneousRendering, preferredContext)
    contexts.filterTo(HashSet()) { context ->
      !visibility.isVisible(context) { ProblemsViewContextVisibility.isAnalyzed(project, document, it) }
    }
  }

  private fun markAnalyzed(context: CodeInsightContext) {
    myDaemonCodeAnalyzer.fileStatusMap.markFileUpToDateForAllPasses(editor.document, context)
  }

  private suspend fun editCommentAndAssertHiddenCommentIsInvalid(hidden: CodeInsightContext) {
    val hiddenComment = readAction {
      PsiTreeUtil.findChildOfType(psiManager.findFile(virtualFile, hidden), PsiComment::class.java)!!
    }
    val document = editor.document
    WriteCommandAction.runWriteCommandAction(project) {
      document.insertString(document.text.indexOf("comment") + "comment".length, "x")
    }
    withContext(Dispatchers.EDT) {
      PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    assertFalse(readAction { hiddenComment.isValid },
                "precondition: the edit must invalidate the comment in the hidden context")
  }

  @RequiresEdt
  private fun createEditor(document: Document): Editor {
    val editor = EditorFactory.getInstance().createEditor(document, project)
    Disposer.register(testRootDisposable) { EditorFactory.getInstance().releaseEditor(editor) }
    return editor
  }

  /** Reopens the file in the tab of a [FileEditorWithTextEditors] that embeds [embeddedEditors] */
  @RequiresEdt
  private fun openInEditorWithTextEditors(embeddedEditors: List<Editor>, onEmbeddedEditorsQueried: () -> Unit = {}) {
    val file = virtualFile
    val fileEditorManager = FileEditorManager.getInstance(project)
    fileEditorManager.closeFile(file)
    file.putUserData(FileEditorProvider.KEY, EditorWithTextEditorsProvider(file, embeddedEditors, onEmbeddedEditorsQueried))
    Disposer.register(testRootDisposable) { file.putUserData(FileEditorProvider.KEY, null) }
    fileEditorManager.openFile(file)
  }

  /** @return the live list of the editors that the daemon starts to highlight */
  private fun collectStartedEditors(): List<Editor> {
    val startedEditors = ContainerUtil.createConcurrentList<Editor>()
    project.messageBus.connect(testRootDisposable).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonCodeAnalyzer.DaemonListener {
      override fun daemonStarting(fileEditors: Collection<FileEditor>) {
        fileEditors.mapNotNullTo(startedEditors) { (it as? TextEditor)?.editor }
      }
    })
    return startedEditors
  }

  /** @return the "module2" context, then the context of the test module */
  private suspend fun configureCommentInTwoModules(): List<ModuleContext> {
    @Language("JAVA")
    val text = """
      // comment
    """
    fixture.configureByText(JavaFileType.INSTANCE, text)
    PsiTestUtil.addModule(project, ModuleType.EMPTY, "module2", readAction { module.rootManager.contentRoots[0] })
    val contexts = getContexts()
    assertThat(contexts).hasSize(2)
    assertEquals("module2", contexts[0].getModule()!!.name)
    return contexts
  }

  @RequiresReadLock
  private fun getAllDocumentHighlights(): List<HighlightInfo> {
    val allInfos = mutableListOf<HighlightInfo>()
    val document = editor.getDocument()
    DaemonCodeAnalyzerEx.processHighlights(document, project, null, 0, document.textLength,
                                           Processors.cancelableCollectProcessor(allInfos))
    allInfos.sortBy { it.description }
    allInfos.sortBy { it.startOffset }
    return allInfos
  }

  // Reproduces the root cause of the daemon error
  // "PsiFile's context does not match the context of the editor.
  //  File's context = LibraryContextImpl(...); Editor's context = DefaultContext"
  // (TextEditorHighlightingPassRegistrarImpl.instantiatePasses, added under IJPL-240162).
  //
  // EditorContextManagerImpl.getEditorContexts caches its result per editor and only refreshes it on
  // CodeInsightContextManager.contextsChanged. When the editor's document is momentarily not backed by
  // a VirtualFile (FileDocumentManager.getFile == null, e.g. before the document<->file mapping is
  // ready), it returns the DefaultContext fallback. If that fallback is cached, the editor stays pinned
  // to DefaultContext even after the real (library/module) context becomes available, while the
  // FileViewProvider for the same file is correctly inferred to its real context -> the contexts
  // diverge and highlighting logs the error.
  @Test
  fun testDefaultContextOfFilelessDocumentIsNotCached(): Unit = runTest {
    assertTrue(isSharedSourceSupportEnabled(project), "multiverse support must be enabled for this test")

    withContext(Dispatchers.EDT) {
      val factory = EditorFactory.getInstance()
      val document = factory.createDocument("class C {}")
      val fileLessEditor = factory.createEditor(document, project)
      try {
        assertNull(FileDocumentManager.getInstance().getFile(document),
                   "precondition: the document must not be backed by a VirtualFile")

        // The editor context resolves to the default fallback because the file is unknown...
        assertEquals(defaultContext(), EditorContextManager.getEditorContext(fileLessEditor, project))

        // ...but this fallback must NOT be cached: otherwise the editor stays pinned to DefaultContext
        // and later highlighting of a real-context file fails the editor-vs-file context check.
        assertNull(EditorContextManager.getCachedEditorContext(fileLessEditor, project),
                   "the default fallback for a file-less document must not be cached")
      }
      finally {
        factory.releaseEditor(fileLessEditor)
      }
    }
  }

  // IJPL-248901: ProjectModel contexts must be identified by their stable symbolic id
  // (ModuleId / LibraryId / SdkId), NOT by the EntityPointer. An EntityPointer's id encodes the
  // entity's slot in the current WorkspaceModel storage (arrayId in the high 32 bits), so the same
  // module resolved against two storage generations (e.g. after a full storage replace on the
  // remote-dev backend) yields pointers that are not equal. That made the editor's context and the
  // file's context for the same module compare unequal and triggered
  // "PsiFile's context does not match the context of the editor" with two ModuleContextImpl values
  // that differed only by EntityPointer id. Keying on the symbolic id makes them generation-stable.
  @Test
  fun testProjectModelContextsAreIdentifiedBySymbolicId() {
    val module = ModuleContextImpl(ModuleId("m"), project)
    val sameModule = ModuleContextImpl(ModuleId("m"), project)
    assertEquals(module, sameModule)
    assertEquals(module.hashCode(), sameModule.hashCode())
    assertFalse(module == ModuleContextImpl(ModuleId("other"), project), "different modules must not be equal")

    val tableId = LibraryTableId.ProjectLibraryTableId
    val library = LibraryContextImpl(LibraryId("lib", tableId), project)
    val sameLibrary = LibraryContextImpl(LibraryId("lib", tableId), project)
    assertEquals(library, sameLibrary)
    assertEquals(library.hashCode(), sameLibrary.hashCode())
    assertFalse(library == LibraryContextImpl(LibraryId("other", tableId), project), "different libraries must not be equal")

    val sdk = SdkContextImpl(SdkId("sdk", "JavaSDK"), project)
    val sameSdk = SdkContextImpl(SdkId("sdk", "JavaSDK"), project)
    assertEquals(sdk, sameSdk)
    assertEquals(sdk.hashCode(), sameSdk.hashCode())
    assertFalse(sdk == SdkContextImpl(SdkId("other", "JavaSDK"), project), "different SDKs must not be equal")
  }

  // A file's contexts come from the first provider, in registration order, that claims it.

  @Test
  fun testOwningProviderPreemptsPlatformContext(): Unit = runTest {
    fixture.configureByText(JavaFileType.INSTANCE, "class C {}")
    // Precondition: with no test provider, the file carries the platform module context.
    assertTrue(getRawContexts().any { it is ModuleContext }, getRawContexts().toString())

    val fake = FakeContext("hi")
    registerOwningProvider(virtualFile, Answer.Contexts(listOf(fake)))

    // The test provider owns the file, so the platform module context is fully suppressed for it.
    assertEquals(listOf<CodeInsightContext>(fake), getRawContexts())
  }

  @Test
  fun testUnclaimedProviderFallsThroughToNextOwner(): Unit = runTest {
    fixture.configureByText(JavaFileType.INSTANCE, "class C {}")
    registerOwningProvider(virtualFile, Answer.NotOwned)

    val contexts = getRawContexts()
    assertTrue(contexts.any { it is ModuleContext }, "must fall through to the platform module context")
    assertFalse(contexts.any { it is FakeContext }, "no fake context is contributed for this file")
  }

  @Test
  fun testDefaultContextAnswerKeepsTheFileAndShowsDefaultContext(): Unit = runTest {
    fixture.configureByText(JavaFileType.INSTANCE, "class C {}")
    registerOwningProvider(virtualFile, Answer.DefaultContext)

    val contexts = getRawContexts()
    assertEquals(listOf(defaultContext()), contexts)
    assertFalse(contexts.any { it is ModuleContext }, "an owned file must not fall through to the platform module context")
  }

  // getPreferredContext lets the owning provider nominate its default context, honored only if it is among the file's
  // contexts (else the platform falls back to the first).

  @Test
  fun testProviderPicksPreferredContextInsteadOfFirst(): Unit = runTest {
    fixture.configureByText(JavaFileType.INSTANCE, "class C {}")
    val first = FakeContext("first")
    val second = FakeContext("second")
    registerOwningProvider(virtualFile, Answer.Contexts(listOf(first, second)), preferred = second)
    assertEquals(second, getPreferredContext())
  }

  @Test
  fun testPreferredContextDefaultsToFirstWithoutNomination(): Unit = runTest {
    fixture.configureByText(JavaFileType.INSTANCE, "class C {}")
    val first = FakeContext("first")
    val second = FakeContext("second")
    registerOwningProvider(virtualFile, Answer.Contexts(listOf(first, second)))
    assertEquals(first, getPreferredContext())
  }

  @Test
  fun testPerFileDropKeepsTheEditorContextOfAnotherFile(): Unit = runTest {
    fixture.configureByText(JavaFileType.INSTANCE, "class C {}")
    val first = FakeContext("first")
    val second = FakeContext("second")
    val provider = registerOwningProvider(virtualFile, Answer.Contexts(listOf(first, second)), preferred = second)
    // The test provider does not claim it, so it carries the platform module context.
    val otherFile = createOtherJavaFile()
    withContext(Dispatchers.EDT) {
      val editorFactory = EditorFactory.getInstance()
      val otherEditor = editorFactory.createEditor(FileDocumentManager.getInstance().getDocument(otherFile)!!, project)
      try {
        val editorContextManager = EditorContextManager.getInstance(project)
        editorContextManager.getEditorContexts(editor)
        val otherContext = editorContextManager.getEditorContexts(otherEditor).mainContext
        val changes = recordPerFileChanges()

        val third = FakeContext("third")
        provider.answer = Answer.Contexts(listOf(first, second, third))
        CodeInsightContextManagerImpl.getInstanceImpl(project).requestInvalidationNow(setOf(virtualFile))

        assertEquals(listOf(setOf(virtualFile)), changes)
        assertEquals(listOf(first, second, third), CodeInsightContextManager.getInstance(project).getCodeInsightContexts(virtualFile))
        assertNull(editorContextManager.getCachedEditorContexts(editor), "the editor of the dropped file infers its context again")
        assertEquals(otherContext, editorContextManager.getCachedEditorContexts(otherEditor)?.mainContext)
      }
      finally {
        editorFactory.releaseEditor(otherEditor)
      }
    }
  }

  @Test
  fun testPerFileDropKeepsTheCachedAnswerOfAnotherFile(): Unit = runTest {
    fixture.configureByText(JavaFileType.INSTANCE, "class C {}")
    val provider = registerOwningProvider(virtualFile, Answer.Contexts(listOf(FakeContext("first"))))
    val otherFile = createOtherJavaFile()
    val otherFirst = FakeContext("other first")
    val otherProvider = registerOwningProvider(otherFile, Answer.Contexts(listOf(otherFirst)))

    withContext(Dispatchers.EDT) {
      val manager = CodeInsightContextManagerImpl.getInstanceImpl(project)
      val cachedContexts = manager.getCodeInsightContexts(virtualFile)
      assertEquals(listOf(otherFirst), manager.getCodeInsightContexts(otherFile))
      val changes = recordPerFileChanges()

      otherProvider.answer = Answer.Contexts(listOf(FakeContext("other second")))
      val second = FakeContext("second")
      provider.answer = Answer.Contexts(listOf(second))
      manager.requestInvalidationNow(setOf(virtualFile))
      Reference.reachabilityFence(cachedContexts)

      assertEquals(listOf(setOf(virtualFile)), changes)
      assertEquals(listOf(second), manager.getCodeInsightContexts(virtualFile))
      assertEquals(listOf(otherFirst), manager.getCodeInsightContexts(otherFile), "the other file keeps its cached answer")
    }
  }

  // A recheck builds the answer again and drops only a changed answer, so an unchanged one costs no write action.

  @Test
  fun testRecheckOfUnchangedAnswerKeepsTheEditorContext(): Unit = runTest {
    fixture.configureByText(JavaFileType.INSTANCE, "class C {}")
    val second = FakeContext("second")
    registerOwningProvider(virtualFile, Answer.Contexts(listOf(FakeContext("first"), second)), preferred = second)

    withContext(Dispatchers.EDT) {
      val contextManager = CodeInsightContextManagerImpl.getInstanceImpl(project)
      val cachedContexts = contextManager.getCodeInsightContexts(virtualFile)
      val editorContextManager = EditorContextManager.getInstance(project)
      assertEquals(second, editorContextManager.getEditorContexts(editor).mainContext)
      val changes = recordPerFileChanges()

      contextManager.requestInvalidationNow(setOf(virtualFile))
      Reference.reachabilityFence(cachedContexts)

      assertThat(changes).isEmpty()
      assertEquals(second, editorContextManager.getCachedEditorContexts(editor)?.mainContext)
    }
  }

  @Test
  fun testRecheckOfChangedPreferredContextDropsTheFile(): Unit = runTest {
    fixture.configureByText(JavaFileType.INSTANCE, "class C {}")
    val first = FakeContext("first")
    val second = FakeContext("second")
    val provider = registerOwningProvider(virtualFile, Answer.Contexts(listOf(first, second)), preferred = second)

    withContext(Dispatchers.EDT) {
      val contextManager = CodeInsightContextManagerImpl.getInstanceImpl(project)
      val cachedContexts = contextManager.getCodeInsightContexts(virtualFile)
      assertEquals(second, contextManager.getPreferredContext(virtualFile))
      val changes = recordPerFileChanges()

      provider.preferred = first
      contextManager.requestInvalidationNow(setOf(virtualFile))
      Reference.reachabilityFence(cachedContexts)

      assertEquals(listOf(setOf(virtualFile)), changes)
      assertEquals(first, contextManager.getPreferredContext(virtualFile))
    }
  }

  // The GC can clear a cached answer while the editor still uses its contexts, so the recheck cannot skip such a file.
  @Test
  fun testRecheckOfFileWithNoCachedAnswerDropsTheFile(): Unit = runTest {
    val file = LightVirtualFile("C.java")
    registerOwningProvider(file, Answer.Contexts(listOf(FakeContext("first"))))

    withContext(Dispatchers.EDT) {
      val changes = recordPerFileChanges()

      CodeInsightContextManagerImpl.getInstanceImpl(project).requestInvalidationNow(setOf(file))

      assertEquals(listOf(setOf(file)), changes)
    }
  }

  // A file with no cached answer counts as changed in a recheck, so the recheck shows that the call cached nothing.
  @Test
  fun testContextsWithoutCachingDoNotCacheTheAnswer(): Unit = runTest {
    val file = LightVirtualFile("C.java")
    val first = FakeContext("first")
    registerOwningProvider(file, Answer.Contexts(listOf(first)))

    withContext(Dispatchers.EDT) {
      val manager = CodeInsightContextManagerImpl.getInstanceImpl(project)
      assertEquals(listOf(first), manager.getCodeInsightContextsWithoutCaching(file))
      val changes = recordPerFileChanges()
      manager.requestInvalidationNow(setOf(file))

      assertEquals(listOf(setOf(file)), changes)
    }
  }

  @Test
  fun testContextsWithoutCachingReturnTheCachedAnswer(): Unit = runTest {
    fixture.configureByText(JavaFileType.INSTANCE, "class C {}")
    val first = FakeContext("first")
    val provider = registerOwningProvider(virtualFile, Answer.Contexts(listOf(first)))

    withContext(Dispatchers.EDT) {
      val manager = CodeInsightContextManagerImpl.getInstanceImpl(project)
      assertEquals(listOf(first), manager.getCodeInsightContexts(virtualFile))

      provider.answer = Answer.Contexts(listOf(FakeContext("second")))

      assertEquals(listOf(first), manager.getCodeInsightContextsWithoutCaching(virtualFile))
    }
  }

  private fun recordPerFileChanges(): List<Set<VirtualFile>> {
    val changes = ArrayList<Set<VirtualFile>>()
    project.messageBus.connect(testRootDisposable).subscribe(CodeInsightContextManager.topic, object : CodeInsightContextChangeListener {
      override fun contextsChanged() {}

      override fun contextsChanged(files: Set<VirtualFile>) {
        changes.add(files)
      }
    })
    return changes
  }

  private fun createOtherJavaFile(): VirtualFile = WriteAction.computeAndWait<VirtualFile, Throwable> {
    val file = virtualFile.parent.createChildData(this, "D.java")
    VfsUtil.saveText(file, "class D {}")
    file
  }

  private suspend fun getRawContexts(): List<CodeInsightContext> = readAction {
    CodeInsightContextManager.getInstance(project).getCodeInsightContexts(virtualFile)
  }

  private suspend fun getPreferredContext(): CodeInsightContext = readAction {
    CodeInsightContextManager.getInstance(project).getPreferredContext(virtualFile)
  }

  private suspend fun registerOwningProvider(
    targetFile: VirtualFile,
    answer: Answer,
    preferred: CodeInsightContext? = null,
    supportsSimultaneousRendering: Boolean = true,
  ): OwningTestContextProvider {
    val provider = OwningTestContextProvider(targetFile, answer, preferred, supportsSimultaneousRendering)
    withContext(Dispatchers.EDT) {
      CodeInsightContextManager.getInstance(project).registerTestOnlyCodeInsightContextProvider(provider, testRootDisposable)
    }
    return provider
  }

  private suspend fun getContexts(): List<ModuleContext> {
    val contexts = getRawContexts()
    assertTrue(contexts.all { it is ModuleContext }, contexts.toString())
    @Suppress("UNCHECKED_CAST")
    return (contexts as List<ModuleContext>)
      .sortedBy { it.getModule()!!.name }
  }
}

/**
 * A [DaemonAnalyzerFixture] whose main module has the name of the test method, as the main module of a JUnit 3 heavy test has.
 */
private class MultiverseDaemonAnalyzerFixture(context: Context) : DaemonAnalyzerFixture(context, "", LocalInspectionTool.EMPTY_ARRAY) {
  override fun createMainModule(): Module {
    val moduleFile = project.stateStore.projectBasePath.resolve("test" + getTestName(false) + ".iml")
    val module = WriteAction.computeAndWait<Module, RuntimeException> {
      ModuleManager.getInstance(project).newModule(moduleFile, JavaModuleType.getModuleType().id)
    }
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    return module
  }
}


private class EditorWithTextEditorsProvider(
  private val file: VirtualFile,
  private val embeddedEditors: List<Editor>,
  private val onEmbeddedEditorsQueried: () -> Unit,
) : FileEditorProvider, DumbAware {
  override fun accept(project: Project, file: VirtualFile): Boolean = file == this.file

  override fun createEditor(project: Project, file: VirtualFile): FileEditor =
    EditorWithTextEditors(file, embeddedEditors, onEmbeddedEditorsQueried)

  override fun getEditorTypeId(): String = "test-editor-with-text-editors"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

private class EditorWithTextEditors(
  private val file: VirtualFile,
  private val embeddedEditors: List<Editor>,
  private val onEmbeddedEditorsQueried: () -> Unit,
) : UserDataHolderBase(), FileEditorWithTextEditors {
  private val component = JPanel()

  override fun getEmbeddedEditors(): List<Editor> {
    onEmbeddedEditorsQueried()
    return embeddedEditors
  }

  override fun getFile(): VirtualFile = file

  override fun getComponent(): JComponent = component

  override fun getPreferredFocusedComponent(): JComponent = component

  override fun getName(): String = "EditorWithTextEditors"

  override fun setState(state: FileEditorState) {}

  override fun isModified(): Boolean = false

  override fun isValid(): Boolean = true

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

  override fun dispose() {}
}

private class FakeContext(private val name: String) : CodeInsightContext {
  override fun toString(): String = "FakeContext($name)"
}

// Gives [answer] for [targetFile] and nominates [preferred], claiming no other file. A test can change both.
private class OwningTestContextProvider(
  private val targetFile: VirtualFile,
  var answer: Answer,
  var preferred: CodeInsightContext? = null,
  private val supportsSimultaneousRendering: Boolean = true,
) : CodeInsightContextProvider {
  override fun isOwnerOf(context: CodeInsightContext): Boolean = context is FakeContext

  override fun supportsSimultaneousRendering(): Boolean = supportsSimultaneousRendering

  override fun getContexts(file: VirtualFile, project: Project): Answer =
    if (file == targetFile) answer else Answer.NotOwned

  override fun subscribeToChanges(project: Project, invalidator: CodeInsightContextProvider.Invalidator) {}

  override fun getPreferredContext(file: VirtualFile, project: Project, contexts: List<CodeInsightContext>): CodeInsightContext? =
    if (file == targetFile) preferred else null
}

private class FileLevelInspection : LocalInspectionTool(), DumbAware {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return object : PsiElementVisitor() {
      override fun visitFile(psiFile: PsiFile) {
        val text = getContextPresentation(psiFile)
        holder.registerProblem(psiFile, "file-level $text")
      }
    }
  }
}

private class CommentInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return object : PsiElementVisitor() {
      override fun visitComment(comment: PsiComment) {
        val text = getContextPresentation(comment.containingFile)
        holder.registerProblem(comment, "Comment warning $text")
      }
    }
  }
}

private fun getContextPresentation(psiFile: PsiFile): String {
  return when (val context = psiFile.codeInsightContext) {
    defaultContext() -> "default-context"
    anyContext() -> "any-context"
    is ModuleContext -> "module-context ${context.getModule()!!.name}"
    else -> "unknown-context $context"
  }
}
