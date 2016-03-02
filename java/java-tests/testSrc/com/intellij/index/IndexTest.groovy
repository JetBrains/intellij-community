/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.index

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.impl.CurrentEditorProvider
import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.util.Factory
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.cache.impl.id.IdIndex
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry
import com.intellij.psi.impl.file.impl.FileManagerImpl
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.impl.source.PsiFileWithStubSupport
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.stubs.SerializedStubTree
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubUpdatingIndex
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.Processor
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.MapIndexStorage
import com.intellij.util.indexing.StorageException
import com.intellij.util.io.*
import org.jetbrains.annotations.NotNull
/**
 * @author Eugene Zhuravlev
 * @since Dec 12, 2007
 */
@SkipSlowTestLocally
public class IndexTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void invokeTestRunnable(@NotNull Runnable runnable) throws Exception {
    if ("testUndoToFileContentForUnsavedCommittedDocument".equals(getName())) {
      super.invokeTestRunnable(runnable);
    }
    else {
      WriteCommandAction.runWriteCommandAction(getProject(), runnable);
    }
  }

  public void testUpdate() throws StorageException, IOException {
    StringIndex index = createIndex(new EnumeratorStringDescriptor())

    try {
      // build index
      index.update("com/ppp/a.java", "a b c d", null);
      index.update("com/ppp/b.java", "a b g h", null);
      index.update("com/ppp/c.java", "a z f", null);
      index.update("com/ppp/d.java", "a a u y z", null);
      index.update("com/ppp/e.java", "a n chj e c d", null);

      assertDataEquals(index.getFilesByWord("a"), "com/ppp/a.java", "com/ppp/b.java", "com/ppp/c.java", "com/ppp/d.java", "com/ppp/e.java");
      assertDataEquals(index.getFilesByWord("b"), "com/ppp/a.java", "com/ppp/b.java");
      assertDataEquals(index.getFilesByWord("c"), "com/ppp/a.java", "com/ppp/e.java");
      assertDataEquals(index.getFilesByWord("d"), "com/ppp/a.java", "com/ppp/e.java");
      assertDataEquals(index.getFilesByWord("g"), "com/ppp/b.java");
      assertDataEquals(index.getFilesByWord("h"), "com/ppp/b.java");
      assertDataEquals(index.getFilesByWord("z"), "com/ppp/c.java", "com/ppp/d.java");
      assertDataEquals(index.getFilesByWord("f"), "com/ppp/c.java");
      assertDataEquals(index.getFilesByWord("u"), "com/ppp/d.java");
      assertDataEquals(index.getFilesByWord("y"), "com/ppp/d.java");
      assertDataEquals(index.getFilesByWord("n"), "com/ppp/e.java");
      assertDataEquals(index.getFilesByWord("chj"), "com/ppp/e.java");
      assertDataEquals(index.getFilesByWord("e"), "com/ppp/e.java");

      // update index
      index.update("com/ppp/d.java", "a u y z", "a a u y z");
      assertDataEquals(index.getFilesByWord("a"), "com/ppp/a.java", "com/ppp/b.java", "com/ppp/c.java", "com/ppp/d.java", "com/ppp/e.java");
      index.update("com/ppp/d.java", "u y z", "a u y z");
      assertDataEquals(index.getFilesByWord("a"), "com/ppp/a.java", "com/ppp/b.java", "com/ppp/c.java", "com/ppp/e.java");
      index.update("com/ppp/d.java", "a a a u y z", "u y z");
      assertDataEquals(index.getFilesByWord("a"), "com/ppp/a.java", "com/ppp/b.java", "com/ppp/c.java", "com/ppp/d.java", "com/ppp/e.java");

      index.update("com/ppp/e.java", "a n chj e c d z", "a n chj e c d");
      assertDataEquals(index.getFilesByWord("z"), "com/ppp/c.java", "com/ppp/d.java", "com/ppp/e.java");

      index.update("com/ppp/b.java", null, "a b g h");
      assertDataEquals(index.getFilesByWord("a"), "com/ppp/a.java", "com/ppp/c.java", "com/ppp/d.java", "com/ppp/e.java");
      assertDataEquals(index.getFilesByWord("b"), "com/ppp/a.java");
      assertDataEquals(index.getFilesByWord("g"));
      assertDataEquals(index.getFilesByWord("h"));
    }
    finally {
      index.dispose()
    }
  }

  public void testUpdateWithCustomEqualityPolicy() {
    def index = createIndex(new CaseInsensitiveEnumeratorStringDescriptor())
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

  private static StringIndex createIndex(EnumeratorStringDescriptor keyDescriptor) {
    final File storageFile = FileUtil.createTempFile("index_test", "storage");
    final File metaIndexFile = FileUtil.createTempFile("index_test_inputs", "storage");
    final MapIndexStorage indexStorage = new MapIndexStorage(storageFile, keyDescriptor, new EnumeratorStringDescriptor(), 16 * 1024);
    return new StringIndex(indexStorage, new Factory<PersistentHashMap<Integer, Collection<String>>>() {
      @Override
      public PersistentHashMap<Integer, Collection<String>> create() {
        try {
          return createMetaIndex(metaIndexFile);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }
  
  private static PersistentHashMap<Integer, Collection<String>> createMetaIndex(File metaIndexFile) throws IOException {
    return new PersistentHashMap<Integer, Collection<String>>(metaIndexFile, new EnumeratorIntegerDescriptor(), new DataExternalizer<Collection<String>>() {
      @Override
      public void save(@NotNull DataOutput out, Collection<String> value) throws IOException {
        DataInputOutputUtil.writeINT(out, value.size());
        for (String key : value) {
          out.writeUTF(key);
        }
      }

      @Override
      public Collection<String> read(@NotNull DataInput _in) throws IOException {
        final int size = DataInputOutputUtil.readINT(_in);
        final List<String> list = new ArrayList<String>();
        for (int idx = 0; idx < size; idx++) {
          list.add(_in.readUTF());
        }
        return list;
      }
    });
  }

  private static <T> void assertDataEquals(List<T> actual, T... expected) {
    assertSameElements(actual, expected);
  }

  public void testCollectedPsiWithChangedDocument() throws IOException {
    final VirtualFile vFile = myFixture.addClass("class Foo {}").getContainingFile().getVirtualFile();

    assertNotNull(findClass("Foo"));
    PsiFile psiFile = getPsiManager().findFile(vFile);
    assertNotNull(psiFile);

    Document document = FileDocumentManager.getInstance().getDocument(vFile);
    document.deleteString(0, document.getTextLength());
    assertNotNull(findClass("Foo"));

    PsiClass foo = findClass("Foo");
    assertNotNull(foo);
    assertTrue(foo.isValid());
    assertEquals("class Foo {}", foo.getText());
    assertTrue(foo.isValid());

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assertNull(findClass("Foo"));
  }
  
  public void testCollectedPsiWithDocumentChangedCommittedAndChangedAgain() throws IOException {
    final VirtualFile vFile = myFixture.addClass("class Foo {}").getContainingFile().getVirtualFile();

    assertNotNull(findClass("Foo"));
    PsiFile psiFile = getPsiManager().findFile(vFile);
    assertNotNull(psiFile);

    Document document = FileDocumentManager.getInstance().getDocument(vFile);
    document.deleteString(0, document.getTextLength());
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    document.insertString(0, " ");

    PsiClass foo = findClass("Foo");
    assertNull(foo);
  }

  private PsiClass findClass(String name) {
    return JavaPsiFacade.getInstance(getProject()).findClass(name, GlobalSearchScope.allScope(getProject()));
  }

  public void testSavedUncommittedDocument() throws IOException {
    final VirtualFile vFile = myFixture.addFileToProject("Foo.java", "").getVirtualFile();

    assertNull(findClass("Foo"));
    PsiFile psiFile = getPsiManager().findFile(vFile);
    assertNotNull(psiFile);

    long count = getPsiManager().getModificationTracker().getModificationCount();

    Document document = FileDocumentManager.getInstance().getDocument(vFile);
    document.insertString(0, "class Foo {}");
    FileDocumentManager.getInstance().saveDocument(document);

    assertTrue(count == getPsiManager().getModificationTracker().getModificationCount());
    assertNull(findClass("Foo"));

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assertNotNull(findClass("Foo"));
    assertNotNull(findClass("Foo").getText());
    // if Foo exists now, mod count should be different
    assertTrue(count != getPsiManager().getModificationTracker().getModificationCount());
  }

  public void testSkipUnknownFileTypes() throws IOException {
    final VirtualFile vFile = myFixture.addFileToProject("Foo.test", "Foo").getVirtualFile();
    assertEquals(PlainTextFileType.INSTANCE, vFile.getFileType());
    final PsiSearchHelper helper = PsiSearchHelper.SERVICE.getInstance(getProject());
    assertOneElement(helper.findFilesWithPlainTextWords("Foo"));

    final Document document = FileDocumentManager.getInstance().getDocument(vFile);
    //todo should file type be changed silently without events?
    //assertEquals(UnknownFileType.INSTANCE, vFile.getFileType());

    final PsiFile file = PsiDocumentManager.getInstance(getProject()).getPsiFile(document);
    assertInstanceOf(file, PsiPlainTextFile.class);
    assertEquals("Foo", file.getText());

    assertOneElement(helper.findFilesWithPlainTextWords("Foo"));

    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        document.insertString(0, " ");
        assertEquals("Foo", file.getText());
        assertOneElement(helper.findFilesWithPlainTextWords("Foo"));

        FileDocumentManager.getInstance().saveDocument(document);
        assertEquals("Foo", file.getText());
        assertOneElement(helper.findFilesWithPlainTextWords("Foo"));

        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        assertEquals(" Foo", file.getText());
        assertOneElement(helper.findFilesWithPlainTextWords("Foo"));
      }
    });
  }

  public void testUndoToFileContentForUnsavedCommittedDocument() throws IOException {
    final VirtualFile vFile = myFixture.addClass("class Foo {}").getContainingFile().getVirtualFile();
    ((VirtualFileSystemEntry)vFile).setModificationStamp(0); // as unchanged file

    final Document document = FileDocumentManager.getInstance().getDocument(vFile);
    assertTrue(document != null);
    assert document.getModificationStamp() == 0;
    assertNotNull(findClass("Foo"));

    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        document.insertString(0, "import Bar;\n");
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        assertNotNull(findClass("Foo"));
      }
    });

    final UndoManager undoManager = UndoManager.getInstance(getProject());
    final FileEditor selectedEditor = FileEditorManager.getInstance(getProject()).openFile(vFile, false)[0];
    ((UndoManagerImpl)undoManager).setEditorProvider(new CurrentEditorProvider() {
      @Override
      public FileEditor getCurrentEditor() {
        return selectedEditor;
      }
    });

    assertTrue(undoManager.isUndoAvailable(selectedEditor));
    FileDocumentManager.getInstance().saveDocument(document);
    undoManager.undo(selectedEditor);

    assertNotNull(findClass("Foo"));
  }

  public void "test rename unsaved file"() {
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

  public void "test rename dir with unsaved file"() {
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

  public void "test language level change"() {
    def psiFile = myFixture.addFileToProject("Foo.java", "class Foo {}")
    def scope = GlobalSearchScope.allScope(project)

    psiFile.add(elementFactory.createEnum("SomeEnum"))

    CodeStyleManager.getInstance(getProject()).reformat(psiFile)
    assert JavaPsiFacade.getInstance(project).findClass("Foo", scope)

    IdeaTestUtil.setModuleLanguageLevel(myFixture.module, LanguageLevel.JDK_1_3)

    assert ((PsiJavaFile)psiFile).importList.node
  }

  public void "test language level change2"() {
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

  public void "test changing a file without psi makes the document committed and updates index"() {
    def psiFile = myFixture.addFileToProject("Foo.java", "class Foo {}")
    def vFile = psiFile.virtualFile
    def scope = GlobalSearchScope.allScope(project)

    FileDocumentManager.instance.getDocument(vFile).text = "import zoo.Zoo; class Foo1 {}"
    assert PsiDocumentManager.getInstance(project).uncommittedDocuments

    FileDocumentManager.instance.saveAllDocuments()
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    //noinspection GroovyUnusedAssignment
    psiFile = null
    PlatformTestUtil.tryGcSoftlyReachableObjects()
    assert !((PsiManagerEx) psiManager).fileManager.getCachedPsiFile(vFile)

    VfsUtil.saveText(vFile, "class Foo3 {}")

    assert !PsiDocumentManager.getInstance(project).uncommittedDocuments

    assert JavaPsiFacade.getInstance(project).findClass("Foo3", scope)
  }

  public void "test rename file invalidates indices in right order"() throws IOException {
    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());

    for(def i = 0; i < 100; ++i) {
      final VirtualFile file = myFixture.addFileToProject("foo/Foo" + i + ".java", "package foo; class Foo" + i + " {}").getVirtualFile();
      assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("foo.Foo" + i, scope));
      file.rename(this, "Bar" + i + ".java");
      assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("foo.Foo" + i, scope));
    }
  }

  public void "test do not collect stub tree while holding stub elements"() throws IOException {
    final VirtualFile vFile = myFixture.addClass("class Foo {}").getContainingFile().getVirtualFile();

    PsiFileWithStubSupport psiFile = getPsiManager().findFile(vFile) as PsiFileWithStubSupport;
    assertNotNull(psiFile);

    def clazz = findClass("Foo")
    assertNotNull(clazz)
    def stubTreeHash = psiFile.getStubTree().hashCode()

    PlatformTestUtil.tryGcSoftlyReachableObjects();
    def stubTree = psiFile.getStubTree()
    assertNotNull(stubTree)
    assertEquals(stubTreeHash, stubTree.hashCode())
  }

  public void "test report using index from other index"() throws IOException {
    def vfile = myFixture.addClass("class Foo { void bar() {} }").getContainingFile().getVirtualFile();
    def scope = GlobalSearchScope.allScope(project)
    def foundClass = [false];
    def foundMethod = [false];

    try {
      StubIndex.instance.processElements(JavaStubIndexKeys.CLASS_SHORT_NAMES, "Foo", project, scope,
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
                                                                                    foundMethod[0] = true;
                                                                                    return true;
                                                                                  }
                                                                                });
                                             return true;
                                           }
                                         });
    } catch (e) {
      if (!(e instanceof RuntimeException)) throw e;
    }

    assertTrue(foundClass[0])
    assertTrue(!foundMethod[0])

    def foundId = [false];
    def foundStub = [false];

    try {
      FileBasedIndex.instance.
        processValues(IdIndex.NAME, new IdIndexEntry("Foo", true), null, new FileBasedIndex.ValueProcessor<Integer>() {
          @Override
          boolean process(VirtualFile file, Integer value) {
            foundId[0] = true
            FileBasedIndex.instance.processValues(
              StubUpdatingIndex.INDEX_ID,
              vfile.id,
              null,
              new FileBasedIndex.ValueProcessor<SerializedStubTree>() {
                @Override
                boolean process(VirtualFile file2, SerializedStubTree value2) {
                  foundStub[0] = true
                  return true
                }
              },
              scope
            );
            return true
          }
        }, scope)
    } catch (e) {
      if (!(e instanceof RuntimeException)) throw e;
    }

    assertTrue(foundId[0])
    assertTrue(!foundStub[0])
  }
}
