// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.find.ngrams.TrigramIndex
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.ide.todo.TodoConfiguration
import com.intellij.java.index.StringIndex
import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl
import com.intellij.openapi.fileTypes.ExactFileNameMatcher
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.impl.FilePropertyPusher
import com.intellij.openapi.roots.impl.JavaLanguageLevelPusher
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.openapi.vfs.newvfs.impl.VfsData
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.PsiDocumentManagerImpl
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.cache.impl.id.IdIndex
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry
import com.intellij.psi.impl.cache.impl.id.IdIndexImpl
import com.intellij.psi.impl.file.impl.FileManagerImpl
import com.intellij.psi.impl.java.JavaFunctionalExpressionIndex
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys
import com.intellij.psi.impl.search.JavaNullMethodArgumentIndex
import com.intellij.psi.impl.source.*
import com.intellij.psi.search.*
import com.intellij.psi.stubs.*
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.*
import com.intellij.util.indexing.events.IndexedFilesListener
import com.intellij.util.indexing.events.VfsEventsMerger
import com.intellij.util.indexing.impl.IndexDebugProperties
import com.intellij.util.indexing.impl.MapIndexStorage
import com.intellij.util.indexing.impl.MapReduceIndex
import com.intellij.util.indexing.impl.UpdatableValueContainer
import com.intellij.util.indexing.impl.forward.IntForwardIndex
import com.intellij.util.indexing.impl.storage.VfsAwareMapIndexStorage
import com.intellij.util.indexing.impl.storage.VfsAwareMapReduceIndex
import com.intellij.util.io.CaseInsensitiveEnumeratorStringDescriptor
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.PersistentMapBase
import com.intellij.util.ref.GCUtil
import com.intellij.util.ref.GCWatcher
import com.intellij.util.ui.UIUtil
import com.siyeh.ig.JavaOverridingMethodUtil
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.GroovyLanguage

import java.util.concurrent.CountDownLatch

@SkipSlowTestLocally
class IndexTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    if (getName() == "test indexed state for file without content requiring indices") {
      // should add file to test dire as soon as possible
      String otherRoot = myFixture.getTempDirPath() + "/otherRoot"
      assertTrue(new File(otherRoot).mkdirs())

