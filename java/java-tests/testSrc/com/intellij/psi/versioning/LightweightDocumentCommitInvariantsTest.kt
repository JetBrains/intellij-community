// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.versioning

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.impl.DocumentCommitProcessor
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.psi.impl.PsiDocumentManagerEx
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiVersioningService
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.util.IncorrectOperationException
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
internal class LightweightDocumentCommitInvariantsTest {

  private companion object {
    private val BASE_TEXT = """
      class Small {
        void existing() {
        }
      }
    """.trimIndent()

    private val UPDATED_TEXT = """
      class Small {
        void existing() {
          int updated = 1;
        }

        void added() {
        }
      }
    """.trimIndent()
  }

  private val _project = projectFixture(openAfterCreation = true)
  private val _module = _project.moduleFixture("src")
  private val _sourceRoot = _module.sourceRootFixture()
  private val _psiFile = _sourceRoot.psiFileFixture("Small.java", "class Small {}")
  private val _editor = _psiFile.editorFixture()
  private val _otherPsiFile = _sourceRoot.psiFileFixture("Other.java", "class Other {}")
  private val _otherEditor = _otherPsiFile.editorFixture()

  private val project by _project
  private val editor by _editor
  private val otherEditor by _otherEditor

  @BeforeEach
  fun awaitIndexing() {
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  /**
   * AI-generated test.
   */
  @Test
  fun `lightweight commit keeps live document and published psi intact`() = runVersionedTest {
    val document = editor.document
    resetToBaseText(document)
    val documentManager = PsiDocumentManager.getInstance(project)

    val lightweightText = withContext(Dispatchers.UiWithModelAccess) {
      WriteCommandAction.runWriteCommandAction(project) {
        document.setText(UPDATED_TEXT)
      }
      documentManager.allowIsolatedCommits(document) {
        ThreadingAssertions.assertNoReadAccess()
        Assertions.assertFalse(ApplicationManager.getApplication().isWriteAccessAllowed)

        documentManager.commitDocument(document)
        currentPsiFile(document).text
      }
    }

    Assertions.assertEquals(UPDATED_TEXT, lightweightText, "Lightweight committed PSI must correspond to the updated document")
    Assertions.assertEquals(UPDATED_TEXT, document.text, "Lightweight commit must not change the live document")
    Assertions.assertFalse(
      documentManager.isCommitted(document),
      "Lightweight commit should not mark the live document committed after inspecting versioned PSI",
    )
    Assertions.assertEquals(BASE_TEXT, documentManager.getLastCommittedText(document).toString())
    Assertions.assertEquals(BASE_TEXT, readAction { currentPsiFile(document).text })
  }

  /**
   * AI-generated test.
   *
   * `doCommitLightweight` must set the commit flag before it calls the document commit processor.
   *
   * ```
   * replace the processor with one that checks isCommitInProgress
   * modify the document in a versioned environment
   * commitDocument(document)
   * assert the processor sees the commit flag
   * ```
   */
  @Test
  fun `lightweight commit is in progress while it prepares the commit`() = runVersionedTest {
    val document = editor.document
    resetToBaseText(document)
    val documentManager = PsiDocumentManager.getInstance(project) as PsiDocumentManagerBase
    val processorField = PsiDocumentManagerBase::class.java.getDeclaredField("myDocumentCommitProcessor")
    processorField.isAccessible = true
    val originalProcessor = processorField.get(documentManager) as DocumentCommitProcessor
    processorField.set(documentManager, object : DocumentCommitProcessor {
      override fun commitSynchronously(document: Document, project: Project, psiFile: PsiFile): Unit = error("Unexpected synchronous commit")

      override fun commitAsynchronously(
        project: Project,
        documentManager: PsiDocumentManagerEx,
        document: Document,
        reason: Any,
        modality: ModalityState,
      ): Unit = error("Unexpected asynchronous commit")

      override fun commitSynchronouslyLightweight(document: Document, project: Project) {
        Assertions.assertTrue(documentManager.isCommitInProgress,
                              "Lightweight commit must be in progress before it prepares the commit")
      }
    })

    try {
      withContext(Dispatchers.UiWithModelAccess) {
        WriteCommandAction.runWriteCommandAction(project) {
          document.setText(UPDATED_TEXT)
        }
        documentManager.allowIsolatedCommits(document) {
          documentManager.commitDocument(document)
        }
      }
    }
    finally {
      processorField.set(documentManager, originalProcessor)
    }
  }

  /**
   * AI-generated test.
   */
  @Test
  fun `lightweight commit does not publish document commit side effects`(@TestDisposable disposable: Disposable) = runVersionedTest {
    val document = editor.document
    resetToBaseText(document)
    val documentManager = PsiDocumentManager.getInstance(project)
    val eventCount = AtomicInteger()
    val afterCommitCallback = AtomicBoolean()
    val listener = object : PsiTreeChangeAdapter() {
      override fun childAdded(event: PsiTreeChangeEvent) {
        eventCount.incrementAndGet()
      }

      override fun childRemoved(event: PsiTreeChangeEvent) {
        eventCount.incrementAndGet()
      }

      override fun childReplaced(event: PsiTreeChangeEvent) {
        eventCount.incrementAndGet()
      }

      override fun childrenChanged(event: PsiTreeChangeEvent) {
        eventCount.incrementAndGet()
      }

      override fun propertyChanged(event: PsiTreeChangeEvent) {
        eventCount.incrementAndGet()
      }
    }
    PsiManager.getInstance(project).addPsiTreeChangeListener(listener, disposable)

    withContext(Dispatchers.UiWithModelAccess) {
      WriteCommandAction.runWriteCommandAction(project) {
        document.setText(UPDATED_TEXT)
      }
      documentManager.allowIsolatedCommits(document) {
        documentManager.performForCommittedDocument(document) {
          afterCommitCallback.set(true)
        }
        documentManager.commitDocument(document)
      }
    }

    Assertions.assertEquals(0, eventCount.get(), "Lightweight commit must not fire PSI tree change events")
    Assertions.assertFalse(afterCommitCallback.get(), "Lightweight commit must not run after-commit callbacks")
    Assertions.assertFalse(documentManager.isCommitted(document), "Lightweight commit must leave the live document uncommitted")
    Assertions.assertEquals(BASE_TEXT, documentManager.getLastCommittedText(document).toString())

    writeCommandAction(project, "") {
      documentManager.commitDocument(document)
    }
    withContext(Dispatchers.UiWithModelAccess) {
      IdeEventQueue.getInstance().flushQueue()
    }
    Assertions.assertTrue(afterCommitCallback.get(), "Normal commit should run the callback scheduled before lightweight commit")
  }

  /**
   * AI-generated test.
   *
   * Smart pointers are not versioned, so a lightweight commit must leave every pointer of the main timeline alone. The
   * commit must not run `updatePointers`, which shifts a range by the pending events, and must not run
   * `updatePointerTargetsAfterReparse`, which re-anchors a pointer that has a cached element.
   *
   * ```
   * pointer = smartPointer(method "existing")   // resolve it, so it has a cached element
   * document.setText(UPDATED_TEXT)              // no commit
   * remember pointer.range and pointer.element.text
   * allowIsolatedCommits(document) { commitDocument(document) }   // lightweight
   * assert the range and the element text did not change
   * commitDocument(document)                    // normal, so it may move the pointer
   * assert the element now holds the new text   // proves the assertions above can observe a move
   * ```
   */
  @Test
  fun `lightweight commit does not relocate smart pointers`() = runVersionedTest {
    val document = editor.document
    resetToBaseText(document)
    val documentManager = PsiDocumentManager.getInstance(project)

    // A pointer with a resolved element is the case that `updatePointerTargetsAfterReparse` re-anchors.
    val pointer = readAction {
      val method = PsiTreeUtil.findChildrenOfType(currentPsiFile(document), PsiMethod::class.java).single { it.name == "existing" }
      SmartPointerManager.getInstance(project).createSmartPsiElementPointer(method)
    }
    readAction {
      Assertions.assertNotNull(pointer.element, "The pointer must resolve before the document changes")
    }

    withContext(Dispatchers.UiWithModelAccess) {
      WriteCommandAction.runWriteCommandAction(project) {
        document.setText(UPDATED_TEXT)
      }
    }

    // Take the state after the document change, so the assertions below measure the lightweight commit alone.
    val rangeBeforeCommit = readAction { pointer.range?.let(TextRange::create) }
    val textBeforeCommit = readAction { pointer.element?.text }
    Assertions.assertNotNull(textBeforeCommit, "The pointer must still resolve while the change waits for a commit")

    withContext(Dispatchers.UiWithModelAccess) {
      documentManager.allowIsolatedCommits(document) {
        documentManager.commitDocument(document)
      }
    }

    // Smart pointers are not versioned, so a lightweight commit must leave every pointer of the main timeline alone.
    Assertions.assertEquals(rangeBeforeCommit, readAction { pointer.range?.let(TextRange::create) },
                            "Lightweight commit must not move the range of a smart pointer")
    Assertions.assertEquals(textBeforeCommit, readAction { pointer.element?.text },
                            "Lightweight commit must not re-anchor a smart pointer to the forked tree")

    // A normal commit publishes the reparse, so it moves the pointer to the new text. This shows that the assertions
    // above can observe a relocation.
    writeCommandAction(project, "") {
      documentManager.commitDocument(document)
    }
    readAction {
      val element = pointer.element
      Assertions.assertNotNull(element, "The pointer must survive the normal commit")
      Assertions.assertTrue(element!!.text.contains("int updated = 1;"),
                            "A normal commit must anchor the pointer to the reparsed element")
    }
  }

  /**
   * AI-generated test.
   *
   * The scenario that the separate state holder exists for. A forked timeline keeps its own last committed text, so a
   * later computation in the same timeline reparses from that text and not from the published one.
   *
   * ```
   * timeline = forkTimeline()                  // published text is T0
   * document.setText(T1)
   * withTimeline(timeline) {
   *   assert lastCommittedText == T0           // the fork starts from the published text
   *   commitDocument(document)                 // the forked PSI now matches T1
   * }
   * document.setText(T2)                       // write action, no commit
   * writeAction { }                            // another one, so the main counter moves again
   * withTimeline(timeline) {
   *   assert lastCommittedText == T1           // not T0
   *   assert !isCommitted(document)
   *   commitDocument(document)
   *   assert the forked PSI matches T2
   * }
   * assert the main timeline still sees T0
   * ```
   */
  @Test
  fun `a re-entered forked timeline reparses from its own last commit`() = runVersionedTest {
    val document = editor.document
    resetToBaseText(document)
    val documentManager = PsiDocumentManager.getInstance(project)
    val firstText = UPDATED_TEXT
    val secondText = UPDATED_TEXT.replace("int updated = 1;", "int updated = 1;\n      int second = 2;")

    val timeline = PsiVersioningService.forkTimeline()
    try {
      withContext(Dispatchers.UiWithModelAccess) {
        WriteCommandAction.runWriteCommandAction(project) {
          document.setText(firstText)
        }
        PsiVersioningService.executeWithTimeline(timeline) {
          Assertions.assertEquals(BASE_TEXT, documentManager.getLastCommittedText(document).toString(),
                                  "Before its first commit the fork reparses from the published text")
          documentManager.commitDocument(document)
          Assertions.assertEquals(firstText, currentPsiFile(document).text)
        }

        // The main timeline advances in each write action, and neither of them commits the document.
        WriteCommandAction.runWriteCommandAction(project) {
          document.setText(secondText)
        }
        WriteCommandAction.runWriteCommandAction(project) {
          // an unrelated write action, so the version counter moves again
        }

        PsiVersioningService.executeWithTimeline(timeline) {
          Assertions.assertEquals(firstText, documentManager.getLastCommittedText(document).toString(),
                                  "A re-entered fork must reparse from the text of its own last commit")
          Assertions.assertFalse(documentManager.isCommitted(document),
                                 "The document changed after the forked commit, so the fork must see it as uncommitted")

          documentManager.commitDocument(document)
          Assertions.assertEquals(secondText, currentPsiFile(document).text)
        }
      }
    }
    finally {
      PsiVersioningService.forgetForkedTimeline(timeline)
    }

    // The main timeline never published anything, so it still sees the base text.
    Assertions.assertFalse(documentManager.isCommitted(document))
    Assertions.assertEquals(BASE_TEXT, documentManager.getLastCommittedText(document).toString())
    Assertions.assertEquals(BASE_TEXT, readAction { currentPsiFile(document).text })
  }

  /**
   * AI-generated test.
   *
   * The main version counter advances in every write action, but the pending state of a document must not. It ends only
   * when the document is published.
   *
   * ```
   * writeAction { document.setText(UPDATED_TEXT) }   // the counter moves
   * writeAction { }                                  // it moves again, still no commit
   * assert lastCommittedText == BASE_TEXT
   * assert eventsSinceCommit is not empty
   * writeAction { commitDocument(document) }
   * assert the published PSI matches UPDATED_TEXT     // the commit still reparses
   * ```
   */
  @Test
  fun `the pending state spans write actions that do not commit`() = runVersionedTest {
    val document = editor.document
    resetToBaseText(document)
    val documentManager = PsiDocumentManager.getInstance(project) as PsiDocumentManagerBase

    writeCommandAction(project, "") {
      document.setText(UPDATED_TEXT)
    }
    writeCommandAction(project, "") {
      // an unrelated write action, so the main version counter moves without a commit
    }

    Assertions.assertEquals(BASE_TEXT, documentManager.getLastCommittedText(document).toString(),
                            "The pending state must survive a write action that does not commit")
    Assertions.assertFalse(documentManager.getEventsSinceCommit(document).isEmpty(),
                           "The accumulated document events must survive too")

    writeCommandAction(project, "") {
      documentManager.commitDocument(document)
    }
    Assertions.assertEquals(UPDATED_TEXT, readAction { currentPsiFile(document).text },
                            "The commit must still reparse after the counter moved")
  }

  /**
   * AI-generated test.
   *
   * A publish retires the event log that a forked watermark points into, so a publish must drop the baseline of every
   * forked timeline of that document.
   *
   * ```
   * timeline = forkTimeline()
   * document.setText(UPDATED_TEXT)
   * withTimeline(timeline) { commitDocument(document) }   // the fork records a baseline
   * writeAction { commitDocument(document) }              // the publish must flush it
   * withTimeline(timeline) {
   *   assert lastCommittedText == UPDATED_TEXT            // the published text, not a stale forked one
   *   assert isCommitted(document)
   * }
   * ```
   */
  @Test
  fun `a real commit flushes the state of every forked timeline`() = runVersionedTest {
    val document = editor.document
    resetToBaseText(document)
    val documentManager = PsiDocumentManager.getInstance(project)

    val timeline = PsiVersioningService.forkTimeline()
    try {
      withContext(Dispatchers.UiWithModelAccess) {
        WriteCommandAction.runWriteCommandAction(project) {
          document.setText(UPDATED_TEXT)
        }
        PsiVersioningService.executeWithTimeline(timeline) {
          documentManager.commitDocument(document)
        }
      }

      writeCommandAction(project, "") {
        documentManager.commitDocument(document)
      }
      Assertions.assertEquals(UPDATED_TEXT, readAction { currentPsiFile(document).text })

      withContext(Dispatchers.UiWithModelAccess) {
        PsiVersioningService.executeWithTimeline(timeline) {
          // The publish retired the event log the forked baseline pointed into, so the fork falls back to the published
          // text instead of using a baseline that no longer relates to it.
          Assertions.assertEquals(UPDATED_TEXT, documentManager.getLastCommittedText(document).toString())
          Assertions.assertTrue(documentManager.isCommitted(document))
        }
      }
    }
    finally {
      PsiVersioningService.forgetForkedTimeline(timeline)
    }
  }

  /**
   * AI-generated test.
   *
   * A forked baseline belongs to one exclusive version, so a commit in one timeline must not commit another one.
   *
   * Note that `doForkTimeline` derives the forked version from the current main version. Two forks taken with no write
   * action between them are therefore one and the same version, so the test separates them with a write action.
   *
   * ```
   * writeAction { document.setText(UPDATED_TEXT) }
   * first = forkTimeline()
   * writeAction { }                       // so the next fork lands on another version
   * second = forkTimeline()
   * withTimeline(first) { commitDocument(document); assert isCommitted(document) }
   * withTimeline(second) {
   *   assert !isCommitted(document)
   *   assert lastCommittedText == BASE_TEXT
   * }
   * ```
   */
  @Test
  fun `two forked timelines do not observe each other`() = runVersionedTest {
    val document = editor.document
    resetToBaseText(document)
    val documentManager = PsiDocumentManager.getInstance(project)

    // `doForkTimeline` derives the forked version from the current main version, so two forks taken with no write action
    // between them are one and the same version. Separate them with a write action to get two distinct timelines.
    writeCommandAction(project, "") {
      document.setText(UPDATED_TEXT)
    }
    val first = PsiVersioningService.forkTimeline()
    writeCommandAction(project, "") {
      // an unrelated write action, so the next fork lands on another version
    }
    val second = PsiVersioningService.forkTimeline()
    try {
      withContext(Dispatchers.UiWithModelAccess) {
        PsiVersioningService.executeWithTimeline(first) {
          documentManager.commitDocument(document)
          Assertions.assertTrue(documentManager.isCommitted(document))
        }
        PsiVersioningService.executeWithTimeline(second) {
          Assertions.assertFalse(documentManager.isCommitted(document),
                                 "A commit in one forked timeline must not commit another one")
          Assertions.assertEquals(BASE_TEXT, documentManager.getLastCommittedText(document).toString())
        }
      }
    }
    finally {
      PsiVersioningService.forgetForkedTimeline(second)
      PsiVersioningService.forgetForkedTimeline(first)
    }
  }

  /**
   * AI-generated test.
   *
   * The answer to "does this document wait for a commit?" belongs to one document, not to the whole timeline. A single
   * flag for every document reported one answer for all of them.
   *
   * ```
   * writeAction { first.setText(...); second.setText(...) }
   * allowIsolatedCommits(first) {
   *   commitDocument(first)
   *   assert isCommitted(first)
   *   assert !isCommitted(second)          // the commit of `first` must not cover `second`
   *   commitDocument(second)
   *   assert the PSI of `second` matches its document
   * }
   * ```
   */
  @Test
  fun `a lightweight commit of one document leaves the other one uncommitted`() = runVersionedTest {
    val document = editor.document
    val otherDocument = otherEditor.document
    resetToBaseText(document)
    val documentManager = PsiDocumentManager.getInstance(project)

    withContext(Dispatchers.UiWithModelAccess) {
      WriteCommandAction.runWriteCommandAction(project) {
        document.setText(UPDATED_TEXT)
        otherDocument.setText("class Other { void added() {} }")
      }
      // The API asks a block to use one document. This test needs both documents on one timeline, because the
      // invariant is that the pending flag belongs to a document and not to the timeline.
      documentManager.allowIsolatedCommits(document) {
        documentManager.commitDocument(document)
        Assertions.assertTrue(documentManager.isCommitted(document))
        Assertions.assertFalse(documentManager.isCommitted(otherDocument),
                               "A commit of one document must not mark another document committed")
        documentManager.commitDocument(otherDocument)
        Assertions.assertEquals(otherDocument.immutableCharSequence.toString(), currentPsiFile(otherDocument).text)
      }
    }
  }

  /**
   * AI-generated test.
   *
   * Both `performWhenAllCommitted` methods need the main timeline queue of uncommitted documents, and they schedule a
   * commit that publishes to the main timeline. A versioned computation must not use either.
   *
   * ```
   * allowIsolatedCommits(document) {
   *   assert performWhenAllCommitted throws IncorrectOperationException
   *   assert performLaterWhenAllCommitted throws IncorrectOperationException
   * }
   * freezePsiVersion {
   *   assert performWhenAllCommitted throws IncorrectOperationException
   * }
   * ```
   */
  @Test
  fun `performWhenAllCommitted is prohibited in a versioned environment`() = runVersionedTest {
    withContext(Dispatchers.UiWithModelAccess) {
      val document = editor.document
      val documentManager = PsiDocumentManager.getInstance(project)
      documentManager.allowIsolatedCommits(document) {
        Assertions.assertThrows(IncorrectOperationException::class.java) {
          documentManager.performWhenAllCommitted { }
        }
        Assertions.assertThrows(IncorrectOperationException::class.java) {
          documentManager.performLaterWhenAllCommitted { }
        }
      }
      PsiVersioningService.freezePsiVersion {
        Assertions.assertThrows(IncorrectOperationException::class.java) {
          documentManager.performWhenAllCommitted { }
        }
      }
    }
  }

  /**
   * AI-generated test
   *
   * A publish must end the isolated timeline of the document.
   *
   * The forked version stays frozen as long as the timeline lives, and a frozen version holds the cleanup barrier of
   * the whole application. So a publish that keeps the timeline retains every version after it.
   *
   * ```
   * allowIsolatedCommits(document) { commitDocument(document) }
   * assert one more forked version is frozen      // the timeline outlives the block
   * writeAction { commitDocument(document) }      // a publish
   * assert the frozen forked versions are back to the initial set
   * ```
   */
  @Test
  fun `a publish releases the isolated timeline of the document`() = runVersionedTest {
    val document = editor.document
    resetToBaseText(document)
    val documentManager = PsiDocumentManager.getInstance(project)
    val before = frozenForkedVersions()

    withContext(Dispatchers.UiWithModelAccess) {
      WriteCommandAction.runWriteCommandAction(project) {
        document.setText(UPDATED_TEXT)
      }
      val isolatedText = documentManager.allowIsolatedCommits(document) {
        ThreadingAssertions.assertNoReadAccess()
        documentManager.commitDocument(document)
        currentPsiFile(document).text
      }
      Assertions.assertEquals(UPDATED_TEXT, isolatedText, "An isolated commit must produce the PSI of the updated document")
    }

    Assertions.assertEquals(before.size + 1, frozenForkedVersions().size,
                            "The isolated timeline must outlive the block, so the next call reuses it")

    writeCommandAction(project, "") {
      documentManager.commitDocument(document)
    }

    Assertions.assertEquals(before, frozenForkedVersions(),
                            "A publish must release the isolated timeline of the document")
  }

  /**
   * AI-generated test
   *
   * A block that commits nothing must end the isolated timeline of the document.
   *
   * The document has no pending change here, so no lightweight commit records a forked baseline. A later call reads the
   * published text anyway, so the fork buys nothing. It only holds the cleanup barrier of the whole application.
   *
   * ```
   * // the document has no pending change
   * allowIsolatedCommits(document) { }
   * assert the frozen forked versions are back to the initial set
   * ```
   */
  @Test
  fun `a block that commits nothing releases the isolated timeline`() = runVersionedTest {
    val document = editor.document
    resetToBaseText(document)
    val documentManager = PsiDocumentManager.getInstance(project)
    val before = frozenForkedVersions()

    withContext(Dispatchers.UiWithModelAccess) {
      val isolatedText = documentManager.allowIsolatedCommits(document) {
        ThreadingAssertions.assertNoReadAccess()
        Assertions.assertTrue(documentManager.isCommitted(document), "The document must wait for no commit here")
        currentPsiFile(document).text
      }
      Assertions.assertEquals(BASE_TEXT, isolatedText)
    }

    Assertions.assertEquals(before, frozenForkedVersions(),
                            "A block that records no forked baseline must release its isolated timeline")
  }

  /**
   * AI-generated test
   *
   * A second call for the same document must reuse the timeline of the first one, so it reparses from its own last
   * commit instead of the published text.
   *
   * ```
   * document.setText(T1)
   * allowIsolatedCommits(document) { commitDocument(document) }    // the isolated PSI matches T1
   * document.setText(T2); writeAction { }                          // the main counter moves, nothing is published
   * allowIsolatedCommits(document) {
   *   assert lastCommittedText == T1                               // not the published text
   *   commitDocument(document)                                     // the isolated PSI matches T2
   * }
   * assert the main timeline still sees the base text
   * ```
   */
  @Test
  fun `a second isolated commit reuses the timeline of its document`() = runVersionedTest {
    val document = editor.document
    resetToBaseText(document)
    val documentManager = PsiDocumentManager.getInstance(project)
    val firstText = UPDATED_TEXT
    val secondText = UPDATED_TEXT.replace("int updated = 1;", "int updated = 1;\n      int second = 2;")

    withContext(Dispatchers.UiWithModelAccess) {
      WriteCommandAction.runWriteCommandAction(project) {
        document.setText(firstText)
      }
      documentManager.allowIsolatedCommits(document) {
        documentManager.commitDocument(document)
        Assertions.assertEquals(firstText, currentPsiFile(document).text)
      }

      // The main timeline advances, and neither write action commits the document.
      WriteCommandAction.runWriteCommandAction(project) {
        document.setText(secondText)
      }
      WriteCommandAction.runWriteCommandAction(project) {
        // an unrelated write action, so the version counter moves again
      }

      documentManager.allowIsolatedCommits(document) {
        Assertions.assertEquals(firstText, documentManager.getLastCommittedText(document).toString(),
                                "A second call must reuse the timeline, so it reparses from its own last commit")
        documentManager.commitDocument(document)
        Assertions.assertEquals(secondText, currentPsiFile(document).text)
      }
    }

    Assertions.assertFalse(documentManager.isCommitted(document), "An isolated commit must not publish the document")
    Assertions.assertEquals(BASE_TEXT, documentManager.getLastCommittedText(document).toString())
    Assertions.assertEquals(BASE_TEXT, readAction { currentPsiFile(document).text })
  }

  /**
   * AI-generated test
   *
   * A caller that already holds a lock gets no isolated timeline. The version of the lock is the one the computation
   * uses, and a fork would install a version that conflicts with it.
   *
   * ```
   * readAction { assert allowIsolatedCommits throws }
   * assert no forked version was frozen
   * ```
   */
  @Test
  fun `an isolated commit under a lock runs the action directly`() = runVersionedTest {
    val document = editor.document
    resetToBaseText(document)
    val documentManager = PsiDocumentManager.getInstance(project)

    readAction {
      assertThrows<IllegalStateException> {
        documentManager.allowIsolatedCommits(document) { }
      }
    }
  }

  /**
   * AI-generated test
   *
   * Each document has its own isolated timeline, so a nested call for another document would run that document on a
   * timeline that does not belong to it.
   *
   * ```
   * allowIsolatedCommits(document) {
   *   allowIsolatedCommits(otherDocument) { }    // reports an error
   * }
   * ```
   */
  @Test
  fun `a nested isolated commit of another document reports an error`() = runVersionedTest {
    val document = editor.document
    val otherDocument = otherEditor.document
    resetToBaseText(document)
    val documentManager = PsiDocumentManager.getInstance(project)
    val messages = mutableListOf<String>()

    withContext(Dispatchers.UiWithModelAccess) {
      LoggedErrorProcessor.executeWith<Throwable>(object : LoggedErrorProcessor() {
        override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
          messages.add(message)
          return Action.NONE
        }
      }) {
        documentManager.allowIsolatedCommits(document) {
          documentManager.allowIsolatedCommits(otherDocument) { }
        }
      }
    }

    Assertions.assertEquals(1, messages.size, "A nested call for another document must report one error: $messages")
    Assertions.assertTrue(messages.single().contains("has no isolated timeline"), messages.single())
  }

