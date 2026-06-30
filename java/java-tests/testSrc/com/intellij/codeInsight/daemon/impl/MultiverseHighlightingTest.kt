// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase.CanChangeDocumentDuringHighlighting
import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.CodeInsightContextManager
import com.intellij.codeInsight.multiverse.EditorContextManager
import com.intellij.codeInsight.multiverse.ModuleContext
import com.intellij.codeInsight.multiverse.SingleEditorContext
import com.intellij.codeInsight.multiverse.anyContext
import com.intellij.codeInsight.multiverse.codeInsightContext
import com.intellij.codeInsight.multiverse.defaultContext
import com.intellij.codeInsight.multiverse.isSharedSourceSupportEnabled
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.rootManager
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PsiTestUtil
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

  private fun getAllDocumentHighlights(): List<HighlightInfo> {
    val allInfos = mutableListOf<HighlightInfo>()
    val document = editor.getDocument()
    DaemonCodeAnalyzerEx.processHighlights(document, project, null, 0, document.textLength,
                                           Processors.cancelableCollectProcessor(allInfos))
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

  private fun getContexts(): List<ModuleContext> {
    val contexts = CodeInsightContextManager.getInstance(project).getCodeInsightContexts(virtualFile)
    assertTrue(contexts.toString(), contexts.all { it is ModuleContext })
    @Suppress("UNCHECKED_CAST")
    return (contexts as List<ModuleContext>)
      .sortedBy { it.getModule()!!.name }
  }
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