      def exe = new File(otherRoot, "intellij.exe")
      assertTrue(exe.createNewFile())
      FileUtil.writeToFile(exe, new byte[]{1,2,3,22}) // convince IDEA it's binary
      moduleBuilder.addSourceContentRoot(otherRoot)
    }
  }

  void testUpdate() throws StorageException, IOException {
    StringIndex index = createIndex(getTestName(false), new EnumeratorStringDescriptor(), false)

    try {
      // build index
      index.update("com/ppp/a.java", "a b c d")
      index.update("com/ppp/b.java", "a b g h")
      index.update("com/ppp/c.java", "a z f")
      index.update("com/ppp/d.java", "a a u y z")
      index.update("com/ppp/e.java", "a n chj e c d")

      assertDataEquals(index.getFilesByWord("a"), "com/ppp/a.java", "com/ppp/b.java", "com/ppp/c.java", "com/ppp/d.java", "com/ppp/e.java")
      assertDataEquals(index.getFilesByWord("b"), "com/ppp/a.java", "com/ppp/b.java")
      assertDataEquals(index.getFilesByWord("c"), "com/ppp/a.java", "com/ppp/e.java")
      assertDataEquals(index.getFilesByWord("d"), "com/ppp/a.java", "com/ppp/e.java")
      assertDataEquals(index.getFilesByWord("g"), "com/ppp/b.java")
      assertDataEquals(index.getFilesByWord("h"), "com/ppp/b.java")
      assertDataEquals(index.getFilesByWord("z"), "com/ppp/c.java", "com/ppp/d.java")
      assertDataEquals(index.getFilesByWord("f"), "com/ppp/c.java")
      assertDataEquals(index.getFilesByWord("u"), "com/ppp/d.java")
      assertDataEquals(index.getFilesByWord("y"), "com/ppp/d.java")
      assertDataEquals(index.getFilesByWord("n"), "com/ppp/e.java")
      assertDataEquals(index.getFilesByWord("chj"), "com/ppp/e.java")
      assertDataEquals(index.getFilesByWord("e"), "com/ppp/e.java")

      // update index
      index.update("com/ppp/d.java", "a u y z")
      assertDataEquals(index.getFilesByWord("a"), "com/ppp/a.java", "com/ppp/b.java", "com/ppp/c.java", "com/ppp/d.java", "com/ppp/e.java")
      index.update("com/ppp/d.java", "u y z")
      assertDataEquals(index.getFilesByWord("a"), "com/ppp/a.java", "com/ppp/b.java", "com/ppp/c.java", "com/ppp/e.java")
      index.update("com/ppp/d.java", "a a a u y z")
      assertDataEquals(index.getFilesByWord("a"), "com/ppp/a.java", "com/ppp/b.java", "com/ppp/c.java", "com/ppp/d.java", "com/ppp/e.java")

      index.update("com/ppp/e.java", "a n chj e c d z")
      assertDataEquals(index.getFilesByWord("z"), "com/ppp/c.java", "com/ppp/d.java", "com/ppp/e.java")

      index.update("com/ppp/b.java", null)
      assertDataEquals(index.getFilesByWord("a"), "com/ppp/a.java", "com/ppp/c.java", "com/ppp/d.java", "com/ppp/e.java")
      assertDataEquals(index.getFilesByWord("b"), "com/ppp/a.java")
      assertDataEquals(index.getFilesByWord("g"))
      assertDataEquals(index.getFilesByWord("h"))
    }
    finally {
      index.dispose()
    }
  }

  void testUpdateWithCustomEqualityPolicy() {
    def index = createIndex(getTestName(false), new CaseInsensitiveEnumeratorStringDescriptor(), false)
    try {
      index.update("a.java", "x")
      assertDataEquals(index.getFilesByWord("x"), "a.java")
      assertDataEquals(index.getFilesByWord("X"), "a.java")

      index.update("b.java", "y")
      assertDataEquals(index.getFilesByWord("y"), "b.java")
      index.update("c.java", "Y")
      assertDataEquals(index.getFilesByWord("y"), "b.java", "c.java")
    }
    finally {
      index.dispose()
    }
  }

  private static StringIndex createIndex(String testName, EnumeratorStringDescriptor keyDescriptor, boolean readOnly) {
    final File storageFile = FileUtil.createTempFile("index_test", "storage")
    final File metaIndexFile = FileUtil.createTempFile("index_test_inputs", "storage")
    final VfsAwareMapIndexStorage indexStorage = new VfsAwareMapIndexStorage(storageFile.toPath(), keyDescriptor, new EnumeratorStringDescriptor(),
                                                                             16 * 1024, readOnly)
    return new StringIndex(testName, indexStorage, metaIndexFile, !readOnly)
  }

  private static <T> void assertDataEquals(List<T> actual, T... expected) {
    assertSameElements(actual, expected)
  }

  void testCollectedPsiWithChangedDocument() throws IOException {
    final VirtualFile vFile = myFixture.addClass("class Foo {}").getContainingFile().getVirtualFile()

    assertNotNull(findClass("Foo"))
    PsiFile psiFile = getPsiManager().findFile(vFile)
    assertNotNull(psiFile)

    Document document = FileDocumentManager.getInstance().getDocument(vFile)
    WriteCommandAction.runWriteCommandAction(project) { document.deleteString(0, document.getTextLength()) }
    assertNotNull(findClass("Foo"))

    PsiClass foo = findClass("Foo")
    assertNotNull(foo)
    assertTrue(foo.isValid())
    assertEquals("class Foo {}", foo.getText())
    assertTrue(foo.isValid())

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments()
    assertNull(findClass("Foo"))
  }

  void testCollectedPsiWithDocumentChangedCommittedAndChangedAgain() throws IOException {
    final VirtualFile vFile = myFixture.addClass("class Foo {}").getContainingFile().getVirtualFile()

    assertNotNull(findClass("Foo"))
    PsiFile psiFile = getPsiManager().findFile(vFile)
    assertNotNull(psiFile)

    Document document = FileDocumentManager.getInstance().getDocument(vFile)
    WriteCommandAction.runWriteCommandAction(project) { document.deleteString(0, document.getTextLength()) }
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments()
    WriteCommandAction.runWriteCommandAction(project) { document.insertString(0, " ") }

    PsiClass foo = findClass("Foo")
    assertNull(foo)
  }

  private PsiClass findClass(String name) {
    return JavaPsiFacade.getInstance(getProject()).findClass(name, GlobalSearchScope.allScope(getProject()))
  }

  void testSavedUncommittedDocument() throws IOException {
    final VirtualFile vFile = myFixture.addFileToProject("Foo.java", "").getVirtualFile()

    assertNull(findClass("Foo"))
    PsiFile psiFile = getPsiManager().findFile(vFile)
    assertNotNull(psiFile)

    long count = getPsiManager().getModificationTracker().getModificationCount()

    Document document = FileDocumentManager.getInstance().getDocument(vFile)
    WriteCommandAction.runWriteCommandAction(project) { document.insertString(0, "class Foo {}") }
    FileDocumentManager.getInstance().saveDocument(document)

    assertTrue(count == getPsiManager().getModificationTracker().getModificationCount())
    assertNull(findClass("Foo"))

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments()
    assertNotNull(findClass("Foo"))
    assertNotNull(findClass("Foo").getText())
    // if Foo exists now, mod count should be different
    assertTrue(count != getPsiManager().getModificationTracker().getModificationCount())
  }

  void testPersistentChangeAffectsDocument() throws IOException {
    def psiFile = myFixture.addFileToProject("Foo.java", "class Foo {}")
    final VirtualFile vFile = psiFile.getVirtualFile()

    def stamp = ((FileBasedIndexImpl)FileBasedIndex.instance).getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, getProject())

    WriteCommandAction.runWriteCommandAction(getProject(), { CodeStyleManager.getInstance(project).reformat(psiFile) })

    PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
    WriteCommandAction.runWriteCommandAction(project) { PsiManager.getInstance(project).reloadFromDisk(psiFile) }

    assertEquals(stamp, ((FileBasedIndexImpl)FileBasedIndex.instance).getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, getProject()))

    FileContentUtilCore.reparseFiles(Collections.singletonList(vFile))

    def provider = PsiManager.getInstance(project).findViewProvider(vFile)
    def stubTree = ((PsiFileImpl)provider.getPsi(provider.getBaseLanguage())).getGreenStubTree()

    WriteAction.run { VfsUtil.saveText(vFile, "class Bar {}") }

    assertNotNull(findClass("Foo"))
  }

  void testPersistentChangeAffectsUnsavedDocument() throws IOException {
    def psiFile = myFixture.addFileToProject("Foo.java", "class Foo {}")
    final VirtualFile vFile = psiFile.getVirtualFile()

    Document document = FileDocumentManager.getInstance().getDocument(vFile)
    WriteCommandAction.runWriteCommandAction(project) { document.insertString(0, "class f {}") }
    WriteCommandAction.runWriteCommandAction(project) { PsiManager.getInstance(project).reloadFromDisk(psiFile) }
    assertNotNull findClass("Foo")
    WriteAction.run { VfsUtil.saveText(vFile, "class x {}")   }
    WriteCommandAction.runWriteCommandAction(project) { document.insertString(0, "class a {}") }
    GCUtil.tryGcSoftlyReachableObjects()
    assertNotNull findClass("Foo")
  }

  void testSkipUnknownFileTypes() throws IOException {
    final VirtualFile vFile = myFixture.addFileToProject("Foo.test", "Foo").getVirtualFile()
    assertEquals(PlainTextFileType.INSTANCE, vFile.getFileType())
    final PsiSearchHelper helper = PsiSearchHelper.getInstance(getProject())
    assertOneElement(helper.findFilesWithPlainTextWords("Foo"))

    final Document document = FileDocumentManager.getInstance().getDocument(vFile)
    //todo should file type be changed silently without events?
    //assertEquals(UnknownFileType.INSTANCE, vFile.getFileType());

    final PsiFile file = PsiDocumentManager.getInstance(getProject()).getPsiFile(document)
    assertInstanceOf(file, PsiPlainTextFile.class)
    assertEquals("Foo", file.getText())

    assertOneElement(helper.findFilesWithPlainTextWords("Foo"))

    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      void run() {
        document.insertString(0, " ")
        assertEquals("Foo", file.getText())
        assertOneElement(helper.findFilesWithPlainTextWords("Foo"))

        FileDocumentManager.getInstance().saveDocument(document)
        assertEquals("Foo", file.getText())
        assertOneElement(helper.findFilesWithPlainTextWords("Foo"))

        PsiDocumentManager.getInstance(getProject()).commitAllDocuments()
        assertEquals(" Foo", file.getText())
        assertOneElement(helper.findFilesWithPlainTextWords("Foo"))
      }
    })
  }

  void testUndoToFileContentForUnsavedCommittedDocument() throws IOException {
    final VirtualFile vFile = myFixture.addClass("class Foo {}").getContainingFile().getVirtualFile()
    ((VirtualFileSystemEntry)vFile).setModificationStamp(0) // as unchanged file

    final Document document = FileDocumentManager.getInstance().getDocument(vFile)
    assertTrue(document != null)
    assert document.getModificationStamp() == 0
    assertNotNull(findClass("Foo"))

    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      void run() {
        document.insertString(0, "import Bar;\n")
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments()
        assertNotNull(findClass("Foo"))
      }
    })

    final UndoManager undoManager = UndoManager.getInstance(getProject())
    final FileEditor selectedEditor = FileEditorManager.getInstance(getProject()).openFile(vFile, false)[0]
    ((UndoManagerImpl)undoManager).setEditorProvider(new CurrentEditorProvider() {
      @Override
      FileEditor getCurrentEditor() {
        return selectedEditor
      }
    })

    assertTrue(undoManager.isUndoAvailable(selectedEditor))
    FileDocumentManager.getInstance().saveDocument(document)
    undoManager.undo(selectedEditor)

    assertNotNull(findClass("Foo"))
  }

  void "test rename unsaved file"() {
    def psiFile = myFixture.addFileToProject("Foo.java", "class Foo {}")
    def scope = GlobalSearchScope.allScope(project)

    assert !FileDocumentManager.instance.unsavedDocuments

    WriteCommandAction.runWriteCommandAction(getProject(), { ((PsiJavaFile)psiFile).importList.add(elementFactory.createImportStatementOnDemand("java.io")) })

    GCUtil.tryGcSoftlyReachableObjects()

    assert JavaPsiFacade.getInstance(project).findClass("Foo", scope)

    assert !((FileManagerImpl)psiManager.fileManager).getCachedDirectory(psiFile.virtualFile.parent)
    WriteCommandAction.runWriteCommandAction(getProject(), { assert psiFile.setName("Foo1.java") == psiFile })

    assert FileDocumentManager.instance.unsavedDocuments
    assert JavaPsiFacade.getInstance(project).findClass("Foo", scope)
  }

  void "test rename dir with unsaved file"() {
    def psiFile = myFixture.addFileToProject("foo/Foo.java", "package pkg; class Foo {}")
    def scope = GlobalSearchScope.allScope(project)

    assert !FileDocumentManager.instance.unsavedDocuments

    WriteCommandAction.runWriteCommandAction(getProject(), { ((PsiJavaFile)psiFile).importList.add(elementFactory.createImportStatementOnDemand("java.io")) })

    GCUtil.tryGcSoftlyReachableObjects()

    assert JavaPsiFacade.getInstance(project).findClass("pkg.Foo", scope)

    def dir = psiFile.virtualFile.parent
    assert !((FileManagerImpl)psiManager.fileManager).getCachedDirectory(dir)
    WriteAction.run { dir.rename(this, "bar") }

    assert FileDocumentManager.instance.unsavedDocuments
    assert JavaPsiFacade.getInstance(project).findClass("pkg.Foo", scope)
  }

  void "test language level change"() {
    def psiFile = myFixture.addFileToProject("Foo.java", "class Foo {}")
    def scope = GlobalSearchScope.allScope(project)

    WriteCommandAction.runWriteCommandAction(getProject(), { psiFile.add(elementFactory.createEnum("SomeEnum")) })

    WriteCommandAction.runWriteCommandAction(getProject(), { CodeStyleManager.getInstance(getProject()).reformat(psiFile) })
    assert JavaPsiFacade.getInstance(project).findClass("Foo", scope)

    def stamp = ((FileBasedIndexImpl)FileBasedIndex.instance).getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, project)

    IdeaTestUtil.setModuleLanguageLevel(myFixture.module, LanguageLevel.JDK_1_3)

    assert ((PsiJavaFile)psiFile).importList.node

    assert stamp != ((FileBasedIndexImpl)FileBasedIndex.instance).getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, project)
  }

  void "test rename file with indexed associated unsaved document don't lost its data"() {
    def level = LanguageLevel.HIGHEST
    IdeaTestUtil.setModuleLanguageLevel(myFixture.module, level)
    def psiFile = myFixture.addFileToProject("Foo.java", "class Foo {}")

    def file = psiFile.virtualFile
    def scope = GlobalSearchScope.allScope(project)

    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting()
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments()
    def vp = PsiManager.getInstance(project).findViewProvider(file)
    WriteCommandAction.runWriteCommandAction(getProject(), { CodeStyleManager.getInstance(getProject()).reformat(vp.getPsi(vp.baseLanguage)) })

    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting()
    FileContentUtilCore.reparseFiles(Collections.singletonList(file))

    vp = PsiManager.getInstance(project).findViewProvider(file)
    ((PsiFileImpl)vp.getPsi(vp.baseLanguage)).greenStubTree

    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting()
    FileContentUtilCore.reparseFiles(Collections.singletonList(file))

    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting()
    IdeaTestUtil.setModuleLanguageLevel(myFixture.module, level)

    assert JavaPsiFacade.getInstance(project).findClass("Foo", scope)
  }

  void "test language level change2"() {
    def psiFile = myFixture.addFileToProject("Foo.java", "class Foo {}")
    def vFile = psiFile.virtualFile
    def scope = GlobalSearchScope.allScope(project)

    WriteCommandAction.runWriteCommandAction(getProject(), { psiFile.add(elementFactory.createEnum("SomeEnum")) })

    WriteCommandAction.runWriteCommandAction(getProject(), { CodeStyleManager.getInstance(getProject()).reformat(psiFile) })
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting()

    assert JavaPsiFacade.getInstance(project).findClass("Foo", scope)

    IdeaTestUtil.setModuleLanguageLevel(myFixture.module, LanguageLevel.JDK_1_3)
    assert ((PsiJavaFile)getPsiManager().findFile(vFile)).importList.node

    GCUtil.tryGcSoftlyReachableObjects()
    assert ((PsiJavaFile)getPsiManager().findFile(vFile)).importList.node
  }

  void "test unknown file type in stubs"() {
    def vFile = myFixture.addFileToProject("Foo.java", "").virtualFile
    final Document document = FileDocumentManager.getInstance().getDocument(vFile)
    WriteCommandAction.runWriteCommandAction(project) { document.setText("class Foo {}")  }
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assert findClass("Foo")

    WriteAction.run { vFile.rename(null, "Foo1")  }
    assert !findClass("Foo")
  }

  void "test uncommitted saved document 2"() {
    def file = myFixture.addFileToProject('a.java', 'class Foo {}')
    WriteCommandAction.runWriteCommandAction(project) {
      assert findClass('Foo')
      file.viewProvider.document.text = ''
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      file.viewProvider.document.text = 'class Foo {}'
      FileDocumentManager.instance.saveAllDocuments()
      assert !findClass('Foo')
    }
  }

  void "test plain text file type in stubs"() {
    def vFile = myFixture.addFileToProject("Foo.java", "class Bar {}").virtualFile
    assert findClass("Bar")
    final Document document = FileDocumentManager.getInstance().getDocument(vFile)
    WriteCommandAction.runWriteCommandAction(project) { document.setText("class Foo {}") }
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assert findClass("Foo")
    assert !findClass("Bar")

    WriteAction.run { vFile.rename(null, "Foo1") }
    assert !findClass("Foo")
    assert !findClass("Bar")
  }

  void "test changing a file without psi makes the document committed and updates index"() {
    def psiFile = myFixture.addFileToProject("Foo.java", "class Foo {}")
    def vFile = psiFile.virtualFile
    def scope = GlobalSearchScope.allScope(project)

    WriteCommandAction.runWriteCommandAction(project) { FileDocumentManager.instance.getDocument(vFile).text = "import zoo.Zoo; class Foo1 {}" }
    assert PsiDocumentManager.getInstance(project).uncommittedDocuments

    FileDocumentManager.instance.saveAllDocuments()
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    //noinspection GroovyUnusedAssignment
    psiFile = null
    GCWatcher.tracking(((PsiManagerEx)psiManager).fileManager.getCachedPsiFile(vFile)).ensureCollected { UIUtil.dispatchAllInvocationEvents() }
    assert !((PsiManagerEx)psiManager).fileManager.getCachedPsiFile(vFile)

    WriteAction.run { VfsUtil.saveText(vFile, "class Foo3 {}") }

    assert !PsiDocumentManager.getInstance(project).uncommittedDocuments

    assert JavaPsiFacade.getInstance(project).findClass("Foo3", scope)
  }

  void "test rename file invalidates indices in right order"() throws IOException {
    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject())

    for (def i = 0; i < 100; ++i) {
      final VirtualFile file = myFixture.addFileToProject("foo/Foo" + i + ".java", "package foo; class Foo" + i + " {}").getVirtualFile()
      assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("foo.Foo" + i, scope))
      WriteCommandAction.runWriteCommandAction(project) { file.rename(this, "Bar" + i + ".java") }
      assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("foo.Foo" + i, scope))
    }
  }

  void "test no index stamp update when no change"() throws IOException {
    final VirtualFile vFile = myFixture.addClass("class Foo {}").getContainingFile().getVirtualFile()
    def stamp = FileBasedIndex.instance.getIndexModificationStamp(IdIndex.NAME, project)
    assertIsIndexed(vFile)

    WriteAction.run { VfsUtil.saveText(vFile, "Foo class") }
    assertTrue(!((VirtualFileSystemEntry)vFile).isFileIndexed())
    assertTrue(stamp == FileBasedIndex.instance.getIndexModificationStamp(IdIndex.NAME, project))
    assertIsIndexed(vFile)

    WriteAction.run { VfsUtil.saveText(vFile, "class Foo2 {}") }
    assertTrue(stamp != FileBasedIndex.instance.getIndexModificationStamp(IdIndex.NAME, project))

    final Document document = FileDocumentManager.getInstance().getDocument(vFile)
    WriteCommandAction.runWriteCommandAction(project) { document.setText("Foo2 class") }
    stamp = FileBasedIndex.instance.getIndexModificationStamp(IdIndex.NAME, project)
    WriteCommandAction.runWriteCommandAction(project) { document.setText("class Foo2") }
    assertTrue(stamp == FileBasedIndex.instance.getIndexModificationStamp(IdIndex.NAME, project))

    WriteCommandAction.runWriteCommandAction(project) { document.setText("Foo3 class") }
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assertTrue(stamp != FileBasedIndex.instance.getIndexModificationStamp(IdIndex.NAME, project))

    WriteCommandAction.runWriteCommandAction(project) { document.text = "class Foo { Runnable r = ( ) -> {}; }" }
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    stamp = FileBasedIndex.instance.getIndexModificationStamp(JavaFunctionalExpressionIndex.INDEX_ID, project)
    WriteCommandAction.runWriteCommandAction(project) { document.text = "class Foo { Runnable x = () -> { }; }" }
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assert stamp == FileBasedIndex.instance.getIndexModificationStamp(JavaFunctionalExpressionIndex.INDEX_ID, project)
  }

  private static assertIsIndexed(VirtualFile vFile) {
    assertTrue(((VirtualFileSystemEntry)vFile).isFileIndexed() || VfsData.isIsIndexedFlagDisabled())
  }

  void "test no index stamp update when no change 2"() throws IOException {
    @org.intellij.lang.annotations.Language("JAVA")
    def text0 = """            
            class Main111 {
                static void staticMethod(Object o) {
                  staticMethod(null);
                }
            }
"""
    final VirtualFile vFile = myFixture.configureByText(JavaFileType.INSTANCE, text0).virtualFile
    def stamp = FileBasedIndex.instance.getIndexModificationStamp(JavaNullMethodArgumentIndex.INDEX_ID, project)
    def data = new JavaNullMethodArgumentIndex.MethodCallData("staticMethod", 0)
    def files = FileBasedIndex.instance.getContainingFiles(JavaNullMethodArgumentIndex.INDEX_ID, data, GlobalSearchScope.projectScope(project))
    assertTrue(files.size() == 1)
    assertEquals(files[0], vFile)

    @org.intellij.lang.annotations.Language("JAVA")
    def text = """
            class Main {
                static void staticMethod(Object o) {
                  staticMethod(null);
                }
            }
"""
    WriteAction.run { VfsUtil.saveText(vFile, text) }
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    assertTrue(stamp == (FileBasedIndex.instance).getIndexModificationStamp(JavaNullMethodArgumentIndex.INDEX_ID, project))
    files = FileBasedIndex.instance.getContainingFiles(JavaNullMethodArgumentIndex.INDEX_ID, data, GlobalSearchScope.projectScope(project))
    assertTrue(files.size() == 1)
    assertEquals(files[0], vFile)
  }

  void "test snapshot index in memory state after commit of unsaved document"() throws IOException {
    final VirtualFile vFile = myFixture.addClass("class Foo {}").getContainingFile().getVirtualFile()
    def classEntry = new IdIndexEntry("class", true)
    FileBasedIndex.ValueProcessor findValueProcessor = { file, value -> file != vFile }

    def projectScope = GlobalSearchScope.projectScope(project)
    def result = FileBasedIndex.instance.processValues(IdIndexImpl.NAME, classEntry, null, findValueProcessor, projectScope)
    assertFalse(result)

    final Document document = FileDocumentManager.getInstance().getDocument(vFile)
    WriteCommandAction.runWriteCommandAction(project) { document.setText("") }
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    result = FileBasedIndex.instance.processValues(IdIndexImpl.NAME, classEntry, null, findValueProcessor, projectScope)
    assertTrue(result)
  }

  void "test no stub index stamp update when no change"() throws IOException {
    final VirtualFile vFile = myFixture.addClass("class Foo {}").getContainingFile().getVirtualFile()
    def stamp = ((StubIndexImpl)StubIndex.instance).getIndexModificationStamp(JavaStubIndexKeys.CLASS_SHORT_NAMES, project)

    WriteAction.run { VfsUtil.saveText(vFile, "class Foo { int foo; }")  }
    assertTrue(stamp == ((StubIndexImpl)StubIndex.instance).getIndexModificationStamp(JavaStubIndexKeys.CLASS_SHORT_NAMES, project))

    WriteAction.run { VfsUtil.saveText(vFile, "class Foo2 { }") }
    assertTrue(stamp != ((StubIndexImpl)StubIndex.instance).getIndexModificationStamp(JavaStubIndexKeys.CLASS_SHORT_NAMES, project))

    final Document document = FileDocumentManager.getInstance().getDocument(vFile)
    WriteCommandAction.runWriteCommandAction(project) { document.setText("class Foo3 {}") }
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    stamp = ((StubIndexImpl)StubIndex.instance).getIndexModificationStamp(JavaStubIndexKeys.CLASS_SHORT_NAMES, project)

    WriteCommandAction.runWriteCommandAction(project) { document.setText("class Foo3 { int foo; }") }
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assertTrue(stamp == ((StubIndexImpl)StubIndex.instance).getIndexModificationStamp(JavaStubIndexKeys.CLASS_SHORT_NAMES, project))

    WriteCommandAction.runWriteCommandAction(project) { document.setText("class Foo2 { }") }
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assertTrue(stamp != ((StubIndexImpl)StubIndex.instance).getIndexModificationStamp(JavaStubIndexKeys.CLASS_SHORT_NAMES, project))
  }

  void "test internalErrorOfStubProcessingInvalidatesIndex"() throws IOException {
    DefaultLogger.disableStderrDumping(testRootDisposable)

    final VirtualFile vFile = myFixture.addClass("class Foo {}").getContainingFile().getVirtualFile()

    def clazz = Ref.create(findClass("Foo"))
    assert clazz.get() != null

    runFindClassStubIndexQueryThatProducesInvalidResult("Foo")

    GCWatcher.fromClearedRef(clazz).ensureCollected()

    assertNull(findClass("Foo"))

    // check invalidation of transient indices state
    def document = FileDocumentManager.instance.getDocument(vFile)
    WriteCommandAction.runWriteCommandAction(project) { document.setText("class Foo2 {}") }
    PsiDocumentManager.getInstance(project).commitDocument(document)

    clazz = Ref.create(findClass("Foo2"))
    assert clazz.get() != null

    runFindClassStubIndexQueryThatProducesInvalidResult("Foo2")

    GCWatcher.fromClearedRef(clazz).ensureCollected()

    assertNull(findClass("Foo2"))
  }

  private void runFindClassStubIndexQueryThatProducesInvalidResult(String qName) {
    def foundFile = [null]

    def searchScope = GlobalSearchScope.allScope(project)
    def processor = new Processor<PsiFile>() {
      @Override
      boolean process(PsiFile file) {
        foundFile[0] = file
        return false
      }
    }

    try {

      StubIndex.instance.
        processElements(JavaStubIndexKeys.CLASS_FQN, qName, project, searchScope, PsiClass.class, new Processor<PsiClass>() {
          @Override
          boolean process(PsiClass aClass) {
            StubIndex.instance.processElements(JavaStubIndexKeys.CLASS_FQN, qName, project, searchScope, PsiFile.class, processor)

            return false
          }
        })
      fail("Unexpected")
    }
    catch (AssertionError ignored) {
      // stub mismatch
    }

    assertTrue(((StubIndexImpl)StubIndex.instance).areAllProblemsProcessedInTheCurrentThread())

    try {
      StubIndex.instance.processElements(JavaStubIndexKeys.CLASS_FQN, qName, project, searchScope, PsiFile.class, processor)

      fail("Unexpected")
    }
    catch (AssertionError ignored) {
      // stub mismatch
    }
  }

  void "test do not collect stub tree while holding stub elements"() throws IOException {
    final VirtualFile vFile = myFixture.addClass("class Foo {}").getContainingFile().getVirtualFile()

    PsiFileWithStubSupport psiFile = getPsiManager().findFile(vFile) as PsiFileWithStubSupport
    assertNotNull(psiFile)

    def clazz = findClass("Foo")
    assertNotNull(clazz)
    def stubTreeHash = psiFile.getStubTree().hashCode()

    GCUtil.tryGcSoftlyReachableObjects()
    def stubTree = psiFile.getStubTree()
    assertNotNull(stubTree)
    assertEquals(stubTreeHash, stubTree.hashCode())
  }

  void "test report using index from other index"() throws IOException {
    def className = "Foo"
    def vfile = myFixture.addClass("class $className { void bar() {} }").getContainingFile().getVirtualFile()
    def scope = GlobalSearchScope.allScope(project)
    def foundClass = [false]
    def foundMethod = [false]

    try {
      StubIndex.instance.processElements(
        JavaStubIndexKeys.CLASS_SHORT_NAMES, className, project, scope,
        PsiClass.class,
        new Processor<PsiClass>() {
          @Override
          boolean process(PsiClass aClass) {
            foundClass[0] = true
            StubIndex.instance.processElements(
              JavaStubIndexKeys.METHODS, "bar", project, scope,
              PsiMethod.class,
              new Processor<PsiMethod>() {
                @Override
                boolean process(PsiMethod method) {
                  foundMethod[0] = true
                  return true
                }
              })
            return true
          }
        })
    }
    catch (e) {
      if (!(e instanceof RuntimeException)) throw e
    }

    assertTrue(foundClass[0])
    assertTrue(foundMethod[0]) // allow access stub index processing other index

    def foundClassProcessAll = [false]
    def foundClassStub = [false]

    try {
      StubIndex.instance.processAllKeys(
        JavaStubIndexKeys.CLASS_SHORT_NAMES, project,
        new Processor<String>() {
          @Override
          boolean process(String aClass) {
            if (!className.equals(aClass)) return true
            foundClassProcessAll[0] = true
            StubIndex.instance.processElements(
              JavaStubIndexKeys.CLASS_SHORT_NAMES, aClass, project, scope,
              PsiClass.class,
              new Processor<PsiClass>() {
                @Override
                boolean process(PsiClass clazz) {
                  foundClassStub[0] = true
                  return true
                }
              })
            return true
          }
        })
    }
    catch (e) {
      if (!(e instanceof RuntimeException)) throw e
    }

    assertTrue(foundClassProcessAll[0])
    assertTrue(foundClassStub[0])

    def foundId = [false]
    def foundStub = [false]

    try {
      FileBasedIndex.instance.
        processValues(IdIndex.NAME, new IdIndexEntry("Foo", true), null, new FileBasedIndex.ValueProcessor<Integer>() {
          @Override
          boolean process(@NotNull VirtualFile file, Integer value) {
            foundId[0] = true
            FileBasedIndex.instance.processValues(
              StubUpdatingIndex.INDEX_ID,
              vfile.id,
              null,
              new FileBasedIndex.ValueProcessor<SerializedStubTree>() {
                @Override
                boolean process(@NotNull VirtualFile file2, SerializedStubTree value2) {
                  foundStub[0] = true
                  return true
                }
              },
              scope
            )
            return true
          }
        }, scope)
    }
    catch (e) {
      if (!(e instanceof RuntimeException)) throw e
    }

    assertTrue(foundId[0])
    assertTrue(foundStub[0])
  }

  void testNullProjectScope() throws Throwable {
    GlobalSearchScope allScope = new EverythingGlobalScope()
    // create file to be indexed
    final VirtualFile testFile = myFixture.addFileToProject("test.txt", "test").getVirtualFile()
    assertNoException(IllegalArgumentException.class, new ThrowableRunnable<Throwable>() {
      @Override
      void run() throws Throwable {
        //force to index new file with null project scope
        FileBasedIndex.getInstance().ensureUpToDate(IdIndex.NAME, getProject(), allScope)
      }
    })
    assertNotNull(testFile)
  }

  class RecordingVfsListener extends IndexedFilesListener {
    RecordingVfsListener() {
      super()
    }

    @Override
    protected void iterateIndexableFiles(@NotNull VirtualFile file, @NotNull ContentIterator iterator) {
      VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor<Void>() {
        @Override
        boolean visitFile(@NotNull VirtualFile visitedFile) {
          iterator.processFile(visitedFile)
          return true
        }
      })
    }

    String indexingOperation(VirtualFile file) {
      Ref<String> operation = new Ref<>()
      eventMerger.processChanges(new VfsEventsMerger.VfsEventProcessor() {
        @Override
        boolean process(@NotNull VfsEventsMerger.ChangeInfo info) {
          operation.set(info.toString())
          return true
        }
      })

      StringUtil.replace(operation.get(), file.getPath(), file.getName())
    }
  }

  void testIndexedFilesListener() throws Throwable {
    def listener = new RecordingVfsListener()

    VirtualFileManager.instance.addAsyncFileListener(listener, myFixture.testRootDisposable)

    def fileName = "test.txt"
    final VirtualFile testFile = myFixture.addFileToProject(fileName, "test").getVirtualFile()

    assertEquals("file: $fileName; " +
                 "operation: UPDATE ADD", listener.indexingOperation(testFile))

    FileContentUtilCore.reparseFiles(Collections.singletonList(testFile))

    assertEquals("file: $fileName; " +
                 "operation: ADD", listener.indexingOperation(testFile))

    WriteAction.run { VfsUtil.saveText(testFile, "foo") }
    WriteAction.run { VfsUtil.saveText(testFile, "bar") }

    assertEquals("file: $fileName; " +
                 "operation: UPDATE", listener.indexingOperation(testFile))

    WriteAction.run { VfsUtil.saveText(testFile, "baz") }
    WriteAction.run { testFile.delete(null) }

    assertEquals("file: $fileName; " +
                 "operation: REMOVE", listener.indexingOperation(testFile))
  }

  void "test files inside copied directory are indexed"() {
    def facade = JavaPsiFacade.getInstance(project)

    def srcFile = myFixture.addFileToProject('foo/bar/A.java', 'class A {}')
    assert facade.findClass('A', GlobalSearchScope.moduleScope(module)) != null

    def anotherDir = myFixture.tempDirFixture.findOrCreateDir('another')
    def anotherModule = PsiTestUtil.addModule(project, StdModuleTypes.JAVA, 'another', anotherDir)
    assert !facade.findClass('A', GlobalSearchScope.moduleScope(anotherModule))

    WriteAction.run { srcFile.virtualFile.parent.copy(this, anotherDir, 'doo') }

    assert facade.findClass('A', GlobalSearchScope.moduleScope(anotherModule)) != null
    assert JavaFileElementType.isInSourceContent(myFixture.tempDirFixture.getFile('another/doo/A.java'))
  }

  void "test requesting nonexisted index fails as expected"() {
    ID<?, ?> myId = ID.create("my.id")
    try {
      FileBasedIndex.instance.getContainingFiles(myId, "null", GlobalSearchScope.allScope(project))
      FileBasedIndex.instance.processAllKeys(myId, CommonProcessors.alwaysTrue(), project)
      fail()
    }
    catch (IllegalStateException ignored) {}
  }

  void "test read-only index access"() {
    StringIndex index = createIndex(getTestName(false), new EnumeratorStringDescriptor(), true)

    try {
      IndexDebugProperties.IS_UNIT_TEST_MODE = false
      assertFalse(index.update("qwe/asd", "some_string"))
      def rebuildThrowable = index.getRebuildThrowable()
      assertInstanceOf(rebuildThrowable, StorageException.class)
      def rebuildCause = rebuildThrowable.getCause()
      assertInstanceOf(rebuildCause, IncorrectOperationException.class)
    }
    finally {
      IndexDebugProperties.IS_UNIT_TEST_MODE = true
      index.dispose()
    }
  }

  void "test commit without reparse properly changes index"() {
    def srcFile = myFixture.addFileToProject('A.java', 'class A {}')
    assert findClass('A') != null

    Document document = FileDocumentManager.getInstance().getDocument(srcFile.virtualFile)
    WriteCommandAction.runWriteCommandAction(project) { document.replaceString(0, document.getTextLength(), 'class B {}') }
    assertNotNull(findClass('A'))

    ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(project)).commitDocument(document)

    assertNull(findClass("A"))
    assertNotNull(findClass("B"))

    WriteCommandAction.runWriteCommandAction(project) { document.replaceString(0, document.getTextLength(), 'class C {}')  }
    assertNotNull(findClass('B'))

    FileDocumentManager.getInstance().saveDocument(document)
    ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(project)).commitDocument(document)

    assertNull(findClass("B"))
    assertNotNull(findClass("C"))
  }

  void "test reload from disk after adding import"() {
    def file = myFixture.addFileToProject("Foo.java", "class Foo {}") as PsiJavaFile
    WriteCommandAction.runWriteCommandAction(project) { file.importList.add(JavaPsiFacade.getElementFactory(project).createImportStatementOnDemand('java.util')) }
    PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()

    WriteCommandAction.runWriteCommandAction(project) { psiManager.reloadFromDisk(file) }

    assert findClass("Foo")
  }

  void "test read-only index has read-only storages"() {
    def index = createIndex(getTestName(false), new EnumeratorStringDescriptor(), true).getIndex()

    try {
      MapIndexStorage<String, String> storage = assertInstanceOf(index, MapReduceIndex.class).getStorage()
      PersistentMapBase<String, UpdatableValueContainer<String>> map = storage.getIndexMap()
      assertTrue(map.getReadOnly())
      assertTrue(map.getValueStorage().isReadOnly())
    }
    finally {
      index.dispose()
    }
  }

  @CompileStatic
  void "test Vfs Event Processing Performance"() {
    def filename = 'A.java'
    myFixture.addFileToProject('foo/bar/' + filename, 'class A {}')

    PlatformTestUtil.startPerformanceTest("Vfs Event Processing By Index", 1000, {
      def files = FilenameIndex.getFilesByName(project, filename, GlobalSearchScope.moduleScope(module))
      assert files?.length == 1

      VirtualFile file = files[0].virtualFile

      def filename2 = 'B.java'
      def max = 100000
      List<VFileEvent> eventList = new ArrayList<>(max)
      def len = max / 2

      for (int i = 0; i < len; ++i) {
        eventList.add(new VFilePropertyChangeEvent(null, file, VirtualFile.PROP_NAME, filename, filename2, true))
        eventList.add(new VFilePropertyChangeEvent(null, file, VirtualFile.PROP_NAME, filename2, filename, true))
        eventList.add(new VFileDeleteEvent(null, file, true))
        eventList.add(new VFileCreateEvent(null, file.parent, filename, false, null, null, true, null))
      }

      def applier = ((FileBasedIndexImpl)FileBasedIndex.instance).changedFilesCollector.prepareChange(eventList)
      applier.beforeVfsChange()
      applier.afterVfsChange()

      files = FilenameIndex.getFilesByName(project, filename, GlobalSearchScope.moduleScope(module))
      assert files?.length == 1
    }).assertTiming()
  }

  void "test class file in src content isn't returned from index"() {
    def runnable = JavaPsiFacade.getInstance(project).findClass(Runnable.name, GlobalSearchScope.allScope(project))
    def thread = JavaPsiFacade.getInstance(project).findClass(Thread.name, GlobalSearchScope.allScope(project))
    def srcRoot = myFixture.tempDirFixture.getFile("")
    WriteCommandAction.runWriteCommandAction(project) { VfsUtil.copy(this, thread.containingFile.virtualFile, srcRoot) }

    def projectScope = GlobalSearchScope.projectScope(project)
    assert !JavaOverridingMethodUtil.getOverridingMethodsIfCheapEnough(runnable.methods[0], projectScope, { true }).findFirst().present
    assert StubIndex.instance.getElements(JavaStubIndexKeys.METHODS, 'run', project, projectScope, PsiMethod).empty
  }

  void "test text todo indexing checks for cancellation"() {
    TodoPattern pattern = new TodoPattern("(x+x+)+y", TodoAttributesUtil.createDefault(), true)

    TodoPattern[] oldPatterns = TodoConfiguration.getInstance().getTodoPatterns()
    TodoPattern[] newPatterns = [pattern]
    TodoConfiguration.getInstance().setTodoPatterns(newPatterns)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    FileBasedIndex.instance.ensureUpToDate(IdIndex.NAME, project, GlobalSearchScope.allScope(project))
    myFixture.addFileToProject("Foo.txt", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")

    try {
      final CountDownLatch progressStarted = new CountDownLatch(1)
      final ProgressIndicatorBase progressIndicatorBase = new ProgressIndicatorBase()
      boolean[] canceled = [false]
      ApplicationManager.application.executeOnPooledThread({
        progressStarted.await()
        TimeoutUtil.sleep(1000)
        progressIndicatorBase.cancel()
        TimeoutUtil.sleep(500)
        assertTrue(canceled[0])
      })
      ProgressManager.getInstance().runProcess(
        {
          try {
            progressStarted.countDown()
            FileBasedIndex.instance.ensureUpToDate(IdIndex.NAME, project, GlobalSearchScope.allScope(project))
          }
          catch (ProcessCanceledException ignore) {
            canceled[0] = true
          }
        }, progressIndicatorBase
      )
    }
    finally {
      TodoConfiguration.getInstance().setTodoPatterns(oldPatterns)
    }
  }

  void "test stub updating index problem during processAllKeys"() throws IOException {
    def className = "Foo"
    myFixture.addClass("class $className {}")
    def scope = GlobalSearchScope.allScope(project)

    def foundClassProcessAll = [false]
    def foundClassStub = [false]

    try {
      StubIndex.instance.processAllKeys(
        JavaStubIndexKeys.CLASS_SHORT_NAMES, project,
        new Processor<String>() {
          @Override
          boolean process(String aClass) {
            if (!className.equals(aClass)) return true
            foundClassProcessAll[0] = true
            // adding file will add file to index's dirty set but it should not be processed within current read action
            myFixture.addFileToProject("Bar.java", "class Bar { }")
            StubIndex.instance.processElements(
              JavaStubIndexKeys.CLASS_SHORT_NAMES, aClass, project, scope,
              PsiClass.class,
              new Processor<PsiClass>() {
                @Override
                boolean process(PsiClass clazz) {
                  foundClassStub[0] = true
                  return true
                }
              })
            return true
          }
        })
    }
    catch (e) {
      if (!(e instanceof RuntimeException)) throw e
    }

    assertTrue(foundClassProcessAll[0])
    assertTrue(foundClassStub[0]) // allow access stub index processing other index
  }

  void "test document increases beyond too large limit"() {
    String item = createLongSequenceOfCharacterConstants()
    def fileText = 'class Bar { char[] item = { ' + item + "};\n }"
    def file = myFixture.addFileToProject('foo/Bar.java', fileText).virtualFile
    assertNotNull(findClass("Bar"))

    Document document = FileDocumentManager.getInstance().getDocument(file)

    for (int i = 0; i < 2; ++i) {
      WriteCommandAction.runWriteCommandAction(getProject(), { document.replaceString(0, document.textLength, item + item) })
      PsiDocumentManager.getInstance(project).commitDocument(document)
      assertNull(findClass("Bar"))

      WriteCommandAction.runWriteCommandAction(getProject(), { document.replaceString(0, document.textLength, fileText) })
      PsiDocumentManager.getInstance(project).commitDocument(document)
      assertNotNull(findClass("Bar"))
    }
  }

  private static String createLongSequenceOfCharacterConstants() {
    String item = "'c',"
    item * (Integer.highestOneBit(FileUtilRt.userFileSizeLimit) / item.length())
  }

  void "test file increases beyond too large limit"() {
    String item = createLongSequenceOfCharacterConstants()
    def fileText = 'class Bar { char[] item = { ' + item + "};\n }"
    def file = myFixture.addFileToProject('foo/Bar.java', fileText).virtualFile
    assertNotNull(findClass("Bar"))

    for (int i = 0; i < 2; ++i) {
      WriteAction.run { VfsUtil.saveText(file, item + item) }
      assertNull(findClass("Bar"))

      WriteAction.run { VfsUtil.saveText(file, fileText) }
      assertNotNull(findClass("Bar"))
    }
  }

  void "test indexed state for file without content requiring indices"() {
    def scope = GlobalSearchScope.allScope(getProject())
    FileBasedIndex.instance.ensureUpToDate(FileTypeIndex.NAME, project, scope)

    def files = FilenameIndex.getFilesByName(getProject(), "intellij.exe", scope)
    def file = assertOneElement(files).virtualFile
    assertIsIndexed(file)

    WriteCommandAction.runWriteCommandAction(getProject(), { file.rename(this, 'intellij2.exe') })
    FileBasedIndex.instance.ensureUpToDate(FileTypeIndex.NAME, project, scope)
    assertIsIndexed(file)
  }

  void "test IDEA-188028" () {
    def file = myFixture.addFileToProject('a.java', 'class Foo {}') as PsiJavaFileImpl
    WriteCommandAction.runWriteCommandAction(project) {
      def document = file.viewProvider.document
      document.setText('')
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      PsiManager.getInstance(project).reloadFromDisk(file)
      document.setText('')
      assert !findClass('Foo')
      file.virtualFile.rename(this, 'a1.java')
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      assert !findClass('Foo')
    }
  }

  void "test every directory and file are marked as indexed in open project"() {
    VirtualFile foo = myFixture.addFileToProject('src/main/a.java', 'class Foo {}').virtualFile
    VirtualFile main = foo.parent
    VirtualFile src = main.parent

    def scope = GlobalSearchScope.allScope(getProject())
    assertEquals(foo, assertOneElement(FilenameIndex.getVirtualFilesByName("a.java", scope)))
    assertEquals(main, assertOneElement(FilenameIndex.getVirtualFilesByName("main", scope)))
    assertEquals(src, assertOneElement(FilenameIndex.getVirtualFilesByName("src", scope)))

    // content-less indexes has been passed
    // now all directories are indexed

    assertFalse(((VirtualFileSystemEntry)foo).isFileIndexed())
    assertIsIndexed(main)
    assertIsIndexed(src)

    assert findClass("Foo") // ensure content dependent indexes are passed

    assertIsIndexed(foo)
  }

  void "test stub updating index stamp"() {
    final VirtualFile vFile = myFixture.addClass("class Foo {}").getContainingFile().getVirtualFile()
    def stamp = ((FileBasedIndexImpl)FileBasedIndex.instance).getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, project)
    WriteAction.run { VfsUtil.saveText(vFile, "class Foo { void m() {} }") }
    assertTrue(stamp != ((FileBasedIndexImpl)FileBasedIndex.instance).getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, project))
    stamp = ((FileBasedIndexImpl)FileBasedIndex.instance).getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, project)
    WriteAction.run { VfsUtil.saveText(vFile, "class Foo { void m() { int k = 0; } }") }
    assertTrue(stamp == ((FileBasedIndexImpl)FileBasedIndex.instance).getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, project))
  }

  void "test index stamp update on transient data deletion"() {
    WriteCommandAction.runWriteCommandAction(project) {
      def stamp = ((FileBasedIndexImpl)FileBasedIndex.instance).getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, project)
      def file = myFixture.addClass("class Foo {}").getContainingFile()

      ((PsiJavaFile)file).getImportList().add(JavaPsiFacade.getElementFactory(getProject()).createImportStatementOnDemand("java.io"))
      assert findClass("Foo") != null
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments()
      assertTrue(stamp != ((FileBasedIndexImpl)FileBasedIndex.instance).getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, project))
      stamp = ((FileBasedIndexImpl)FileBasedIndex.instance).getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, project)

      assert findClass("Foo") != null
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments()
      assertTrue(stamp == ((FileBasedIndexImpl)FileBasedIndex.instance).getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, project))
      stamp = ((FileBasedIndexImpl)FileBasedIndex.instance).getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, project)

      PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting()
      PsiManager.getInstance(getProject()).reloadFromDisk(file)
      assertTrue(stamp != ((FileBasedIndexImpl)FileBasedIndex.instance).getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, project))

      assert findClass("Foo") != null
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments()
      assertTrue(stamp != ((FileBasedIndexImpl)FileBasedIndex.instance).getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, project))
      stamp = ((FileBasedIndexImpl)FileBasedIndex.instance).getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, project)

      findClass("Foo").replace(findClass("Foo").copy())
      assert findClass("Foo") != null
      assertTrue(stamp == ((FileBasedIndexImpl)FileBasedIndex.instance).getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, project))
    }
  }

  void "test non empty memory storage cleanup advances index modification stamp"() {
    def stamp = ((FileBasedIndexImpl)FileBasedIndex.instance).getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, project)
    def file = myFixture.addClass("class Foo {}").getContainingFile()
    assert findClass("Foo")
    assertTrue(stamp != ((FileBasedIndexImpl)FileBasedIndex.instance).getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, project))
    stamp = ((FileBasedIndexImpl)FileBasedIndex.instance).getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, project)

    WriteCommandAction.runWriteCommandAction(project) { ((PsiJavaFile)file).getImportList().add(JavaPsiFacade.getElementFactory(getProject()).createImportStatementOnDemand("java.io")) }
    assert findClass("Foo")
    assertTrue(stamp != ((FileBasedIndexImpl)FileBasedIndex.instance).getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, project))
    stamp = ((FileBasedIndexImpl)FileBasedIndex.instance).getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, project)

    WriteCommandAction.runWriteCommandAction(project) { ((FileDocumentManagerImpl)FileDocumentManager.instance).dropAllUnsavedDocuments()  }
    assert findClass("Foo")
    assertTrue(stamp != ((FileBasedIndexImpl)FileBasedIndex.instance).getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, project))
  }

  void "test index clear increments modification stamp"() {
    StringIndex index = createIndex(getTestName(false), new EnumeratorStringDescriptor(), false)
    try {
      def stamp = index.getModificationStamp()
      index.clear()
      assertTrue(stamp != index.getModificationStamp())
    }
    finally {
      index.dispose()
    }
  }

  void "test unsaved document is still indexed on dumb mode ignoring access"() {
    def file = (PsiJavaFile)myFixture.addFileToProject("Foo.java", "class Foo {}")
    def nameIdentifier = file.getClasses()[0].getNameIdentifier()

    def project = getProject()
    def dumbService = (DumbServiceImpl)DumbService.getInstance(project)
    def virtualFile = file.getVirtualFile()

    assertTrue(findWordInDumbMode("Foo", virtualFile, false))
    assertFalse(findWordInDumbMode("Bar", virtualFile, false))

    dumbService.setDumb(true)
    try {
      assertTrue(findWordInDumbMode("Foo", virtualFile, true))
      assertFalse(findWordInDumbMode("Bar", virtualFile, true))

      WriteCommandAction.runWriteCommandAction(project) { nameIdentifier.replace(JavaPsiFacade.getElementFactory(project).createIdentifier("Bar")) }
      assertTrue(FileDocumentManager.instance.isDocumentUnsaved(PsiDocumentManager.getInstance(project).getDocument(file)))

      assertTrue(findWordInDumbMode("Bar", virtualFile, true))
      assertFalse(findWordInDumbMode("Foo", virtualFile, true))

    }
    finally {
      dumbService.setDumb(false)
    }

    assertTrue(findWordInDumbMode("Bar", virtualFile, false))
    assertFalse(findWordInDumbMode("Foo", virtualFile, false))
  }

  void "test change file type association from groovy to java"() {
    @org.intellij.lang.annotations.Language("JAVA")
    def text = "class Foo { void m() {" +
               " String x = 'qwerty';" +
               "}}"
    def file = myFixture.addFileToProject("Foo.groovy", text)
    def virtualFile = file.virtualFile

    def idIndexData = getIdIndexData(virtualFile)
    assertTrue(idIndexData.containsKey(new IdIndexEntry("Foo", false)))
    assertTrue(idIndexData.containsKey(new IdIndexEntry("qwerty", false)))
    assertEquals(UsageSearchContext.IN_STRINGS | UsageSearchContext.IN_CODE, idIndexData.get(new IdIndexEntry("qwerty", false)))
    assertEquals(GroovyFileType.GROOVY_FILE_TYPE, FileTypeIndex.getIndexedFileType(virtualFile, getProject()))
    def stub = StubTreeLoader.getInstance().readFromVFile(getProject(), virtualFile)
    assertStubLanguage(GroovyLanguage.INSTANCE, stub)
    assertEquals(GroovyLanguage.INSTANCE, file.getLanguage())
    assert findClass("Foo")
    def matcher = new ExactFileNameMatcher("Foo.groovy")
    try {
      WriteCommandAction.runWriteCommandAction(project) { FileTypeManager.getInstance().associate(JavaFileType.INSTANCE, matcher) }

      assertEquals(JavaFileType.INSTANCE, FileTypeIndex.getIndexedFileType(virtualFile, getProject()))
      stub = StubTreeLoader.getInstance().readFromVFile(getProject(), virtualFile)
      assertStubLanguage(JavaLanguage.INSTANCE, stub)
      idIndexData = getIdIndexData(virtualFile)
      assertTrue(idIndexData.containsKey(new IdIndexEntry("Foo", false)))
      assertFalse(idIndexData.containsKey(new IdIndexEntry("qwerty", false)))
      def javaFooClass = findClass("Foo")
      assertEquals(JavaLanguage.INSTANCE, javaFooClass.getLanguage())
    }
    finally {
      WriteCommandAction.runWriteCommandAction(project) { FileTypeManager.getInstance().removeAssociation(JavaFileType.INSTANCE, matcher) }
    }
  }

  void "test composite index with snapshot mappings hash id"() {
    def groovyFileId = ((VirtualFileWithId)myFixture.addFileToProject("Foo.groovy", "class Foo {}").virtualFile).getId()
    def javaFileId = ((VirtualFileWithId)myFixture.addFileToProject("Foo.java", "class Foo {}").virtualFile).getId()

    def fbi = FileBasedIndex.getInstance()
    fbi.ensureUpToDate(IdIndex.NAME, getProject(), GlobalSearchScope.allScope(getProject()))
    fbi.ensureUpToDate(TrigramIndex.INDEX_ID, getProject(), GlobalSearchScope.allScope(getProject()))
    def idIndex = ((FileBasedIndexImpl)fbi).getIndex(IdIndex.NAME)
    def trigramIndex = ((FileBasedIndexImpl)fbi).getIndex(TrigramIndex.INDEX_ID)

    assertTrue(FileBasedIndex.ourSnapshotMappingsEnabled)
    def idIndexForwardIndex = (IntForwardIndex)((VfsAwareMapReduceIndex)idIndex).getForwardIndex()
    def trigramIndexForwardIndex = (IntForwardIndex)((VfsAwareMapReduceIndex)trigramIndex).getForwardIndex()

    // id index depends on file type
    assertFalse(idIndexForwardIndex.getInt(javaFileId) == 0)
    assertFalse(idIndexForwardIndex.getInt(groovyFileId) == 0)
    assertFalse(idIndexForwardIndex.getInt(groovyFileId) == idIndexForwardIndex.getInt(javaFileId))

    // trigram index is not a composite index
    assertFalse(trigramIndexForwardIndex.getInt(javaFileId) == 0)
    assertFalse(trigramIndexForwardIndex.getInt(groovyFileId) == 0)
    // for trigram index the assertion above can be broken by definition of trigram index
    // assertFalse(trigramIndexForwardIndex.getInt(groovyFileId) == trigramIndexForwardIndex.getInt(javaFileId))
  }

  private boolean findWordInDumbMode(String word, VirtualFile file, boolean inDumbMode) {
    assertTrue(DumbService.isDumb(getProject()) == inDumbMode)
    assertTrue(FileBasedIndex.isIndexAccessDuringDumbModeEnabled())

    def wordHash = new IdIndexEntry(word, true)
    def scope = GlobalSearchScope.allScope(project)
    def fileBasedIndex = FileBasedIndex.instance
    boolean found = false
    def runnable = {
      found = fileBasedIndex.getContainingFiles(IdIndex.NAME, wordHash, scope).contains(file)
    }
    if (inDumbMode) {
      fileBasedIndex.ignoreDumbMode(DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE, runnable)
    }
    else {
      runnable.run()
    }
    return found
  }

  private static assertStubLanguage(@NotNull Language expectedLanguage, @NotNull ObjectStubTree stub) {
    def parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(expectedLanguage)
    assertEquals(parserDefinition.getFileNodeType(), stub.getPlainList().get(0).getType())
  }

  @NotNull
  private Map<IdIndexEntry, Integer> getIdIndexData(@NotNull VirtualFile file) {
    FileBasedIndex.getInstance().getFileData(IdIndex.NAME, file, getProject())
  }

  void 'test no caching for index queries with different ignoreDumbMode kinds'() {
    RecursionManager.disableMissedCacheAssertions(testRootDisposable)
    
    def clazz = myFixture.addClass('class Foo {}')
    assert clazz == myFixture.findClass('Foo')

    DumbServiceImpl.getInstance(project).setDumb(true)

    def indexQueries = 0
    def plainQueries = 0

    def stubQuery = CachedValuesManager.getManager(project).createCachedValue {
      indexQueries++
      CachedValueProvider.Result.create(myFixture.findClass('Foo'), PsiModificationTracker.MODIFICATION_COUNT)
    }
    def idQuery = CachedValuesManager.getManager(project).createCachedValue {
      indexQueries++
      GlobalSearchScope fileScope = GlobalSearchScope.fileScope(clazz.containingFile)
      IdIndexEntry key = new IdIndexEntry('Foo', true)
      def hasId = !FileBasedIndex.instance.getContainingFiles(IdIndex.NAME, key, fileScope).isEmpty()
      CachedValueProvider.Result.create(hasId, PsiModificationTracker.MODIFICATION_COUNT)
    }
    def plainValue = CachedValuesManager.getManager(project).createCachedValue {
      plainQueries++
      CachedValueProvider.Result.create("x", PsiModificationTracker.MODIFICATION_COUNT)
    }

    // index queries aren't cached
    5.times {
      assert clazz == FileBasedIndex.instance.ignoreDumbMode(DumbModeAccessType.RELIABLE_DATA_ONLY, asComputable(stubQuery))
    }
    assert indexQueries >= 5

    indexQueries = 0
    assert FileBasedIndex.instance.ignoreDumbMode(DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE, asComputable(idQuery))
    assert FileBasedIndex.instance.ignoreDumbMode(DumbModeAccessType.RELIABLE_DATA_ONLY, asComputable(idQuery))
    assert indexQueries >= 2

    // non-index queries should work as usual
    3.times {
      assert "x" == FileBasedIndex.instance.ignoreDumbMode(DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE, asComputable(plainValue))
      assert "x" == FileBasedIndex.instance.ignoreDumbMode(DumbModeAccessType.RELIABLE_DATA_ONLY, asComputable(plainValue))
    }
    assert plainQueries > 0 && plainQueries < 3*2

    // cache queries inside single ignoreDumbMode
    indexQueries = 0
    psiManager.dropPsiCaches()
    FileBasedIndex.instance.ignoreDumbMode(DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE) {
      5.times {assert idQuery.getValue() }
      assert indexQueries > 0 && indexQueries < 5
    }

    indexQueries = 0
    psiManager.dropPsiCaches()
    FileBasedIndex.instance.ignoreDumbMode(DumbModeAccessType.RELIABLE_DATA_ONLY) {
      5.times {assert clazz == stubQuery.getValue() }
      assert indexQueries > 0 && indexQueries < 5
    }
  }

  void 'test no caching on write action inside ignoreDumbMode'() {
    RecursionManager.disableMissedCacheAssertions(testRootDisposable)

    def clazz = myFixture.addClass('class Foo {}')
    assert clazz == myFixture.findClass('Foo')

    DumbServiceImpl.getInstance(project).setDumb(true)

    def stubQuery = CachedValuesManager.getManager(project).createCachedValue {
      CachedValueProvider.Result.create(myFixture.javaFacade.findClass('Foo', GlobalSearchScope.allScope(project)),
                                        PsiModificationTracker.MODIFICATION_COUNT)
    }

    FileBasedIndex.instance.ignoreDumbMode(DumbModeAccessType.RELIABLE_DATA_ONLY) {
      assert clazz == stubQuery.getValue()
      WriteCommandAction.runWriteCommandAction(project) {
        clazz.setName('Bar')
      }
      assert null == stubQuery.getValue()
    }
  }

  void 'test indexes should be wiped after scratch removal'() {
    def file = ScratchRootType.getInstance().createScratchFile(project, "Foo.java", JavaLanguage.INSTANCE, "class Foo {}")
    def fileId = ((VirtualFileWithId)file).getId()

    def fileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.instance
    def trigramId = TrigramIndex.INDEX_ID

    fileBasedIndex.ensureUpToDate(trigramId, project, GlobalSearchScope.everythingScope(project))
    assertNotEmpty(fileBasedIndex.getIndex(trigramId).getIndexedFileData(fileId).values())

    WriteCommandAction.runWriteCommandAction(project) { file.delete(null) }
    fileBasedIndex.ensureUpToDate(trigramId, project, GlobalSearchScope.everythingScope(project))
    assertEmpty(fileBasedIndex.getIndex(trigramId).getIndexedFileData(fileId).values())
  }

  void 'test requestReindex'() {
    def file = ScratchRootType.getInstance().createScratchFile(project, "Foo.java", JavaLanguage.INSTANCE, "class Foo {}")

    CountingFileBasedIndexExtension.registerCountingFileBasedIndex(testRootDisposable)

    FileBasedIndex.getInstance().getFileData(CountingFileBasedIndexExtension.INDEX_ID, file, project)
    assertTrue(CountingFileBasedIndexExtension.COUNTER.get() > 0)

    CountingFileBasedIndexExtension.COUNTER.set(0)
    FileBasedIndex.instance.requestReindex(file)

    FileBasedIndex.getInstance().getFileData(CountingFileBasedIndexExtension.INDEX_ID, file, project)
    assertTrue(CountingFileBasedIndexExtension.COUNTER.get() > 0)
  }

  void 'test modified excluded file not present in index'() {
    // we don't update excluded file index data, so we should wipe it to be consistent
    def file = myFixture.addFileToProject("src/to_be_excluded/A.java", "class A {}").virtualFile
    assertNotNull(findClass("A"))

    def fileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.instance
    def trigramId = TrigramIndex.INDEX_ID
    def fileId = ((VirtualFileWithId)file).getId()

    fileBasedIndex.ensureUpToDate(trigramId, project, GlobalSearchScope.everythingScope(project))
    assertNotEmpty(fileBasedIndex.getIndex(trigramId).getIndexedFileData(fileId).values())

    def parentDir = file.parent
    PsiTestUtil.addExcludedRoot(myFixture.getModule(), parentDir)
    WriteAction.run { VfsUtil.saveText(file, "class B {}") }

    fileBasedIndex.ensureUpToDate(trigramId, project, GlobalSearchScope.everythingScope(project))
    assertEmpty(fileBasedIndex.getIndex(trigramId).getIndexedFileData(fileId).values())
    assertFalse(((VirtualFileSystemEntry)file).isFileIndexed())
  }

  void 'test stub index updated after language level change'() {
    def file = myFixture.addFileToProject("src1/A.java", "class A {}").virtualFile
    def javaLanguageLevelKey = FilePropertyPusher.EP_NAME.findExtension(JavaLanguageLevelPusher.class).getFilePropertyKey()

    def languageLevel = javaLanguageLevelKey.getPersistentValue(file.parent)
    assertNotNull(languageLevel)
    assertNotNull(findClass("A"))

    // be a :hacker:😀
    // do it manually somehow
    // seems property pushers are crazy, we know it from its name
    javaLanguageLevelKey.setPersistentValue(file.parent, null)
    // fire any event
    FileContentUtilCore.reparseFiles(file)

    assertNull(javaLanguageLevelKey.getPersistentValue(file.parent))
    assertNull(findClass("A"))

    // and return everything to a normal state
    javaLanguageLevelKey.setPersistentValue(file.parent, languageLevel)
    FileContentUtilCore.reparseFiles(file)

    assertNotNull(javaLanguageLevelKey.getPersistentValue(file.parent))
    assertNotNull(findClass("A"))
  }

  private static <T> ThrowableComputable<T, RuntimeException> asComputable(CachedValue<T> cachedValue) {
    return new ThrowableComputable<T, RuntimeException>() {
      @Override
      T compute() throws RuntimeException {
        return cachedValue.getValue()
      }
    }
  }
}