  /**
   * AI-generated test.
   *
   * The central invariant of a lightweight commit: the baseline that the fork records is the text that its committed PSI matches.
   *
   * ```
   * document.setText(T1);
   * allowIsolatedCommits {
   *   assert getLastCommittedText == psiFile.text
   *   commitDocument()
   * }
   *
   * document.setText(T2);
   * allowIsolatedCommits {
   *   assert getLastCommittedText == psiFile.text
   *   commitDocument()
   * }
   * ```
   */
  @Test
  fun `a lightweight commit records the text that its psi matches`() = runVersionedTest {
    val document = editor.document
    resetToBaseText(document)
    val documentManager = PsiDocumentManager.getInstance(project)
    val secondText = UPDATED_TEXT.replace("int updated = 1;", "int updated = 1;\n      int second = 2;")

    withContext(Dispatchers.UiWithModelAccess) {
      for (text in listOf(UPDATED_TEXT, secondText)) {
        WriteCommandAction.runWriteCommandAction(project) {
          document.setText(text)
        }
        documentManager.allowIsolatedCommits(document) {
          documentManager.commitDocument(document)
          Assertions.assertEquals(
            currentPsiFile(document).text,
            documentManager.getLastCommittedText(document).toString(),
            "the fork must record the text that its PSI matches",
          )
        }
      }
    }
  }

