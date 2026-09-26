// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.lexer.DummyLexer
import com.intellij.lexer.Lexer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.testFramework.loadExtensionWithText
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.DefaultStubBuilder
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.NamedStubBase
import com.intellij.psi.stubs.PsiFileStub
import com.intellij.psi.stubs.PsiFileStubImpl
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.IStubFileElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

/**
 * A brand-new [com.intellij.psi.stubs.StubIndexExtension] can't be given real data for an existing
 * element like PsiMethod: languages' `indexStub()` implementations are hardcoded to their own keys
 * (see `JavaMethodStubSerializer.indexStub()`), so a new extension for `PsiMethod` never receives any
 * occurrences no matter how many times the file is reindexed. This test instead defines a small,
 * self-contained stub-supporting language whose own `indexStub()` writes into the new key, so the
 * new index can be proven to actually receive real data once it's registered.
 */
@TestApplication
class StubIndexNewExtensionRebuildTest {

  private val project = projectFixture(openAfterCreation = true)
  private val module = project.moduleFixture("src")
  private val sourceRoot = module.sourceRootFixture()
  private val psiFile = sourceRoot.psiFileFixture("Foo.${TestStubFileType.defaultExtension}", "hello")

  @Test
  fun `newly registered stub index extension is populated from an already-indexed file`(@TestDisposable disposable: Disposable) = runBlocking {
    withTimeout(1.minutes) {
      // Constructing a stub element type outside the platform's own startup sequence logs an error
      // (IStubElementType's "should be created before index initialization" check); it's benign here
      // since this test-only language never ships as a real plugin.
      LoggedErrorProcessor.executeWith(IgnoreLateStubElementTypeRegistration()).use {
        val fileTypeManager = FileTypeManager.getInstance() as FileTypeManagerImpl
        val corePlugin = PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID)!!
        withContext(Dispatchers.EDT) {
          writeIntentReadAction {
            fileTypeManager.registerFileType(
              TestStubFileType, listOf(ExtensionFileNameMatcher(TestStubFileType.defaultExtension)), disposable, corePlugin
            )
          }
        }
        LanguageParserDefinitions.INSTANCE.addExplicitExtension(TestStubLanguage, TestStubParserDefinition(), disposable)

        // SerializationManagerImpl scans all constructed IElementTypes for stub serializers exactly
        // once per process (see SerializationManagerImpl#doInitSerializedUnderLock), and that scan has
        // almost certainly already run before this test's element types ever existed. Force-construct
        // them now (before dropping the cache) so that whichever code re-triggers the scan next - ours
        // or an unrelated file's - is guaranteed to already see them in the global IElementType registry.
        TestWordElementType.toString()
        TestStubFileElementType.toString()

        // Registering any stubElementTypeHolder fires SerializationManagerImpl's change listener, which
        // drops the cached serializer data and forces a fresh scan next time a stub gets (de)serialized.
        withContext(Dispatchers.EDT) {
          val holderText = "<stubElementTypeHolder class=\"${Any::class.java.name}\"/>"
          Disposer.register(disposable, loadExtensionWithText(holderText))
        }

        val project = psiFile.get().project
        IndexingTestUtil.suspendUntilIndexesAreReady(project)

        NewTestStringStubIndexExtension.extensionRegistered = true
        withContext(Dispatchers.EDT) {
          val text = "<stubIndex implementation=\"${NewTestStringStubIndexExtension::class.java.name}\"/>"
          Disposer.register(disposable, loadExtensionWithText(text))
        }
        IndexingTestUtil.suspendUntilIndexesAreReady(project)

        val scope = GlobalSearchScope.allScope(project)
        val elements = smartReadAction(project) {
          StubIndex.getElements(NewTestStringStubIndexExtension.KEY, "hello", project, scope, TestWordPsiElement::class.java)
        }
        assertEquals(listOf("hello"), elements.map { it.name })
      }
    }
  }
}

private class IgnoreLateStubElementTypeRegistration : LoggedErrorProcessor() {
  override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
    if (category == "#" + IStubElementType::class.java.name) {
      return setOf(Action.LOG, Action.STDERR)
    }
    return super.processError(category, message, details, t)
  }
}

private object TestStubLanguage : Language("TestStubLanguage45129")

object TestStubFileType : LanguageFileType(TestStubLanguage) {
  override fun getName(): String = "TestStubFileType45129"
  override fun getDescription(): String = "TestStubFileType45129"
  override fun getDefaultExtension(): String = "teststub45129"
  override fun getIcon() = null
}

class TestStubPsiFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, TestStubLanguage) {
  override fun getFileType() = TestStubFileType
}

// A separate, plain leaf token type from the stub element type below: reusing the same IElementType
// for both meant the raw lexer token AND its wrapping composite shared one type, so DefaultStubBuilder's
// walker also tried (and failed) to build a stub for the leaf, whose PSI never goes through
// ParserDefinition.createElement().
private object TestWordTokenType : IElementType("WORD_TOKEN45129", TestStubLanguage)

object TestWordElementType : IStubElementType<TestWordStub, TestWordPsiElement>("WORD45129", TestStubLanguage) {
  override fun createPsi(stub: TestWordStub): TestWordPsiElement = TestWordPsiElement(stub)

  override fun createStub(psi: TestWordPsiElement, parentStub: StubElement<*>?): TestWordStub =
    TestWordStub(parentStub, psi.text)

  override fun getExternalId(): String = "teststub45129.WORD"

  override fun serialize(stub: TestWordStub, dataStream: StubOutputStream) {
    dataStream.writeName(stub.name)
  }

  override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): TestWordStub =
    TestWordStub(parentStub, dataStream.readNameString())

  override fun indexStub(stub: TestWordStub, sink: IndexSink) {
    // Mirrors a real plugin update: the "old" indexStub() (extensionRegistered == false) doesn't know
    // about the new key at all, so it must not call sink.occurrence() for it - only the "new" version
    // (after the extension is actually registered) does. Without this gate, indexing the file for the
    // first time (before registration) fails with "Can't find stub index extension for key ...".
    val name = stub.name
    if (name != null && NewTestStringStubIndexExtension.extensionRegistered) {
      sink.occurrence(NewTestStringStubIndexExtension.KEY, name)
    }
  }
}

class TestWordStub(parent: StubElement<*>?, name: String?) :
  NamedStubBase<TestWordPsiElement>(parent, TestWordElementType, name)

class TestWordPsiElement : StubBasedPsiElementBase<TestWordStub>, StubBasedPsiElement<TestWordStub>, PsiNamedElement {
  constructor(node: ASTNode) : super(node)
  constructor(stub: TestWordStub) : super(stub, TestWordElementType)

  override fun getName(): String? = greenStub?.name
  override fun setName(name: String): PsiElement = this
  override fun toString(): String = "TestWordPsiElement"
}

object TestStubFileElementType : IStubFileElementType<PsiFileStub<TestStubPsiFile>>(TestStubLanguage) {
  override fun getExternalId(): String = "teststub45129.FILE"

  override fun getBuilder() = object : DefaultStubBuilder() {
    override fun createStubForFile(file: PsiFile): StubElement<*> = TestFileStub(file as TestStubPsiFile)
  }
}

class TestFileStub(file: TestStubPsiFile?) : PsiFileStubImpl<TestStubPsiFile>(file) {
  override fun getFileElementType(): IStubFileElementType<*> = TestStubFileElementType
}

class TestStubParser : PsiParser {
  override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
    val fileMarker = builder.mark()
    if (!builder.eof()) {
      val wordMarker = builder.mark()
      builder.advanceLexer()
      wordMarker.done(TestWordElementType)
    }
    fileMarker.done(root)
    return builder.treeBuilt
  }
}

class TestStubParserDefinition : ParserDefinition {
  override fun createLexer(project: Project?): Lexer = DummyLexer(TestWordTokenType)
  override fun createParser(project: Project?): PsiParser = TestStubParser()
  override fun getFileNodeType(): IFileElementType = TestStubFileElementType
  override fun getWhitespaceTokens(): TokenSet = TokenSet.EMPTY
  override fun getCommentTokens(): TokenSet = TokenSet.EMPTY
  override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY
  override fun createElement(node: ASTNode): PsiElement = TestWordPsiElement(node)
  override fun createFile(viewProvider: FileViewProvider): PsiFile = TestStubPsiFile(viewProvider)
}

internal class NewTestStringStubIndexExtension : StringStubIndexExtension<TestWordPsiElement>() {
  companion object {
    // Randomized so reruns in the same sandbox always see a genuinely new key, with no leftover
    // on-disk state from a previous run under the same name.
    val KEY: StubIndexKey<String, TestWordPsiElement> =
      StubIndexKey.createIndexKey("StubIndexNewExtensionRebuildTest.word_${Random.nextInt()}")

    // Flips to true only once the extension is actually registered - see indexStub()'s comment.
    @Volatile
    var extensionRegistered: Boolean = false
  }

  override fun getKey(): StubIndexKey<String, TestWordPsiElement> = KEY
}
