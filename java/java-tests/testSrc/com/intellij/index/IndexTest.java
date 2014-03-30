/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.index;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.util.indexing.MapIndexStorage;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 12, 2007
 */
@SkipSlowTestLocally
public class IndexTest extends IdeaTestCase {

  public void testUpdate() throws StorageException, IOException {
    final File storageFile = FileUtil.createTempFile("indextest", "storage");
    final File metaIndexFile = FileUtil.createTempFile("indextest_inputs", "storage");
    final MapIndexStorage indexStorage = new MapIndexStorage(storageFile, new EnumeratorStringDescriptor(), new EnumeratorStringDescriptor(), 16 * 1024);
    final StringIndex index = new StringIndex(indexStorage, new Factory<PersistentHashMap<Integer, Collection<String>>>() {
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
      indexStorage.close();
      FileUtil.delete(storageFile);
    }
  }

  private PersistentHashMap<Integer, Collection<String>> createMetaIndex(File metaIndexFile) throws IOException {
    return new PersistentHashMap<Integer, Collection<String>>(metaIndexFile, new EnumeratorIntegerDescriptor(), new DataExternalizer<Collection<String>>() {
      @Override
      public void save(@NotNull DataOutput out, Collection<String> value) throws IOException {
        DataInputOutputUtil.writeINT(out, value.size());
        for (String key : value) {
          out.writeUTF(key);
        }
      }

      @Override
      public Collection<String> read(@NotNull DataInput in) throws IOException {
        final int size = DataInputOutputUtil.readINT(in);
        final List<String> list = new ArrayList<String>();
        for (int idx = 0; idx < size; idx++) {
          list.add(in.readUTF());
        }
        return list;
      }
    });
  }

  /*
  public void testStubIndexUnsavedDocumentsIndexing() throws IncorrectOperationException, IOException, StorageException {
    IdeaTestUtil.registerExtension(StubIndexExtension.EP_NAME, new TextStubIndexExtension(), getTestRootDisposable());
    IdeaTestUtil.registerExtension(StubIndexExtension.EP_NAME, new ClassNameStubIndexExtension(), getTestRootDisposable());
    FileTypeManager.getInstance().registerFileType(TestFileType.INSTANCE, "fff");
    final FFFLangParserDefinition parserDefinition = new FFFLangParserDefinition();
    LanguageParserDefinitions.INSTANCE.addExplicitExtension(FFFLanguage.INSTANCE, parserDefinition);

    final TestStubElementType stubType = new TestStubElementType();
    SerializationManager.getInstance().registerSerializer(TestStubElement.class, stubType);

    final File fffFile = new File(FileUtil.createTempDirectory("testing", "stubindex"), "MyClass.fff");
    fffFile.createNewFile();

    final VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(fffFile);

    assertNotNull(vFile);
    assertEquals(TestFileType.INSTANCE, vFile.getFileType());

    try {
      final MockPsiFile psiFile = new MockPsiFile(vFile, MockPsiManager.getInstance(myProject));

      final MockPsiClass cls = new MockPsiClass("com.company.MyClass");
      psiFile.add(cls);

      final MockPsiMethod aaaMethod = new MockPsiMethod("aaa");
      cls.addMethod(aaaMethod);
      final MockPsiMethod bbbMethod = new MockPsiMethod("bbb");
      cls.addMethod(bbbMethod);

      final PsiFileStubImpl fileStub = new PsiFileStubImpl(psiFile);
      final TestStubElement clsStub = stubType.createStub(cls, fileStub);
      stubType.createStub(aaaMethod, clsStub);
      stubType.createStub(bbbMethod, clsStub);

      final ByteArrayOutputStream arrayStream = new ByteArrayOutputStream();
      SerializationManager.getInstance().serialize(fileStub, new DataOutputStream(arrayStream));

      final FileBasedIndex fbi = FileBasedIndex.getInstance();
      final UpdatableIndex<Integer, SerializedStubTree, FileContent> stubUpdatingIndex = fbi.getIndex(StubUpdatingIndex.INDEX_ID);
      final MemoryIndexStorage storage = (MemoryIndexStorage)((MapReduceIndex)stubUpdatingIndex).getStorage();

      // initial
      final int fileId = FileBasedIndex.getFileId(vFile);
      final byte[] bytes = arrayStream.toByteArray();
      stubUpdatingIndex.update(fileId, new FileContent(vFile, bytes), null);

      final ValueContainer<SerializedStubTree> data = stubUpdatingIndex.getData(fileId);
      final List<SerializedStubTree> trees = data.toValueList();

      final SerializedStubTree tree = assertOneElement(trees);

      assertTrue(Comparing.equal(bytes, tree.getBytes()));

      final StubElement deserialized = tree.getStub();
    }
    finally {
      LanguageParserDefinitions.INSTANCE.removeExplicitExtension(FFFLanguage.INSTANCE, parserDefinition);
    }

  }
  */

  private static <T> void assertDataEquals(List<T> actual, T... expected) {
    assertTrue(new HashSet<T>(Arrays.asList(expected)).equals(new HashSet<T>(actual)));
  }

  public void testCollectedPsiWithChangedDocument() throws IOException {
    VirtualFile dir = getVirtualFile(createTempDirectory());
    PsiTestUtil.addSourceContentToRoots(myModule, dir);

    final VirtualFile vFile = createChildData(dir, "Foo.java");
    VfsUtil.saveText(vFile, "class Foo {}");

    final GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    assertNotNull(facade.findClass("Foo", scope));
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(vFile);
        assertNotNull(psiFile);

        Document document = FileDocumentManager.getInstance().getDocument(vFile);
        document.deleteString(0, document.getTextLength());
        assertNotNull(facade.findClass("Foo", scope));

        psiFile = null;
        PlatformTestUtil.tryGcSoftlyReachableObjects();
        assertNull(((PsiManagerEx)PsiManager.getInstance(getProject())).getFileManager().getCachedPsiFile(vFile));

        PsiClass foo = facade.findClass("Foo", scope);
        assertNotNull(foo);
        assertTrue(foo.isValid());
        assertEquals("class Foo {}", foo.getText());
        assertTrue(foo.isValid());

        PsiDocumentManager.getInstance(myProject).commitAllDocuments();
        assertNull(facade.findClass("Foo", scope));
      }
    });
  }

  public void testSavedUncommittedDocument() throws IOException {
    VirtualFile dir = getVirtualFile(createTempDirectory());
    PsiTestUtil.addSourceContentToRoots(myModule, dir);

    final VirtualFile vFile = createChildData(dir, "Foo.java");
    VfsUtil.saveText(vFile, "");

    final GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    assertNull(facade.findClass("Foo", scope));
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(vFile);
        assertNotNull(psiFile);

        long count = PsiManager.getInstance(myProject).getModificationTracker().getModificationCount();

        Document document = FileDocumentManager.getInstance().getDocument(vFile);
        document.insertString(0, "class Foo {}");
        FileDocumentManager.getInstance().saveDocument(document);

        assertTrue(count == PsiManager.getInstance(myProject).getModificationTracker().getModificationCount());
        assertNull(facade.findClass("Foo", scope));

        PsiDocumentManager.getInstance(myProject).commitAllDocuments();
        assertNotNull(facade.findClass("Foo", scope));
        assertNotNull(facade.findClass("Foo", scope).getText());
        // if Foo exists now, mod count should be different
        assertTrue(count != PsiManager.getInstance(myProject).getModificationTracker().getModificationCount());
      }
    });
  }

  public void testSkipUnknownFileTypes() throws IOException {
    VirtualFile dir = getVirtualFile(createTempDirectory());
    PsiTestUtil.addSourceContentToRoots(myModule, dir);

    final VirtualFile vFile = createChildData(dir, "Foo.test");
    VfsUtil.saveText(vFile, "Foo");
    assertEquals(UnknownFileType.INSTANCE, vFile.getFileType());
    assertEmpty(PsiSearchHelper.SERVICE.getInstance(myProject).findFilesWithPlainTextWords("Foo"));

    final Document document = FileDocumentManager.getInstance().getDocument(vFile);
    //todo should file type be changed silently without events?
    //assertEquals(UnknownFileType.INSTANCE, vFile.getFileType());

    final PsiFile file = getPsiFile(document);
    assertInstanceOf(file, PsiPlainTextFile.class);
    assertEquals("Foo", file.getText());

    assertEmpty(PsiSearchHelper.SERVICE.getInstance(myProject).findFilesWithPlainTextWords("Foo"));

    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        document.insertString(0, " ");
        assertEquals("Foo", file.getText());
        assertEmpty(PsiSearchHelper.SERVICE.getInstance(myProject).findFilesWithPlainTextWords("Foo"));

        FileDocumentManager.getInstance().saveDocument(document);
        assertEquals("Foo", file.getText());
        assertEmpty(PsiSearchHelper.SERVICE.getInstance(myProject).findFilesWithPlainTextWords("Foo"));

        PsiDocumentManager.getInstance(myProject).commitAllDocuments();
        assertEquals(" Foo", file.getText());
        assertEmpty(PsiSearchHelper.SERVICE.getInstance(myProject).findFilesWithPlainTextWords("Foo"));

      }
    });
  }

}