  /**
   * AI-generated test.
   *
   * `lightweight commit does not publish document commit side effects` covers the after-events of a PSI change.
   * This test covers the before-events, which `DiffLog` suppresses on its own condition.
   *
   * The two halves of one event pair must agree. A before-event without its after-event leaves each listener that
   * tracks a change in progress in a broken state.
   */
  @Test
  fun `a lightweight commit fires no before-change psi events`(@TestDisposable disposable: Disposable) = runVersionedTest {
    val document = editor.document
    resetToBaseText(document)
    val documentManager = PsiDocumentManager.getInstance(project)
    val beforeEvents = AtomicInteger()
    val afterEvents = AtomicInteger()
    val listener = object : PsiTreeChangeAdapter() {
      override fun beforeChildAddition(event: PsiTreeChangeEvent) {
        beforeEvents.incrementAndGet()
      }

      override fun beforeChildRemoval(event: PsiTreeChangeEvent) {
        beforeEvents.incrementAndGet()
      }

      override fun beforeChildReplacement(event: PsiTreeChangeEvent) {
        beforeEvents.incrementAndGet()
      }

      override fun beforeChildrenChange(event: PsiTreeChangeEvent) {
        beforeEvents.incrementAndGet()
      }

      override fun beforePropertyChange(event: PsiTreeChangeEvent) {
        beforeEvents.incrementAndGet()
      }

      override fun childAdded(event: PsiTreeChangeEvent) {
        afterEvents.incrementAndGet()
      }

      override fun childRemoved(event: PsiTreeChangeEvent) {
        afterEvents.incrementAndGet()
      }

      override fun childReplaced(event: PsiTreeChangeEvent) {
        afterEvents.incrementAndGet()
      }

      override fun childrenChanged(event: PsiTreeChangeEvent) {
        afterEvents.incrementAndGet()
      }

      override fun propertyChanged(event: PsiTreeChangeEvent) {
        afterEvents.incrementAndGet()
      }
    }
    PsiManager.getInstance(project).addPsiTreeChangeListener(listener, disposable)

    withContext(Dispatchers.UiWithModelAccess) {
      WriteCommandAction.runWriteCommandAction(project) {
        document.setText(UPDATED_TEXT)
      }
      documentManager.allowIsolatedCommits(document) {
        documentManager.commitDocument(document)
      }
    }

    Assertions.assertEquals(0, beforeEvents.get(), "a lightweight commit must fire no before-change PSI event")
    Assertions.assertEquals(0, afterEvents.get(), "a lightweight commit must fire no PSI change event")
  }

