// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.versioning

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.AbstractFileViewProvider
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.source.CharTableImpl
import com.intellij.psi.impl.source.DummyHolder
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.impl.source.tree.ChangeUtil
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.impl.source.tree.CompositePsiElement
import com.intellij.psi.impl.source.tree.FileElement
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning.PsiVersioningLockingListener
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiUtilBase
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.PsiVersioningService
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ref.GCWatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestApplication
internal class FileViewProviderVersioningConsistencyTest {

  private companion object {
    private val leafType = IElementType("PsiVersioningLeaf", null)
    private val compositeType = IElementType("PsiVersioningComposite", null)
  }

  private val _tempDir = tempPathFixture()
  private val _project = projectFixture(_tempDir, openAfterCreation = true)
  private val _module = _project.moduleFixture("basic")
  private val _sourceRoot = _module.sourceRootFixture(pathFixture = _tempDir)
  private val _psiFile = _sourceRoot.psiFileFixture("Main.java", """
    public class Main {
      public static void main(String[] args) {
         <caret> 
      }
    }
  """.trimIndent())
  private val _substratePsiFile = _sourceRoot.psiFileFixture("SubstrateTarget.java", """
    public class SubstrateTarget {
      void marker() {}
    }
  """.trimIndent())
  private val _editor = _psiFile.editorFixture()

  private val project by _project
  private val psiFile by _psiFile
  private val substratePsiFile by _substratePsiFile
  private val editor by _editor

  @BeforeEach
  fun awaitIndexing() {
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    runWriteAction {
      (psiFile.manager as PsiManagerEx).fileManagerEx.forceReload(psiFile.virtualFile)
    }
  }

