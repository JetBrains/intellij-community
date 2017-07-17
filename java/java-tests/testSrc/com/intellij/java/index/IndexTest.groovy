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
package com.intellij.java.index

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.impl.CurrentEditorProvider
import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
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
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys
import com.intellij.psi.impl.source.JavaFileElementType
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.impl.source.PsiFileWithStubSupport
import com.intellij.psi.search.EverythingGlobalScope
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.stubs.SerializedStubTree
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexImpl
import com.intellij.psi.stubs.StubUpdatingIndex
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.exceptionCases.IllegalArgumentExceptionCase
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.FileContentUtil
import com.intellij.util.IncorrectOperationException
import com.intellij.util.Processor
import com.intellij.util.indexing.*
import com.intellij.util.indexing.impl.MapIndexStorage
import com.intellij.util.indexing.impl.MapReduceIndex
import com.intellij.util.io.*
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull

/**
 * @author Eugene Zhuravlev
 * @since Dec 12, 2007
 */
@SkipSlowTestLocally
class IndexTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void invokeTestRunnable(@NotNull Runnable runnable) throws Exception {
    if ("testUndoToFileContentForUnsavedCommittedDocument".equals(getName())) {
      super.invokeTestRunnable(runnable)
    }
    else {
      WriteCommandAction.runWriteCommandAction(getProject(), runnable)
    }
  }

  void testUpdate() throws StorageException, IOException {
    StringIndex index = createIndex(getTestName(false), new EnumeratorStringDescriptor(), false)

    try {
      // build index
      index.update("com/ppp/a.java", "a b c d", null)
      index.update("com/ppp/b.java", "a b g h", null)
      index.update("com/ppp/c.java", "a z f", null)
      index.update("com/ppp/d.java", "a a u y z", null)
      index.update("com/ppp/e.java", "a n chj e c d", null)

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
      index.update("com/ppp/d.java", "a u y z", "a a u y z")
      assertDataEquals(index.getFilesByWord("a"), "com/ppp/a.java", "com/ppp/b.java", "com/ppp/c.java", "com/ppp/d.java", "com/ppp/e.java")
      index.update("com/ppp/d.java", "u y z", "a u y z")
      assertDataEquals(index.getFilesByWord("a"), "com/ppp/a.java", "com/ppp/b.java", "com/ppp/c.java", "com/ppp/e.java")
      index.update("com/ppp/d.java", "a a a u y z", "u y z")
      assertDataEquals(index.getFilesByWord("a"), "com/ppp/a.java", "com/ppp/b.java", "com/ppp/c.java", "com/ppp/d.java", "com/ppp/e.java")

      index.update("com/ppp/e.java", "a n chj e c d z", "a n chj e c d")
      assertDataEquals(index.getFilesByWord("z"), "com/ppp/c.java", "com/ppp/d.java", "com/ppp/e.java")

      index.update("com/ppp/b.java", null, "a b g h")
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
      index.update("a.java", "x", null)
      assertDataEquals(index.getFilesByWord("x"), "a.java")
      index.flush() //todo: this should not be required but the following line will fail without it
      assertDataEquals(index.getFilesByWord("X"), "a.java")

      index.update("b.java", "y", null)
      assertDataEquals(index.getFilesByWord("y"), "b.java")
      index.update("c.java", "Y", null)
      index.flush() //todo: this should not be required but the following line will fail without it
      assertDataEquals(index.getFilesByWord("y"), "b.java", "c.java")
    }
    finally {
      index.dispose()
    }
  }

  private static StringIndex createIndex(String testName, EnumeratorStringDescriptor keyDescriptor, boolean readOnly) {
    final File storageFile = FileUtil.createTempFile("index_test", "storage")
    final File metaIndexFile = FileUtil.createTempFile("index_test_inputs", "storage")
    PersistentHashMap<Integer, Collection<String>>  index = createMetaIndex(metaIndexFile)
    final VfsAwareMapIndexStorage indexStorage = new VfsAwareMapIndexStorage(storageFile, keyDescriptor, new EnumeratorStringDescriptor(), 16 * 1024, readOnly)
    return new StringIndex(testName, indexStorage, index, !readOnly)
  }
  
  private static PersistentHashMap<Integer, Collection<String>> createMetaIndex(File metaIndexFile) throws IOException {
    return new PersistentHashMap<Integer, Collection<String>>(metaIndexFile, new EnumeratorIntegerDescriptor(), new DataExternalizer<Collection<String>>() {
      @Override
      void save(@NotNull DataOutput out, Collection<String> value) throws IOException {
        DataInputOutputUtil.writeINT(out, value.size())
        for (String key : value) {
          out.writeUTF(key)
        }
      }

      @Override
      Collection<String> read(@NotNull DataInput _in) throws IOException {
        final int size = DataInputOutputUtil.readINT(_in)
        final List<String> list = new ArrayList<String>()
        for (int idx = 0; idx < size; idx++) {
          list.add(_in.readUTF())
        }
        return list
      }
    })
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
    document.deleteString(0, document.getTextLength())
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
    document.deleteString(0, document.getTextLength())
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments()
    document.insertString(0, " ")

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
    document.insertString(0, "class Foo {}")
    FileDocumentManager.getInstance().saveDocument(document)

    assertTrue(count == getPsiManager().getModificationTracker().getModificationCount())
    assertNull(findClass("Foo"))

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments()
    assertNotNull(findClass("Foo"))
    assertNotNull(findClass("Foo").getText())
    // if Foo exists now, mod count should be different
    assertTrue(count != getPsiManager().getModificationTracker().getModificationCount())
  }

  void testSkipUnknownFileTypes() throws IOException {
    final VirtualFile vFile = myFixture.addFileToProject("Foo.test", "Foo").getVirtualFile()
    assertEquals(PlainTextFileType.INSTANCE, vFile.getFileType())
    final PsiSearchHelper helper = PsiSearchHelper.SERVICE.getInstance(getProject())
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

    ((PsiJavaFile)psiFile).importList.add(elementFactory.createImportStatementOnDemand("java.io"))

    PlatformTestUtil.tryGcSoftlyReachableObjects()

    assert JavaPsiFacade.getInstance(project).findClass("Foo", scope)

    assert !((FileManagerImpl) psiManager.fileManager).getCachedDirectory(psiFile.virtualFile.parent)
    assert psiFile.setName("Foo1.java") == psiFile

    assert FileDocumentManager.instance.unsavedDocuments
    assert JavaPsiFacade.getInstance(project).findClass("Foo", scope)
  }

  void "test rename dir with unsaved file"() {
    def psiFile = myFixture.addFileToProject("foo/Foo.java", "package pkg; class Foo {}")
    def scope = GlobalSearchScope.allScope(project)

    assert !FileDocumentManager.instance.unsavedDocuments

    ((PsiJavaFile)psiFile).importList.add(elementFactory.createImportStatementOnDemand("java.io"))

    PlatformTestUtil.tryGcSoftlyReachableObjects()

    assert JavaPsiFacade.getInstance(project).findClass("pkg.Foo", scope)

    def dir = psiFile.virtualFile.parent
    assert !((FileManagerImpl) psiManager.fileManager).getCachedDirectory(dir)
    dir.rename(this, "bar")

    assert FileDocumentManager.instance.unsavedDocuments
    assert JavaPsiFacade.getInstance(project).findClass("pkg.Foo", scope)
  }

  void "test language level change"() {
    def psiFile = myFixture.addFileToProject("Foo.java", "class Foo {}")
    def scope = GlobalSearchScope.allScope(project)

    psiFile.add(elementFactory.createEnum("SomeEnum"))

    CodeStyleManager.getInstance(getProject()).reformat(psiFile)
    assert JavaPsiFacade.getInstance(project).findClass("Foo", scope)

    IdeaTestUtil.setModuleLanguageLevel(myFixture.module, LanguageLevel.JDK_1_3)

    assert ((PsiJavaFile)psiFile).importList.node
  }

  void "test language level change2"() {
    def psiFile = myFixture.addFileToProject("Foo.java", "class Foo {}")
    def vFile = psiFile.virtualFile
    def scope = GlobalSearchScope.allScope(project)

    psiFile.add(elementFactory.createEnum("SomeEnum"))

    CodeStyleManager.getInstance(getProject()).reformat(psiFile)
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting()

    assert JavaPsiFacade.getInstance(project).findClass("Foo", scope)

    IdeaTestUtil.setModuleLanguageLevel(myFixture.module, LanguageLevel.JDK_1_3)
    assert ((PsiJavaFile)getPsiManager().findFile(vFile)).importList.node

    PlatformTestUtil.tryGcSoftlyReachableObjects()
    assert ((PsiJavaFile)getPsiManager().findFile(vFile)).importList.node
  }

  void "test changing a file without psi makes the document committed and updates index"() {
    def psiFile = myFixture.addFileToProject("Foo.java", "class Foo {}")
    def vFile = psiFile.virtualFile
    def scope = GlobalSearchScope.allScope(project)

    FileDocumentManager.instance.getDocument(vFile).text = "import zoo.Zoo; class Foo1 {}"
    assert PsiDocumentManager.getInstance(project).uncommittedDocuments

    FileDocumentManager.instance.saveAllDocuments()
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    //noinspection GroovyUnusedAssignment
    psiFile = null
    PlatformTestUtil.tryGcSoftlyReachableObjects()
    assert !((PsiManagerEx) psiManager).fileManager.getCachedPsiFile(vFile)

    VfsUtil.saveText(vFile, "class Foo3 {}")

    assert !PsiDocumentManager.getInstance(project).uncommittedDocuments

    assert JavaPsiFacade.getInstance(project).findClass("Foo3", scope)
  }

  void "test rename file invalidates indices in right order"() throws IOException {
    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject())

    for(def i = 0; i < 100; ++i) {
      final VirtualFile file = myFixture.addFileToProject("foo/Foo" + i + ".java", "package foo; class Foo" + i + " {}").getVirtualFile()
      assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("foo.Foo" + i, scope))
      file.rename(this, "Bar" + i + ".java")
      assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("foo.Foo" + i, scope))
    }
  }

  void "test no index stamp update when no change"() throws IOException {
    final VirtualFile vFile = myFixture.addClass("class Foo {}").getContainingFile().getVirtualFile()
    def stamp = ((FileBasedIndexImpl)FileBasedIndex.instance).getIndexModificationStamp(IdIndex.NAME, project)

    VfsUtil.saveText(vFile, "Foo class")
    assertTrue(stamp == ((FileBasedIndexImpl)FileBasedIndex.instance).getIndexModificationStamp(IdIndex.NAME, project))

    VfsUtil.saveText(vFile, "class Foo2 {}")
    assertTrue(stamp != ((FileBasedIndexImpl)FileBasedIndex.instance).getIndexModificationStamp(IdIndex.NAME, project))

    final Document document = FileDocumentManager.getInstance().getDocument(vFile)
    document.setText("Foo2 class")
    stamp = ((FileBasedIndexImpl)FileBasedIndex.instance).getIndexModificationStamp(IdIndex.NAME, project)
    document.setText("class Foo2")
    assertTrue(stamp == ((FileBasedIndexImpl)FileBasedIndex.instance).getIndexModificationStamp(IdIndex.NAME, project))

    document.setText("Foo3 class")
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assertTrue(stamp != ((FileBasedIndexImpl)FileBasedIndex.instance).getIndexModificationStamp(IdIndex.NAME, project))
  }

  void "test snapshot index in memory state after commit of unsaved document"() throws IOException {
    final VirtualFile vFile = myFixture.addClass("class Foo {}").getContainingFile().getVirtualFile()
    def classEntry = new IdIndexEntry("class", true)
    def findValueProcessor = { file, value -> file != vFile } as FileBasedIndex.ValueProcessor
    
    def projectScope = GlobalSearchScope.projectScope(project)
    def result = FileBasedIndex.instance.processValues(IdIndexImpl.NAME, classEntry, null, findValueProcessor,
                                                       projectScope)
    assertFalse(result)
    
    final Document document = FileDocumentManager.getInstance().getDocument(vFile)
    document.setText("")
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    result = FileBasedIndex.instance.processValues(IdIndexImpl.NAME, classEntry, null, findValueProcessor,
                                                   projectScope)
    assertTrue(result)
  }

  void "test no stub index stamp update when no change"() throws IOException {
    final VirtualFile vFile = myFixture.addClass("class Foo {}").getContainingFile().getVirtualFile()
    def stamp = ((StubIndexImpl)StubIndex.instance).getIndexModificationStamp(JavaStubIndexKeys.CLASS_SHORT_NAMES, project)

    VfsUtil.saveText(vFile, "class Foo { int foo; }")
    assertTrue(stamp == ((StubIndexImpl)StubIndex.instance).getIndexModificationStamp(JavaStubIndexKeys.CLASS_SHORT_NAMES, project))

    VfsUtil.saveText(vFile, "class Foo2 { }")
    assertTrue(stamp != ((StubIndexImpl)StubIndex.instance).getIndexModificationStamp(JavaStubIndexKeys.CLASS_SHORT_NAMES, project))

    final Document document = FileDocumentManager.getInstance().getDocument(vFile)
    document.setText("class Foo3 {}")
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    stamp = ((StubIndexImpl)StubIndex.instance).getIndexModificationStamp(JavaStubIndexKeys.CLASS_SHORT_NAMES, project)

    document.setText("class Foo3 { int foo; }")
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assertTrue(stamp == ((StubIndexImpl)StubIndex.instance).getIndexModificationStamp(JavaStubIndexKeys.CLASS_SHORT_NAMES, project))

    document.setText("class Foo2 { }")
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assertTrue(stamp != ((StubIndexImpl)StubIndex.instance).getIndexModificationStamp(JavaStubIndexKeys.CLASS_SHORT_NAMES, project))
  }

  void "test do not collect stub tree while holding stub elements"() throws IOException {
    final VirtualFile vFile = myFixture.addClass("class Foo {}").getContainingFile().getVirtualFile()

    PsiFileWithStubSupport psiFile = getPsiManager().findFile(vFile) as PsiFileWithStubSupport
    assertNotNull(psiFile)

    def clazz = findClass("Foo")
    assertNotNull(clazz)
    def stubTreeHash = psiFile.getStubTree().hashCode()

    PlatformTestUtil.tryGcSoftlyReachableObjects()
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
      StubIndex.instance.processElements(JavaStubIndexKeys.CLASS_SHORT_NAMES, className, project, scope,
                                         PsiClass.class,
                                         new Processor<PsiClass>() {
                                           @Override
                                           boolean process(PsiClass aClass) {
                                             foundClass[0] = true
                                             StubIndex.instance.processElements(JavaStubIndexKeys.METHODS, "bar", project, scope,
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
    } catch (e) {
      if (!(e instanceof RuntimeException)) throw e
    }

    assertTrue(foundClass[0])
    assertTrue(foundMethod[0]) // allow access stub index processing other index

    def foundClassProcessAll = [false]
    def foundClassStub = [false]

    try {
      StubIndex.instance.processAllKeys(JavaStubIndexKeys.CLASS_SHORT_NAMES, project,
                                        new Processor<String>() {
                                          @Override
                                          boolean process(String aClass) {
                                            if (!className.equals(aClass)) return true;
                                            foundClassProcessAll[0] = true
                                            StubIndex.instance.processElements(JavaStubIndexKeys.CLASS_SHORT_NAMES, aClass, project, scope,
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
    } catch (e) {
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
    } catch (e) {
      if (!(e instanceof RuntimeException)) throw e
    }

    assertTrue(foundId[0])
    assertTrue(!foundStub[0])
  }

  void testNullProjectScope() throws Throwable {
    final GlobalSearchScope allScope = new EverythingGlobalScope(null)
    // create file to be indexed
    final VirtualFile testFile = myFixture.addFileToProject("test.txt", "test").getVirtualFile()
    assertNoException(new IllegalArgumentExceptionCase() {
      @Override
      void tryClosure() throws IllegalArgumentException {
        //force to index new file with null project scope
        FileBasedIndex.getInstance().ensureUpToDate(IdIndex.NAME, null, allScope)
      }
    })
  }

  class RecordingVfsListener extends IndexedFilesListener {
    def vfsEventMerger = new VfsEventsMerger()

    @Override
    protected void iterateIndexableFiles(@NotNull VirtualFile file, @NotNull ContentIterator iterator) {
      VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor() {
        @Override
        boolean visitFile(@NotNull VirtualFile visitedFile) {
          iterator.processFile(visitedFile)
          return true
        }
      })
    }

    protected void doInvalidateIndicesForFile(@NotNull VirtualFile file, boolean contentChange) {
      vfsEventMerger.recordBeforeFileEvent(((VirtualFileWithId)file).id, file, contentChange)
    }

    @Override
    protected void buildIndicesForFile(@NotNull VirtualFile file, boolean contentChange) {
      vfsEventMerger.recordFileEvent(((VirtualFileWithId)file).id, file, contentChange)
    }

    String indexingOperation(VirtualFile file) {
      Ref<String> operation = new Ref<>()
      vfsEventMerger.processChanges(new VfsEventsMerger.VfsEventProcessor() {
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

    ApplicationManager.getApplication().getMessageBus().connect(myFixture.getTestRootDisposable()).subscribe(
      VirtualFileManager.VFS_CHANGES,
      listener
    )

    def fileName = "test.txt"
    final VirtualFile testFile = myFixture.addFileToProject(fileName, "test").getVirtualFile()

    assertEquals("file: $fileName\n" +
                 "operation: UPDATE-REMOVE UPDATE ADD", listener.indexingOperation(testFile))

    FileContentUtil.reparseFiles(testFile)

    assertEquals("file: $fileName\n" +
                 "operation: REMOVE ADD", listener.indexingOperation(testFile))

    VfsUtil.saveText(testFile, "foo")
    VfsUtil.saveText(testFile, "bar")

    assertEquals("file: $fileName\n" +
                 "operation: UPDATE-REMOVE UPDATE", listener.indexingOperation(testFile))

    VfsUtil.saveText(testFile, "baz")
    testFile.delete(null)

    assertEquals("file: $fileName\n" +
                 "operation: REMOVE", listener.indexingOperation(testFile))
  }

  void "test files inside copied directory are indexed"() {
    def facade = JavaPsiFacade.getInstance(project)

    def srcFile = myFixture.addFileToProject('foo/bar/A.java', 'class A {}')
    assert facade.findClass('A', GlobalSearchScope.moduleScope(myModule)) != null

    def anotherDir = myFixture.tempDirFixture.findOrCreateDir('another')
    def anotherModule = PsiTestUtil.addModule(project, StdModuleTypes.JAVA, 'another', anotherDir)
    assert !facade.findClass('A', GlobalSearchScope.moduleScope(anotherModule))

    WriteAction.run { srcFile.virtualFile.parent.copy(this, anotherDir, 'doo') }

    assert facade.findClass('A', GlobalSearchScope.moduleScope(anotherModule)) != null
    assert JavaFileElementType.isInSourceContent(myFixture.tempDirFixture.getFile('another/doo/A.java'))
  }

  void "test requesting nonexisted index fails as expected"() {
    ID<?, ?> myId = ID.create("my.id")
    FileBasedIndex.instance.getContainingFiles(myId, "null", GlobalSearchScope.allScope(project))
    FileBasedIndex.instance.processAllKeys(myId, Processor.TRUE, project)
  }

  void "test read-only index access"() {
    StringIndex index = createIndex(getTestName(false), new EnumeratorStringDescriptor(), true)

    try {
      assertFalse(index.update("qwe/asd", "some_string", null))
      def rebuildException = index.getRebuildException()
      assertInstanceOf(rebuildException, StorageException.class)
      def rebuildCause = rebuildException.getCause()
      assertInstanceOf(rebuildCause, IncorrectOperationException.class)
    } finally {
      index.dispose()
    }
  }

  void "test commit without reparse properly changes index"() {
    def srcFile = myFixture.addFileToProject('A.java', 'class A {}')
    assert findClass('A' ) != null

    Document document = FileDocumentManager.getInstance().getDocument(srcFile.virtualFile)
    document.replaceString(0, document.getTextLength(), 'class B {}')
    assertNotNull(findClass('A'))
    
    ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(project)).doCommitWithoutReparse(document)

    assertNull(findClass("A"))
    assertNotNull(findClass("B"))

    document.replaceString(0, document.getTextLength(), 'class C {}')
    assertNotNull(findClass('B'))

    FileDocumentManager.getInstance().saveDocument(document)
    ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(project)).doCommitWithoutReparse(document)

    assertNull(findClass("B"))
    assertNotNull(findClass("C"))
  }
  
  void "test read-only index has read-only storages"() {
    def index = createIndex(getTestName(false), new EnumeratorStringDescriptor(), true).getIndex()

    try {
      MapIndexStorage<String, String> storage = assertInstanceOf(index, MapReduceIndex.class).getStorage()
      def map = storage.getIndexMap()
      assertTrue(map.getReadOnly())
      assertTrue(map.getValueStorage().isReadOnly())
    } finally {
      index.dispose()
    }
  }

  @CompileStatic
  void "test Vfs Events Processing Performance"() {
    def filename = 'A.java'
    myFixture.addFileToProject('foo/bar/' + filename, 'class A {}')

    PlatformTestUtil.startPerformanceTest("Vfs Event Processing By Index", 1000, {
      def files = FilenameIndex.getFilesByName(project, filename, GlobalSearchScope.moduleScope(myModule))
      assert files?.length == 1

      VirtualFile file = files[0].virtualFile

      def filename2 = 'B.java'
      def max = 100000
      List<VFileEvent> eventList = new ArrayList<>(max)
      def len = max / 2

      for(int i = 0; i < len; ++i) {
        eventList.add(new VFilePropertyChangeEvent(null, file, VirtualFile.PROP_NAME, filename, filename2, true))
        eventList.add(new VFilePropertyChangeEvent(null, file, VirtualFile.PROP_NAME, filename2, filename, true))
        eventList.add(new VFileDeleteEvent(null, file, true))
        eventList.add(new VFileCreateEvent(null, file.parent, filename, false, true))
      }

      IndexedFilesListener indexedFilesListener = ((FileBasedIndexImpl)FileBasedIndex.instance).changedFilesCollector
      indexedFilesListener.before(eventList)
      indexedFilesListener.after(eventList)

      files = FilenameIndex.getFilesByName(project, filename, GlobalSearchScope.moduleScope(myModule))
      assert files?.length == 1
    }).assertTiming()
  }
}
