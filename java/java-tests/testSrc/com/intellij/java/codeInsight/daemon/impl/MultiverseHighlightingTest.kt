// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase.CanChangeDocumentDuringHighlighting
import com.intellij.codeInsight.daemon.ProductionLightDaemonAnalyzerTestCase
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator
import com.intellij.codeInsight.daemon.impl.EditorTracker
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.CodeInsightContextChangeListener
import com.intellij.codeInsight.multiverse.CodeInsightContextManager
import com.intellij.codeInsight.multiverse.CodeInsightContextManagerImpl
import com.intellij.codeInsight.multiverse.CodeInsightContextProvider
import com.intellij.codeInsight.multiverse.CodeInsightContextProvider.Answer
import com.intellij.codeInsight.multiverse.EditorContextManager
import com.intellij.codeInsight.multiverse.ModuleContext
import com.intellij.codeInsight.multiverse.SingleEditorContext
import com.intellij.codeInsight.multiverse.anyContext
import com.intellij.codeInsight.multiverse.codeInsightContext
import com.intellij.codeInsight.multiverse.defaultContext
import com.intellij.codeInsight.multiverse.isSharedSourceSupportEnabled
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
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.createGlobalContextForTool
import com.intellij.testFramework.enableInspectionTools
import com.intellij.util.Processors
import com.intellij.util.ThrowableRunnable
import org.intellij.lang.annotations.Language

@CanChangeDocumentDuringHighlighting
class MultiverseHighlightingTest : DaemonAnalyzerTestCase() {
  private var context: CodeInsightContext? = null
    set(value) {
      field = value
      if (value != null) {
        if (myFile != null) {
          myFile = psiManager.findFile(virtualFile, value)
        }
        else {
          // using random context
          myFile = psiManager.findFile(virtualFile)
        }

        if (editor != null) {
          EditorContextManager.getInstance(project).setEditorContext(editor, SingleEditorContext(value))
        }
      }
    }

  private val virtualFile get() = myFile.virtualFile

  override fun setUp() {
    super.setUp()
    context = null
  }