  @Test
  fun `psiFile#getText can be retrieved in versioned environment`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    // triggering loading of AST
    withContext(Dispatchers.EDT) {
      PsiDocumentManager.getInstance(project).commitDocument(editor.document)
      PsiDocumentManager.getInstance(project).getPsiFile(editor.document)!!.findElementAt(editor.caretModel.offset)!!
    }
    PsiVersioningService.freezePsiVersion {
      val textAfterModification = async {
        writeCommandAction(project, "") {
          editor.document.insertString(editor.caretModel.offset, "System.out.println(\"Hello World!\");")
          PsiDocumentManager.getInstance(project).commitDocument(editor.document)
        }
        readAction {
          PsiDocumentManager.getInstance(project).getPsiFile(editor.document)!!.text
        }
      }.asCompletableFuture().get()
      val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)!!
      val frozenText = file.text
      Assertions.assertTrue { textAfterModification.contains("Hello World!") }
      Assertions.assertFalse { frozenText.contains("Hello World!")}
    }
  }

  @Test
  fun `FileViewProvider#getContents stays frozen after PSI tree modifications`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val file = PsiVersioningService.freezePsiVersion {
      createPlainTextFile("initial")
    }
    val assertLiveContentsAfterModification: (String, (FileElement) -> Unit) -> Unit = { expectedContents, modification ->
      val liveContents = async {
        writeCommandAction(project, "") {
          val root = file.node as FileElement
          modification(root)
        }
        readAction { file.viewProvider.contents.toString() }
      }.asCompletableFuture().get()
      Assertions.assertEquals(expectedContents, liveContents)
    }

    val frozenContents = PsiVersioningService.freezePsiVersion {
      val frozenContents = file.viewProvider.contents.toString()

      assertLiveContentsAfterModification("alpha") { root ->
        ChangeUtil.prepareAndRunChangeAction(ChangeUtil.ChangeAction {
          root.replaceChild(root.firstChildNode, psiLeaf("alpha"))
        }, root)
      }
      assertFrozenContents(file, frozenContents, "simple replacement")

      assertLiveContentsAfterModification("AbB") { root ->
        root.replaceChild(root.firstChildNode, composite(psiLeaf("a"), composite(psiLeaf("b")), psiLeaf("c")))
        ChangeUtil.prepareAndRunChangeAction(ChangeUtil.ChangeAction {
          val outer = root.firstChildNode
          val middle = outer.firstChildNode.treeNext as CompositeElement
          outer.replaceChild(outer.firstChildNode, psiLeaf("A"))
          middle.addChild(psiLeaf("B"), null)
          outer.removeChild(outer.lastChildNode)
        }, root)
      }
      assertFrozenContents(file, frozenContents, "nested changes")

      assertLiveContentsAfterModification("two-three") { root ->
        ChangeUtil.prepareAndRunChangeAction(ChangeUtil.ChangeAction {
          root.replaceChild(root.firstChildNode, composite(psiLeaf("two"), psiLeaf("-"), psiLeaf("three")))
        }, root)
      }
      assertFrozenContents(file, frozenContents, "sequential replacement")

      assertLiveContentsAfterModification("psi-ast") { root ->
        ChangeUtil.prepareAndRunChangeAction(ChangeUtil.ChangeAction {
          root.replaceChild(root.firstChildNode, composite(psiLeaf("psi"), astOnlyLeaf("-ast")))
        }, root)
      }
      assertFrozenContents(file, frozenContents, "AST-only node change")

      frozenContents
    }

    Assertions.assertEquals("psi-ast", readAction { file.viewProvider.contents.toString() })
    Assertions.assertEquals("initial", frozenContents)
  }

  @Test
  fun `explicitly dropped files can be recreated in frozen environment`() = timeoutRunBlocking(context = Dispatchers.Default) {
    val file = psiFile
    val fileManager = (psiFile.manager as PsiManagerEx).fileManagerEx
    withContext(Dispatchers.UiWithModelAccess) {
      backgroundWriteAction {
        fileManager.forceReload(file.virtualFile)
      }
      PsiVersioningService.freezePsiVersion {
        Assertions.assertNotNull(PsiDocumentManager.getInstance(project).getPsiFile(editor.document))
      }
    }
  }

  @Test
  fun `a file that is captured in versioned environment can be discovered later`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val fileManager = (psiFile.manager as PsiManagerEx).fileManagerEx
    runReadActionBlocking {
      PsiDocumentManager.getInstance(project).getPsiFile(editor.document)!! // force loading the file so that it is available
    }
    PsiVersioningService.freezePsiVersion {
      val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)!!
      launch {
        backgroundWriteAction {
          fileManager.forceReload(file.virtualFile)
        }
      }.asCompletableFuture().join()
      println("Original file hashcode: ${file.hashCode()}")
      val newPsiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)!!
      Assertions.assertEquals(file, newPsiFile)
    }
  }

  @Test
  fun `invalidated PsiFile stays invalid in frozen PSI after live resurrection`() {
    val fileManager = (psiFile.manager as PsiManagerEx).fileManagerEx
    val file = runReadActionBlocking {
      PsiDocumentManager.getInstance(project).getPsiFile(editor.document)!!
    }

    runWriteAction {
      DebugUtil.performPsiModification<Throwable>("test possibly invalidate physical PSI") {
        fileManager.possiblyInvalidatePhysicalPsi()
      }
    }

    PsiVersioningService.freezePsiVersion {
      ThreadingAssertions.assertNoReadAccess()
      Assertions.assertFalse(file.isValid)
    }

    runReadActionBlocking {
      Assertions.assertTrue(file.isValid, "The live PSI should be resurrected outside frozen PSI")
    }

    PsiVersioningService.freezePsiVersion {
      ThreadingAssertions.assertNoReadAccess()
      Assertions.assertFalse(file.isValid, "Frozen PSI should keep the invalidation and must not observe live resurrection")
    }
  }

  // until it is possible to use document outside RA, this test will fail
  // @Test
  fun `PsiUtilBase#getLanguageInEditor can be retrieved in without read lock`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    launch(Dispatchers.UiWithModelAccess) { // UI because we touch editor
      val file = runReadActionBlocking {
        PsiDocumentManager.getInstance(project).getPsiFile(editor.document)!!
      }
      PsiVersioningService.freezePsiVersion {
        ThreadingAssertions.assertNoReadAccess()
        file.assertAstState(shouldBeBuilt = false)
        val lang = PsiUtilBase.getLanguageInEditor(editor, project)
        Assertions.assertEquals(JavaLanguage.INSTANCE, lang)
        Assertions.assertInstanceOf(PsiWhiteSpace::class.java, PsiUtilCore.getElementAtOffset(file, editor.caretModel.offset))
      }
    }
  }

  @Test
  fun `PsiUtilCore#findLanguageFromElement can be retrieved in without read lock`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    launch(Dispatchers.UI) { // UI because we touch editor
      PsiVersioningService.freezePsiVersion {
        ThreadingAssertions.assertNoReadAccess()
        val file = psiFile
        file.assertAstState(shouldBeBuilt = true)
        val lang = PsiUtilCore.findLanguageFromElement(file.findElementAt(editor.caretModel.offset)!!)
        Assertions.assertEquals(JavaLanguage.INSTANCE, lang)
      }
    }
  }

  @Test
  fun `getContainingFile can be retrieved in without read lock`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    launch(Dispatchers.UiWithModelAccess) { // UI because we touch editor
      val file = runReadActionBlocking {
        val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)!! // force loading the file so that it is available
        file.node // force loading AST
        file
      }
      PsiVersioningService.freezePsiVersion {
        ThreadingAssertions.assertNoReadAccess()
        file.assertAstState(shouldBeBuilt = true)
        val element = file.findElementAt(editor.caretModel.offset)!!
        val containingFile = element.containingFile
        Assertions.assertEquals(file, containingFile)
      }
    }
  }

  @Test
  fun `freezePsiVersion activates getCurrentVersionInsideFrozenPsi`() {
    Assertions.assertNull(InternalPsiVersioning.getCurrentPsiVersionInsideFrozenPsi())
    PsiVersioningService.freezePsiVersion {
      Assertions.assertNotNull(InternalPsiVersioning.getCurrentPsiVersionInsideFrozenPsi())
    }
    runReadActionBlocking {
      Assertions.assertNull(InternalPsiVersioning.getCurrentPsiVersionInsideFrozenPsi())
    }
  }

  @Test
  fun `syntax tree is available in frozen version after live file switches to indexes and released later`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val file = runReadActionBlocking {
      PsiManager.getInstance(project).findFile(substratePsiFile.virtualFile) as PsiFileImpl
    }
    runReadActionBlocking {
      GCWatcher.tracking(file.node)
    }.ensureCollected()

    val (mainClass, expectedText) = runReadActionBlocking {
      file.assertAstState(shouldBeBuilt = false)
      val mainClass = JavaPsiFacade.getInstance(project).findClass("SubstrateTarget", GlobalSearchScope.allScope(project))
                      ?: throw AssertionError("SubstrateTarget class should be available from indexes")

      val expectedText = file.text
      Assertions.assertEquals(expectedText, file.node.text)
      file.assertAstState(shouldBeBuilt = true)
      Assertions.assertTrue(mainClass.text.contains("class SubstrateTarget"))
      SubstrateFileUnderTest(mainClass, expectedText)
    }

    val frozenSyntaxWatcher = PsiVersioningService.freezePsiVersion {
      ThreadingAssertions.assertNoReadAccess()
      Assertions.assertEquals(expectedText, file.text)

      async(Dispatchers.Default) {
        backgroundWriteAction {
          file.setTreeElementPointer(null)
        }
        readAction {
          assertSwitchedToIndexes(file, "SubstrateTarget")
        }
      }.asCompletableFuture().get()

      val frozenNode = file.node
      Assertions.assertEquals(expectedText, frozenNode.text)
      Assertions.assertTrue(mainClass.text.contains("class SubstrateTarget"))

      async(Dispatchers.Default) {
        readAction {
          assertSwitchedToIndexes(file, "SubstrateTarget")
        }
      }.asCompletableFuture().get()

      GCWatcher.tracking(frozenNode)
    }

    frozenSyntaxWatcher.ensureCollected()
    runReadActionBlocking {
      assertSwitchedToIndexes(file, "SubstrateTarget")
      Assertions.assertEquals("SubstrateTarget", mainClass.name)
      assertSwitchedToIndexes(file, "SubstrateTarget")
    }
  }

  @TestFactory
  fun `PsiElement#clone returns results corresponding to the current versioned environment`() = listOf<Triple<String, (() -> Unit) -> Unit, Boolean>>(
    Triple("default environment", { it() }, false),
    Triple("inVersionedEnvironment(true)", { InternalPsiVersioning.inVersionedEnvironment(true) { it() } }, true),
    Triple("inVersionedEnvironment(false)", { InternalPsiVersioning.inVersionedEnvironment(false) { it() } }, false)
  ).map { (testName, wrapper, expectedVersioned) ->
    DynamicTest.dynamicTest(testName) {
      runReadActionBlocking {
        val checkElement = {
          val element = JavaPsiFacade.getElementFactory(project).createExpressionFromText("2 + 2", psiFile)
          Assertions.assertEquals(expectedVersioned, (element.node as TreeElement).isVersioned)
        }

        wrapper(checkElement)
      }
    }
  }


  @Test
  fun `FileViewProvider contents stay consistent during versioned document churn`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    installVersioningListeners(disposable)

    val project = ProjectManager.getInstance().defaultProject
    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    val file = runSyncVersionedWriteAction {
      PsiFileFactory.getInstance(project).createFileFromText(
        "versioned-content.txt",
        PlainTextLanguage.INSTANCE,
        versionedDocumentText(0),
        false,
        false,
      ).apply {
        node // forcing computation of versioned node
      }
    }
    val document = readActionBlocking {
      kotlin.test.assertNotNull(FileDocumentManager.getInstance().getDocument(file.viewProvider.virtualFile))
    }

    readActionBlocking {
      assertConsistentFileViewProviderSnapshot(file, versionedDocumentText(0))
    }

    repeat(50) { iteration ->
      val expectedFrozenText = versionedDocumentText(iteration)
      val nextText = versionedDocumentText(iteration + 1)

      PsiVersioningService.freezePsiVersion {
        ThreadingAssertions.assertNoReadAccess()
        val frozenSnapshot = assertConsistentFileViewProviderSnapshot(file, expectedFrozenText)
        file.viewProvider.contents // touch content so that the inner caches are populated

        async(Dispatchers.Default) {
          backgroundWriteAction {
            InternalPsiVersioning.inVersionedEnvironment(true) {
              document.setText(nextText)
              psiDocumentManager.commitDocument(document)
            }
          }
        }.asCompletableFuture().get()

        repeat(3) {
          assertEquals(frozenSnapshot, fileViewProviderSnapshot(file))
        }
      }

      readActionBlocking {
        assertConsistentFileViewProviderSnapshot(file, nextText)
      }
    }
  }

  @Test
  fun `access to uninitialized document is forbidden in versioned environment`() {
    val file = LightVirtualFile()
    InternalPsiVersioning.freezePsiVersion {
      val exception = assertThrows<IllegalStateException> {
        FileDocumentManager.getInstance().getDocument(file)
      }
      assertTrue { exception.message!!.contains("Attempt to interact with uninitialized document") }
    }
  }

  private fun installVersioningListeners(disposable: Disposable) {
    val listener = PsiVersioningLockingListener()
    ApplicationManagerEx.getApplicationEx().addWriteActionListener(listener, disposable)
    ApplicationManagerEx.getApplicationEx().addReadActionListener(listener, disposable)
    ApplicationManagerEx.getApplicationEx().addWriteIntentReadActionListener(listener, disposable)
    ApplicationManagerEx.getApplicationEx().addSuspendingWriteActionListener(listener, disposable)
  }

  private fun assertConsistentFileViewProviderSnapshot(file: PsiFile, expectedText: String): FileViewProviderSnapshot {
    val snapshot = fileViewProviderSnapshot(file)
    assertEquals(expectedText, snapshot.contents)
    assertEquals(expectedText.length, snapshot.contentsLength)
    assertEquals(snapshot.contents, snapshot.psiText)
    assertEquals(snapshot.contents.length, snapshot.psiTextLength)
    assertEquals(snapshot.contents, snapshot.nodeText)
    assertEquals(snapshot.contents.length, snapshot.nodeTextLength)
    assertTrue(snapshot.knownRoots.isNotEmpty())
    snapshot.knownRoots.forEach { root ->
      assertEquals(snapshot.contents, root.text)
      assertEquals(snapshot.contents.length, root.textLength)
    }
    return snapshot
  }

  private fun fileViewProviderSnapshot(file: PsiFile): FileViewProviderSnapshot {
    val provider = file.viewProvider
    val contents = provider.contents.toString()
    assertEquals(contents, provider.contents.toString())
    val node = file.node
    return FileViewProviderSnapshot(
      contents = contents,
      contentsLength = provider.contents.length,
      modificationStamp = provider.modificationStamp,
      psiText = file.text,
      psiTextLength = file.textLength,
      nodeText = node.text,
      nodeTextLength = node.textLength,
      knownRoots = (provider as AbstractFileViewProvider).knownTreeRoots.map { root ->
        FileAstRootSnapshot(root.text, root.textLength)
      },
    )
  }

  private fun versionedDocumentText(iteration: Int): String {
    return when (iteration % 6) {
      0 -> "alpha-$iteration\nbeta\ngamma"
      1 -> "short-$iteration"
      2 -> "prefix-${"x".repeat(iteration % 9 + 1)}-suffix"
      3 -> ""
      4 -> "line-one\n\nline-$iteration\nend"
      else -> "same-length-${(iteration % 10)}-${((iteration + 3) % 10)}"
    }
  }

  private fun <T> runSyncVersionedWriteAction(action: () -> T): T {
    return runWriteAction {
      InternalPsiVersioning.inVersionedEnvironment(true, action)
    }
  }

  private data class FileViewProviderSnapshot(
    val contents: String,
    val contentsLength: Int,
    val modificationStamp: Long,
    val psiText: String,
    val psiTextLength: Int,
    val nodeText: String,
    val nodeTextLength: Int,
    val knownRoots: List<FileAstRootSnapshot>,
  )

  private data class FileAstRootSnapshot(
    val text: String,
    val textLength: Int,
  )

  private fun PsiFile.assertAstState(shouldBeBuilt: Boolean) {
    val refs = (this as PsiFileImpl).stubTreeOrFileElement
    if (shouldBeBuilt) {
      Assertions.assertNotNull(refs.second, "AST for $this should be built")
    }
    else {
      Assertions.assertNull(refs.second, "AST for $this should not be built")
    }
  }

  private fun assertSwitchedToIndexes(file: PsiFileImpl, className: String) {
    Assertions.assertNull(file.treeElement, "AST should not be retained in the live file")
    Assertions.assertNotNull(file.stubTree, "Stub tree should be available after switching to indexes")
    Assertions.assertNull(file.treeElement, "Index access must not reload AST")
    Assertions.assertNotNull(JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project)))
    Assertions.assertNull(file.treeElement, "Class lookup should stay index-backed")
  }

  private fun assertFrozenContents(file: PsiFile, expectedContents: String, modificationName: String) {
    Assertions.assertEquals(expectedContents, file.viewProvider.contents.toString(), "Frozen contents changed after $modificationName")
  }

  private fun createPlainTextFile(text: String): PsiFile =
    PsiFileFactory.getInstance(project).createFileFromText("a.txt", PlainTextLanguage.INSTANCE, text, true, false)

  private fun psiLeaf(text: String): TreeElement = withDummyHolder(object : LeafPsiElement(leafType, text) {
    override fun toString(): String = text
  })

  private fun astOnlyLeaf(text: String): TreeElement = withDummyHolder(object : LeafElement(leafType, text) {
    override fun toString(): String = text
  })

  private fun composite(vararg children: TreeElement): TreeElement {
    val composite = withDummyHolder(object : CompositePsiElement(compositeType) {
      override fun toString(): String = getChildren(null).asList().toString()
    })
    children.forEach(composite::addChild)
    return composite
  }

  private fun withDummyHolder(e: TreeElement): TreeElement {
    DummyHolder(PsiManager.getInstance(project), e, null, CharTableImpl())
    CodeEditUtil.setNodeGenerated(e, true)
    return e
  }

  private data class SubstrateFileUnderTest(
    val mainClass: PsiClass,
    val text: String,
  )
}

internal fun PsiFile.assertAstState(shouldBeBuilt: Boolean) {
  val refs = (this as PsiFileImpl).stubTreeOrFileElement
  if (shouldBeBuilt) {
    Assertions.assertNotNull(refs.second, "AST for $this should be built")
  }
  else {
    Assertions.assertNull(refs.second, "AST for $this should not be built")
  }
}