  /**
   * Returns each frozen version that belongs to a forked timeline. A forked version is an odd one.
   */
  private fun frozenForkedVersions(): Set<Long> {
    return InternalPsiVersioning.PsiVersionRegistry.instance.getFrozenKeys().filterTo(mutableSetOf()) { it % 2 != 0L }
  }

  private fun runVersionedTest(action: suspend () -> Unit) {
    val disposable = Disposer.newDisposable()
    (PsiDocumentManagerBase.getInstance(project) as PsiDocumentManagerBase).disableBackgroundCommit(disposable)
    try {
      timeoutRunBlocking(context = Dispatchers.Default) {
        action()
      }
    } finally {
      Disposer.dispose(disposable)
    }
  }

  private suspend fun resetToBaseText(document: Document) {
    writeCommandAction(project, "") {
      document.setText(BASE_TEXT)
      PsiDocumentManager.getInstance(project).commitDocument(document)
    }
    readAction {
      val psiFile = currentPsiFile(document)
      PsiTestUtil.checkFileStructure(psiFile)
      Assertions.assertEquals(BASE_TEXT, psiFile.text)
    }
  }

  private fun currentPsiFile(document: Document): PsiFile {
    return PsiDocumentManager.getInstance(project).getPsiFile(document) ?: error("PSI file should exist for $document")
  }
}