  override fun tearDown() {
    try {
      context = null
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable?>) {
    DaemonProgressIndicator.runInDebugMode<Exception> {
      super.runTestRunnable(testRunnable)
    }
  }

  fun testLocalInspectionInSeveralContexts() {
    enableInspectionTools(project, testRootDisposable, FileLevelInspection(), CommentInspection())

    @Language("JAVA")
    val text = """
      // comment
    """
    configureByText(JavaFileType.INSTANCE, text)

    val root = module.rootManager.contentRoots[0]
    PsiTestUtil.addModule(project, ModuleType.EMPTY, "module2", root)

    val contexts = getContexts()
    assertSize(2, contexts)

    assertEquals("module2", contexts[0].getModule()!!.name)
    assertEquals("testLocalInspectionInSeveralContexts", contexts[1].getModule()!!.name)

    // highlighting in "module2" context
    this.context = contexts[0]
    val infos2 = doHighlighting()
    assertOrderedEquals(infos2.map { it.description },
                        "file-level module-context module2",
                        "Comment warning module-context module2",
    )

    val allInfosBefore = getAllDocumentHighlights()
    assertOrderedEquals(allInfosBefore.map { it.description },
                        "file-level module-context module2",
                        "Comment warning module-context module2",
    )

    // highlighting in "testLocalInspectionInSeveralContexts" context
    this.context = contexts[1]
    val infos1 = doHighlighting()
    assertOrderedEquals(infos1.map { it.description },
                        "file-level module-context testLocalInspectionInSeveralContexts",
                        "Comment warning module-context testLocalInspectionInSeveralContexts",
    )

    val allInfos = getAllDocumentHighlights()
    assertOrderedEquals(allInfos.map { it.description },
                        "file-level module-context module2",
                        "file-level module-context testLocalInspectionInSeveralContexts",
                        "Comment warning module-context module2",
                        "Comment warning module-context testLocalInspectionInSeveralContexts",
    )
  }

  fun testBatchInspectionInPreferredContextOnly() {
    val descriptions = runBatchCommentInspectionInTwoModules().problemElements.values.map { it.descriptionTemplate }
    assertEquals("the file must be inspected only in its preferred context: $descriptions", 1, descriptions.size)
  }

  fun testBatchInspectionInAllContexts() {
    Registry.get("batch.inspections.inspect.all.code.insight.contexts").setValue(true, testRootDisposable)
    val descriptions = runBatchCommentInspectionInTwoModules().problemElements.values.map { it.descriptionTemplate }
    assertSameElements("the file must be inspected in each of its contexts",
                       descriptions,
                       listOf("Comment warning module-context module2", "Comment warning module-context $name"))
  }

  fun testBatchInspectionInAllContextsReportsProblemsOnFileOfEachContext() {
    Registry.get("batch.inspections.inspect.all.code.insight.contexts").setValue(true, testRootDisposable)
    val refFiles = runBatchCommentInspectionInTwoModules().problemElements.keys()
    assertSameElements("each context must have its own reference element",
                       refFiles.map { (it as RefFile).psiElement.codeInsightContext },
                       getContexts())
  }

  private fun runBatchCommentInspectionInTwoModules(): DefaultInspectionToolResultExporter {
    @Language("JAVA")
    val text = """
      // comment
    """
    configureByText(JavaFileType.INSTANCE, text)

    val root = module.rootManager.contentRoots[0]
    PsiTestUtil.addModule(project, ModuleType.EMPTY, "module2", root)
    val contexts = getContexts()
    assertEquals("the file must have a context for each module: $contexts", 2, contexts.size)

    val toolWrapper = LocalInspectionToolWrapper(CommentInspection())
    val scope = AnalysisScope(project)
    val globalContext = createGlobalContextForTool(scope, project, listOf(toolWrapper))
    InspectionTestUtil.runTool(toolWrapper, scope, globalContext)
    return globalContext.getPresentation(toolWrapper) as DefaultInspectionToolResultExporter
  }

  // CPP-24696: a file restart dirties every context, but the daemon re-runs only the context of the editor
  fun testAnalysisFinishesAfterEditorSwitchesContext() {
    configureByText(JavaFileType.INSTANCE, "class C {}")
    PsiTestUtil.addModule(project, ModuleType.EMPTY, "module2", module.rootManager.contentRoots[0])
    val contexts = getContexts()
    assertSize(2, contexts)
    val (first, second) = contexts
    val fileStatusMap = myDaemonCodeAnalyzer.fileStatusMap

    ProductionLightDaemonAnalyzerTestCase.runTestInProduction(false, myDaemonCodeAnalyzer) {
      EditorTracker.getInstance(project).setActiveEditorsInTests(listOf(editor))
      context = first
      myTestDaemonCodeAnalyzer.waitForDaemonToFinish(myFile)
      assertTrue("precondition: analysis under the first context finishes", myDaemonCodeAnalyzer.isAllAnalysisFinished(myFile))

      context = second
      myDaemonCodeAnalyzer.restart(myFile, "test")
      assertFalse("the restart dirties the context the editor left", fileStatusMap.allDirtyScopesAreNullFor(editor.document, first))
      myTestDaemonCodeAnalyzer.waitForDaemonToFinish(myFile)
      assertFalse("nothing re-analyzes the context the editor left", fileStatusMap.allDirtyScopesAreNullFor(editor.document, first))
      assertTrue("analysis under the second context finishes", myDaemonCodeAnalyzer.isAllAnalysisFinished(myFile))
      val textEditor = TextEditorProvider.getInstance().getTextEditor(editor)
      assertTrue("the editor reports its highlighting as completed", DaemonCodeAnalyzerEx.isHighlightingCompleted(textEditor, project))
    }
  }

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
  fun testDefaultContextOfFilelessDocumentIsNotCached() {
    assertTrue("multiverse support must be enabled for this test", isSharedSourceSupportEnabled(project))

    val factory = EditorFactory.getInstance()
    val document = factory.createDocument("class C {}")
    val fileLessEditor = factory.createEditor(document, project)
    try {
      assertNull("precondition: the document must not be backed by a VirtualFile",
                 FileDocumentManager.getInstance().getFile(document))

      // The editor context resolves to the default fallback because the file is unknown...
      assertEquals(defaultContext(), EditorContextManager.getEditorContext(fileLessEditor, project))

      // ...but this fallback must NOT be cached: otherwise the editor stays pinned to DefaultContext
      // and later highlighting of a real-context file fails the editor-vs-file context check.
      assertNull("the default fallback for a file-less document must not be cached",
                 EditorContextManager.getCachedEditorContext(fileLessEditor, project))
    }
    finally {
      factory.releaseEditor(fileLessEditor)
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
  fun testProjectModelContextsAreIdentifiedBySymbolicId() {
    val module = ModuleContextImpl(ModuleId("m"), project)
    val sameModule = ModuleContextImpl(ModuleId("m"), project)
    assertEquals(module, sameModule)
    assertEquals(module.hashCode(), sameModule.hashCode())
    assertFalse("different modules must not be equal", module == ModuleContextImpl(ModuleId("other"), project))

    val tableId = LibraryTableId.ProjectLibraryTableId
    val library = LibraryContextImpl(LibraryId("lib", tableId), project)
    val sameLibrary = LibraryContextImpl(LibraryId("lib", tableId), project)
    assertEquals(library, sameLibrary)
    assertEquals(library.hashCode(), sameLibrary.hashCode())
    assertFalse("different libraries must not be equal", library == LibraryContextImpl(LibraryId("other", tableId), project))

    val sdk = SdkContextImpl(SdkId("sdk", "JavaSDK"), project)
    val sameSdk = SdkContextImpl(SdkId("sdk", "JavaSDK"), project)
    assertEquals(sdk, sameSdk)
    assertEquals(sdk.hashCode(), sameSdk.hashCode())
    assertFalse("different SDKs must not be equal", sdk == SdkContextImpl(SdkId("other", "JavaSDK"), project))
  }

  // A file's contexts come from the first provider, in registration order, that claims it.

  fun testOwningProviderPreemptsPlatformContext() {
    configureByText(JavaFileType.INSTANCE, "class C {}")
    // Precondition: with no test provider, the file carries the platform module context.
    assertTrue(getRawContexts().toString(), getRawContexts().any { it is ModuleContext })

    val fake = FakeContext("hi")
    registerOwningProvider(virtualFile, Answer.Contexts(listOf(fake)))

    // The test provider owns the file, so the platform module context is fully suppressed for it.
    assertEquals(listOf<CodeInsightContext>(fake), getRawContexts())
  }

  fun testUnclaimedProviderFallsThroughToNextOwner() {
    configureByText(JavaFileType.INSTANCE, "class C {}")
    registerOwningProvider(virtualFile, Answer.NotOwned)

    val contexts = getRawContexts()
    assertTrue("must fall through to the platform module context", contexts.any { it is ModuleContext })
    assertFalse("no fake context is contributed for this file", contexts.any { it is FakeContext })
  }

  fun testDefaultContextAnswerKeepsTheFileAndShowsDefaultContext() {
    configureByText(JavaFileType.INSTANCE, "class C {}")
    registerOwningProvider(virtualFile, Answer.DefaultContext)

    val contexts = getRawContexts()
    assertEquals(listOf(defaultContext()), contexts)
    assertFalse("an owned file must not fall through to the platform module context", contexts.any { it is ModuleContext })
  }

  // getPreferredContext lets the owning provider nominate its default context, honored only if it is among the file's
  // contexts (else the platform falls back to the first).

  fun testProviderPicksPreferredContextInsteadOfFirst() {
    configureByText(JavaFileType.INSTANCE, "class C {}")
    val first = FakeContext("first")
    val second = FakeContext("second")
    registerOwningProvider(virtualFile, Answer.Contexts(listOf(first, second)), preferred = second)
    assertEquals(second, getPreferredContext())
  }

  fun testPreferredContextDefaultsToFirstWithoutNomination() {
    configureByText(JavaFileType.INSTANCE, "class C {}")
    val first = FakeContext("first")
    val second = FakeContext("second")
    registerOwningProvider(virtualFile, Answer.Contexts(listOf(first, second)))
    assertEquals(first, getPreferredContext())
  }

  fun testPerFileDropKeepsTheEditorContextOfAnotherFile() {
    configureByText(JavaFileType.INSTANCE, "class C {}")
    val first = FakeContext("first")
    val second = FakeContext("second")
    val provider = registerOwningProvider(virtualFile, Answer.Contexts(listOf(first, second)), preferred = second)
    // The test provider does not claim it, so it carries the platform module context.
    val otherFile = createOtherJavaFile()
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
      assertEquals(listOf(first, second, third), getRawContexts())
      assertNull("the editor of the dropped file infers its context again", editorContextManager.getCachedEditorContexts(editor))
      assertEquals(otherContext, editorContextManager.getCachedEditorContexts(otherEditor)?.mainContext)
    }
    finally {
      editorFactory.releaseEditor(otherEditor)
    }
  }

  fun testPerFileDropKeepsTheCachedAnswerOfAnotherFile() {
    configureByText(JavaFileType.INSTANCE, "class C {}")
    val provider = registerOwningProvider(virtualFile, Answer.Contexts(listOf(FakeContext("first"))))
    val otherFile = createOtherJavaFile()
    val otherFirst = FakeContext("other first")
    val otherProvider = registerOwningProvider(otherFile, Answer.Contexts(listOf(otherFirst)))
    val manager = CodeInsightContextManager.getInstance(project)
    getRawContexts()
    assertEquals(listOf(otherFirst), manager.getCodeInsightContexts(otherFile))

    // The request names only the first file, so only a dropped answer can show this.
    otherProvider.answer = Answer.Contexts(listOf(FakeContext("other second")))
    val second = FakeContext("second")
    provider.answer = Answer.Contexts(listOf(second))
    CodeInsightContextManagerImpl.getInstanceImpl(project).requestInvalidationNow(setOf(virtualFile))

    assertEquals(listOf(second), getRawContexts())
    assertEquals("the other file keeps its cached answer", listOf(otherFirst), manager.getCodeInsightContexts(otherFile))
  }

  // A recheck builds the answer again and drops only a changed answer, so an unchanged one costs no write action.

  fun testRecheckOfUnchangedAnswerKeepsTheEditorContext() {
    configureByText(JavaFileType.INSTANCE, "class C {}")
    val second = FakeContext("second")
    registerOwningProvider(virtualFile, Answer.Contexts(listOf(FakeContext("first"), second)), preferred = second)
    val editorContextManager = EditorContextManager.getInstance(project)
    assertEquals(second, editorContextManager.getEditorContexts(editor).mainContext)
    val changes = recordPerFileChanges()

    CodeInsightContextManagerImpl.getInstanceImpl(project).requestInvalidationNow(setOf(virtualFile))

    assertEmpty(changes)
    assertEquals(second, editorContextManager.getCachedEditorContexts(editor)?.mainContext)
  }

  fun testRecheckOfChangedPreferredContextDropsTheFile() {
    configureByText(JavaFileType.INSTANCE, "class C {}")
    val first = FakeContext("first")
    val second = FakeContext("second")
    val provider = registerOwningProvider(virtualFile, Answer.Contexts(listOf(first, second)), preferred = second)
    assertEquals(second, getPreferredContext())
    val changes = recordPerFileChanges()

    provider.preferred = first
    CodeInsightContextManagerImpl.getInstanceImpl(project).requestInvalidationNow(setOf(virtualFile))

    assertEquals(listOf(setOf(virtualFile)), changes)
    assertEquals(first, getPreferredContext())
  }

  // The GC can clear a cached answer while the editor still uses its contexts, so the recheck cannot skip such a file.
  fun testRecheckOfFileWithNoCachedAnswerDropsTheFile() {
    configureByText(JavaFileType.INSTANCE, "class C {}")
    registerOwningProvider(virtualFile, Answer.Contexts(listOf(FakeContext("first"))))
    val changes = recordPerFileChanges()

    CodeInsightContextManagerImpl.getInstanceImpl(project).requestInvalidationNow(setOf(virtualFile))

    assertEquals(listOf(setOf(virtualFile)), changes)
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

  private fun createOtherJavaFile(): VirtualFile = runWriteAction {
    val file = virtualFile.parent.createChildData(this, "D.java")
    VfsUtil.saveText(file, "class D {}")
    file
  }

  private fun getRawContexts(): List<CodeInsightContext> =
    CodeInsightContextManager.getInstance(project).getCodeInsightContexts(virtualFile)

  private fun getPreferredContext(): CodeInsightContext =
    CodeInsightContextManager.getInstance(project).getPreferredContext(virtualFile)

  private fun registerOwningProvider(
    targetFile: VirtualFile,
    answer: Answer,
    preferred: CodeInsightContext? = null,
  ): OwningTestContextProvider {
    val provider = OwningTestContextProvider(targetFile, answer, preferred)
    CodeInsightContextManager.getInstance(project).registerTestOnlyCodeInsightContextProvider(provider, testRootDisposable)
    return provider
  }

  private fun getContexts(): List<ModuleContext> {
    val contexts = CodeInsightContextManager.getInstance(project).getCodeInsightContexts(virtualFile)
    assertTrue(contexts.toString(), contexts.all { it is ModuleContext })
    @Suppress("UNCHECKED_CAST")
    return (contexts as List<ModuleContext>)
      .sortedBy { it.getModule()!!.name }
  }
}

private class FakeContext(private val name: String) : CodeInsightContext {
  override fun toString(): String = "FakeContext($name)"
}

// Gives [answer] for [targetFile] and nominates [preferred], claiming no other file. A test can change both.
private class OwningTestContextProvider(
  private val targetFile: VirtualFile,
  var answer: Answer,
  var preferred: CodeInsightContext? = null,
) : CodeInsightContextProvider {
  override fun isOwnerOf(context: CodeInsightContext): Boolean = context is FakeContext

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
