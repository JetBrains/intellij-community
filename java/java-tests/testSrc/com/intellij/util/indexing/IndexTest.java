// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.find.ngrams.TrigramIndex;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.scratch.ScratchRootType;
import com.intellij.ide.scratch.ScratchesSearchScope;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.java.index.StringIndex;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.fileTypes.ExactFileNameMatcher;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.module.impl.scopes.ModuleWithDependentsScope;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.impl.FilePropertyPusher;
import com.intellij.openapi.roots.impl.JavaLanguageLevelPusher;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.cache.impl.id.IdIndex;
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.psi.impl.java.JavaFunctionalExpressionIndex;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.impl.search.JavaNullMethodArgumentIndex;
import com.intellij.psi.impl.source.*;
import com.intellij.psi.search.*;
import com.intellij.psi.search.impl.VirtualFileEnumeration;
import com.intellij.psi.stubs.*;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.testFramework.*;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.*;
import com.intellij.util.indexing.dependencies.IndexingRequestToken;
import com.intellij.util.indexing.dependencies.IsFileChangedResult;
import com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesService;
import com.intellij.util.indexing.dependencies.ScanningRequestToken;
import com.intellij.util.indexing.events.IndexedFilesListener;
import com.intellij.util.indexing.impl.IndexDebugProperties;
import com.intellij.util.indexing.impl.MapIndexStorage;
import com.intellij.util.indexing.impl.MapReduceIndex;
import com.intellij.util.indexing.impl.UpdatableValueContainer;
import com.intellij.util.indexing.impl.forward.IntForwardIndex;
import com.intellij.util.indexing.impl.storage.VfsAwareMapIndexStorage;
import com.intellij.util.indexing.impl.storage.VfsAwareMapReduceIndex;
import com.intellij.util.io.CaseInsensitiveEnumeratorStringDescriptor;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.PersistentMapImpl;
import com.intellij.util.ref.GCUtil;
import com.intellij.util.ref.GCWatcher;
import com.intellij.util.ui.UIUtil;
import com.intellij.workspaceModel.ide.impl.WorkspaceEntityLifecycleSupporterUtils;
import com.siyeh.ig.JavaOverridingMethodUtil;
import kotlin.Unit;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyLanguage;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SkipSlowTestLocally
public class IndexTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    if (getName().equals("test_indexed_state_for_file_without_content_requiring_indices")) {
      // should add file to test dire as soon as possible
      String otherRoot = myFixture.getTempDirPath() + "/otherRoot";
      assertTrue(new File(otherRoot).mkdirs());

      File exe = new File(otherRoot, "intellij.exe");
      assertTrue(exe.createNewFile());
      FileUtil.writeToFile(exe, new byte[]{1, 2, 3, 22});// convince IDEA it's binary
      moduleBuilder.addSourceContentRoot(otherRoot);
    }
  }

  public void testUpdate() throws StorageException, IOException {
    StringIndex index = createIndex(getTestName(false), new EnumeratorStringDescriptor(), false);

    try {
      // build index
      index.update("com/ppp/a.java", "a b c d");
      index.update("com/ppp/b.java", "a b g h");
      index.update("com/ppp/c.java", "a z f");
      index.update("com/ppp/d.java", "a a u y z");
      index.update("com/ppp/e.java", "a n chj e c d");

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
      index.update("com/ppp/d.java", "a u y z");
      assertDataEquals(index.getFilesByWord("a"), "com/ppp/a.java", "com/ppp/b.java", "com/ppp/c.java", "com/ppp/d.java", "com/ppp/e.java");
      index.update("com/ppp/d.java", "u y z");
      assertDataEquals(index.getFilesByWord("a"), "com/ppp/a.java", "com/ppp/b.java", "com/ppp/c.java", "com/ppp/e.java");
      index.update("com/ppp/d.java", "a a a u y z");
      assertDataEquals(index.getFilesByWord("a"), "com/ppp/a.java", "com/ppp/b.java", "com/ppp/c.java", "com/ppp/d.java", "com/ppp/e.java");

      index.update("com/ppp/e.java", "a n chj e c d z");
      assertDataEquals(index.getFilesByWord("z"), "com/ppp/c.java", "com/ppp/d.java", "com/ppp/e.java");

      index.update("com/ppp/b.java", null);
      assertDataEquals(index.getFilesByWord("a"), "com/ppp/a.java", "com/ppp/c.java", "com/ppp/d.java", "com/ppp/e.java");
      assertDataEquals(index.getFilesByWord("b"), "com/ppp/a.java");
      assertDataEquals(index.getFilesByWord("g"));
      assertDataEquals(index.getFilesByWord("h"));
    }
    finally {
      index.dispose();
    }
  }

  public void testUpdateWithCustomEqualityPolicy() throws IOException, StorageException {
    StringIndex index = createIndex(getTestName(false), new CaseInsensitiveEnumeratorStringDescriptor(), false);
    try {
      index.update("a.java", "x");
      assertDataEquals(index.getFilesByWord("x"), "a.java");
      assertDataEquals(index.getFilesByWord("X"), "a.java");

      index.update("b.java", "y");
      assertDataEquals(index.getFilesByWord("y"), "b.java");
      index.update("c.java", "Y");
      assertDataEquals(index.getFilesByWord("y"), "b.java", "c.java");
    }
    finally {
      index.dispose();
    }
  }

  private static StringIndex createIndex(String testName, EnumeratorStringDescriptor keyDescriptor, boolean readOnly) throws IOException {
    final File storageFile = FileUtil.createTempFile("index_test", "storage");
    final File metaIndexFile = FileUtil.createTempFile("index_test_inputs", "storage");
    final VfsAwareMapIndexStorage<String, String> indexStorage =
      new VfsAwareMapIndexStorage<>(storageFile.toPath(), keyDescriptor, new EnumeratorStringDescriptor(), 16 * 1024, readOnly);
    return new StringIndex(testName, indexStorage, metaIndexFile, !readOnly);
  }

  private static <T> void assertDataEquals(List<T> actual, T... expected) {
    assertSameElements(actual, expected);
  }

  public void testCollectedPsiWithChangedDocument() {
    final VirtualFile vFile = myFixture.addClass("class Foo {}").getContainingFile().getVirtualFile();

    assertNotNull(findClass("Foo"));
    PsiFile psiFile = getPsiManager().findFile(vFile);
    assertNotNull(psiFile);

    final Document document = FileDocumentManager.getInstance().getDocument(vFile);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.deleteString(0, document.getTextLength()));
    assertNotNull(findClass("Foo"));

    PsiClass foo = findClass("Foo");
    assertNotNull(foo);
    assertTrue(foo.isValid());
    assertEquals("class Foo {}", foo.getText());
    //noinspection ConstantValue
    assertTrue(foo.isValid());

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assertNull(findClass("Foo"));
  }

  public void testCollectedPsiWithDocumentChangedCommittedAndChangedAgain() {
    final VirtualFile vFile = myFixture.addClass("class Foo {}").getContainingFile().getVirtualFile();

    assertNotNull(findClass("Foo"));
    PsiFile psiFile = getPsiManager().findFile(vFile);
    assertNotNull(psiFile);

    final Document document = FileDocumentManager.getInstance().getDocument(vFile);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.deleteString(0, document.getTextLength()));
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(0, " "));

    PsiClass foo = findClass("Foo");
    assertNull(foo);
  }

  private PsiClass findClass(String name) {
    return JavaPsiFacade.getInstance(getProject()).findClass(name, GlobalSearchScope.allScope(getProject()));
  }

  public void testSavedUncommittedDocument() {
    final VirtualFile vFile = myFixture.addFileToProject("Foo.java", "").getVirtualFile();

    assertNull(findClass("Foo"));
    PsiFile psiFile = getPsiManager().findFile(vFile);
    assertNotNull(psiFile);

    long count = getPsiManager().getModificationTracker().getModificationCount();

    final Document document = FileDocumentManager.getInstance().getDocument(vFile);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(0, "class Foo {}"));
    FileDocumentManager.getInstance().saveDocument(document);

    assertEquals(count, getPsiManager().getModificationTracker().getModificationCount());
    assertNull(findClass("Foo"));

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assertNotNull(findClass("Foo"));
    assertNotNull(findClass("Foo").getText());
    // if Foo exists now, mod count should be different
    assertTrue(count != getPsiManager().getModificationTracker().getModificationCount());
  }

  public void testPersistentChangeAffectsDocument() throws IOException {
    final PsiFile psiFile = myFixture.addFileToProject("Foo.java", "class Foo {}");
    final VirtualFile vFile = psiFile.getVirtualFile();

    long stamp = FileBasedIndex.getInstance().getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, getProject());

    WriteCommandAction.runWriteCommandAction(getProject(),
                                             (Computable<PsiElement>)() -> CodeStyleManager.getInstance(getProject()).reformat(psiFile));

    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    WriteCommandAction.runWriteCommandAction(getProject(), () -> PsiManager.getInstance(getProject()).reloadFromDisk(psiFile));

    assertEquals(stamp, FileBasedIndex.getInstance().getIndexModificationStamp(StubUpdatingIndex.INDEX_ID,
                                                                               getProject()));

    FileContentUtilCore.reparseFiles(Collections.singletonList(vFile));

    FileViewProvider provider = PsiManager.getInstance(getProject()).findViewProvider(vFile);
    StubTree stubTree = ((PsiFileImpl)provider.getPsi(provider.getBaseLanguage())).getGreenStubTree();

    WriteAction.run(() -> VfsUtil.saveText(vFile, "class Bar {}"));

    assertNotNull(findClass("Foo"));
  }

  public void testPersistentChangeAffectsUnsavedDocument() throws IOException {
    final PsiFile psiFile = myFixture.addFileToProject("Foo.java", "class Foo {}");
    final VirtualFile vFile = psiFile.getVirtualFile();

    final Document document = FileDocumentManager.getInstance().getDocument(vFile);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(0, "class f {}"));
    WriteCommandAction.runWriteCommandAction(getProject(), () -> PsiManager.getInstance(getProject()).reloadFromDisk(psiFile));
    assertNotNull(findClass("Foo"));
    WriteAction.run(() -> VfsUtil.saveText(vFile, "class x {}"));
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(0, "class a {}"));
    GCUtil.tryGcSoftlyReachableObjects();
    assertNotNull(findClass("Foo"));
  }

  public void testSkipUnknownFileTypes() {
    final VirtualFile vFile = myFixture.addFileToProject("Foo.test", "Foo").getVirtualFile();
    assertEquals(PlainTextFileType.INSTANCE, vFile.getFileType());
    final PsiSearchHelper helper = PsiSearchHelper.getInstance(getProject());
    assertOneElement(helper.findFilesWithPlainTextWords("Foo"));

    final Document document = FileDocumentManager.getInstance().getDocument(vFile);
    //todo should file type be changed silently without events?
    //assertEquals(UnknownFileType.INSTANCE, vFile.getFileType());

    final PsiFile file = PsiDocumentManager.getInstance(getProject()).getPsiFile(document);
    assertInstanceOf(file, PsiPlainTextFile.class);
    assertEquals("Foo", file.getText());

    assertOneElement(helper.findFilesWithPlainTextWords("Foo"));

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      document.insertString(0, " ");
      assertEquals("Foo", file.getText());
      assertOneElement(helper.findFilesWithPlainTextWords("Foo"));

      FileDocumentManager.getInstance().saveDocument(document);
      assertEquals("Foo", file.getText());
      assertOneElement(helper.findFilesWithPlainTextWords("Foo"));

      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      assertEquals(" Foo", file.getText());
      assertOneElement(helper.findFilesWithPlainTextWords("Foo"));
    });
  }

  public void testUndoToFileContentForUnsavedCommittedDocument() {
    final VirtualFile vFile = myFixture.addClass("class Foo {}").getContainingFile().getVirtualFile();
    ((VirtualFileSystemEntry)vFile).setModificationStamp(0);// as unchanged file

    final Document document = FileDocumentManager.getInstance().getDocument(vFile);
    assertNotNull(document);
    assertEquals(0, document.getModificationStamp());
    assertNotNull(findClass("Foo"));

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      document.insertString(0, "import Bar;\n");
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      assertNotNull(findClass("Foo"));
    });

    final UndoManager undoManager = UndoManager.getInstance(getProject());
    final FileEditor selectedEditor = FileEditorManager.getInstance(getProject()).openFile(vFile, false)[0];
    ((UndoManagerImpl)undoManager).setOverriddenEditorProvider(new CurrentEditorProvider() {
      @Override
      public FileEditor getCurrentEditor(@Nullable Project project) {
        return selectedEditor;
      }
    });

    assertTrue(undoManager.isUndoAvailable(selectedEditor));
    FileDocumentManager.getInstance().saveDocument(document);
    undoManager.undo(selectedEditor);

    assertNotNull(findClass("Foo"));
  }

  public void test_rename_unsaved_file() {
    final PsiFile psiFile = myFixture.addFileToProject("Foo.java", "class Foo {}");
    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());

    assertEquals(0, FileDocumentManager.getInstance().getUnsavedDocuments().length);

    WriteCommandAction.runWriteCommandAction(getProject(),
                                             (Computable<PsiElement>)() -> ((PsiJavaFile)psiFile).getImportList()
                                               .add(getElementFactory().createImportStatementOnDemand("java.io")));

    GCUtil.tryGcSoftlyReachableObjects();

    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", scope));

    assertNull(((FileManagerImpl)getPsiManager().getFileManager()).getCachedDirectory(psiFile.getVirtualFile().getParent()));
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      assertEquals(psiFile.setName("Foo1.java"), psiFile);
    });

    assertTrue(FileDocumentManager.getInstance().getUnsavedDocuments().length > 0);
    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", scope));
  }

  public void test_rename_dir_with_unsaved_file() throws IOException {
    final PsiFile psiFile = myFixture.addFileToProject("foo/Foo.java", "package pkg; class Foo {}");
    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());

    assertEquals(0, FileDocumentManager.getInstance().getUnsavedDocuments().length);

    WriteCommandAction.runWriteCommandAction(getProject(), (Computable<PsiElement>)() ->
      ((PsiJavaFile)psiFile).getImportList().add(getElementFactory().createImportStatementOnDemand("java.io")));

    GCUtil.tryGcSoftlyReachableObjects();

    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("pkg.Foo", scope));

    final VirtualFile dir = psiFile.getVirtualFile().getParent();
    assertNull(((FileManagerImpl)getPsiManager().getFileManager()).getCachedDirectory(dir));
    WriteAction.run(() -> dir.rename(this, "bar"));

    assertTrue(FileDocumentManager.getInstance().getUnsavedDocuments().length > 0);
    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("pkg.Foo", scope));
  }

  public void test_language_level_change() {
    final PsiFile psiFile = myFixture.addFileToProject("Foo.java", "class Foo {}");
    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());

    WriteCommandAction.runWriteCommandAction(getProject(),
                                             (Computable<PsiElement>)() -> psiFile.add(getElementFactory().createEnum("SomeEnum")));

    WriteCommandAction.runWriteCommandAction(getProject(),
                                             (Computable<PsiElement>)() -> CodeStyleManager.getInstance(getProject()).reformat(psiFile));
    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", scope));

    long stamp = FileBasedIndex.getInstance().getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, getProject());

    IdeaTestUtil.setModuleLanguageLevel(myFixture.getModule(), LanguageLevel.JDK_1_3);

    assertNotNull(((PsiJavaFile)psiFile).getImportList().getNode());

    assertTrue(stamp != FileBasedIndex.getInstance().getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, getProject()));
  }

  public void test_rename_file_with_indexed_associated_unsaved_document_don_t_lost_its_data() {
    LanguageLevel level = LanguageLevel.HIGHEST;
    IdeaTestUtil.setModuleLanguageLevel(myFixture.getModule(), level);
    PsiFile psiFile = myFixture.addFileToProject("Foo.java", "class Foo {}");

    VirtualFile file = psiFile.getVirtualFile();
    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());

    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    final FileViewProvider vp1 = PsiManager.getInstance(getProject()).findViewProvider(file);
    WriteCommandAction.runWriteCommandAction(getProject(), (Computable<PsiElement>)() -> {
      return CodeStyleManager.getInstance(getProject()).reformat(vp1.getPsi(vp1.getBaseLanguage()));
    });

    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    FileContentUtilCore.reparseFiles(Collections.singletonList(file));

    final FileViewProvider vp2 = PsiManager.getInstance(getProject()).findViewProvider(file);
    ((PsiFileImpl)vp2.getPsi(vp2.getBaseLanguage())).getGreenStubTree();

    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    FileContentUtilCore.reparseFiles(Collections.singletonList(file));

    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    IdeaTestUtil.setModuleLanguageLevel(myFixture.getModule(), level);

    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", scope));
  }

  public void test_language_level_change2() {
    final PsiFile psiFile = myFixture.addFileToProject("Foo.java", "class Foo {}");
    VirtualFile vFile = psiFile.getVirtualFile();
    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());

    WriteCommandAction.runWriteCommandAction(getProject(),
                                             (Computable<PsiElement>)() -> psiFile.add(getElementFactory().createEnum("SomeEnum")));

    WriteCommandAction.runWriteCommandAction(getProject(),
                                             (Computable<PsiElement>)() -> CodeStyleManager.getInstance(getProject()).reformat(psiFile));
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();

    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", scope));

    IdeaTestUtil.setModuleLanguageLevel(myFixture.getModule(), LanguageLevel.JDK_1_3);
    assertNotNull(((PsiJavaFile)getPsiManager().findFile(vFile)).getImportList().getNode());

    GCUtil.tryGcSoftlyReachableObjects();
    assertNotNull(((PsiJavaFile)getPsiManager().findFile(vFile)).getImportList().getNode());
  }

  public void test_unknown_file_type_in_stubs() throws IOException {
    final VirtualFile vFile = myFixture.addFileToProject("Foo.java", "").getVirtualFile();
    final Document document = FileDocumentManager.getInstance().getDocument(vFile);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText("class Foo {}"));
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assertNotNull(findClass("Foo"));

    WriteAction.run(() -> vFile.rename(null, "Foo1"));
    assertNull(findClass("Foo"));
  }

  public void test_uncommitted_saved_document_2() {
    final PsiFile file = myFixture.addFileToProject("a.java", "class Foo {}");
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      assertNotNull(findClass("Foo"));
      file.getViewProvider().getDocument().setText("");
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      file.getViewProvider().getDocument().setText("class Foo {}");
      FileDocumentManager.getInstance().saveAllDocuments();
      assertNull(findClass("Foo"));
    });
  }

  public void test_plain_text_file_type_in_stubs() throws IOException {
    final VirtualFile vFile = myFixture.addFileToProject("Foo.java", "class Bar {}").getVirtualFile();
    assertNotNull(findClass("Bar"));
    final Document document = FileDocumentManager.getInstance().getDocument(vFile);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText("class Foo {}"));
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assertNotNull(findClass("Foo"));
    assertNull(findClass("Bar"));

    WriteAction.run(() -> vFile.rename(null, "Foo1"));
    assertNull(findClass("Foo"));
    assertNull(findClass("Bar"));
  }

  public void test_changing_a_file_without_psi_makes_the_document_committed_and_updates_index() throws IOException {
    PsiFile psiFile = myFixture.addFileToProject("Foo.java", "class Foo {}");
    final VirtualFile vFile = psiFile.getVirtualFile();
    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());

    WriteCommandAction.runWriteCommandAction(getProject(),
                                             () -> FileDocumentManager.getInstance().getDocument(vFile)
                                               .setText("import zoo.Zoo; class Foo1 {}"));
    assertTrue(PsiDocumentManager.getInstance(getProject()).getUncommittedDocuments().length > 0);

    FileDocumentManager.getInstance().saveAllDocuments();
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    //noinspection GroovyUnusedAssignment
    psiFile = null;
    GCWatcher.tracking(getPsiManager().getFileManager().getCachedPsiFile(vFile))
      .ensureCollected(() -> UIUtil.dispatchAllInvocationEvents());
    assertNull(getPsiManager().getFileManager().getCachedPsiFile(vFile));

    WriteAction.run(() -> VfsUtil.saveText(vFile, "class Foo3 {}"));

    assertEquals(0, PsiDocumentManager.getInstance(getProject()).getUncommittedDocuments().length);

    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo3", scope));
  }

  public void test_rename_file_invalidates_indices_in_right_order() throws IOException {
    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());

    for (int i = 0; i < 100; i++) {
      final VirtualFile file =
        myFixture.addFileToProject("foo/Foo" + i + ".java", "package foo; class Foo" + i + " {}").getVirtualFile();
      assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("foo.Foo" + i, scope));
      String newName = "Bar" + i + ".java";
      WriteCommandAction.runWriteCommandAction(getProject(), (ThrowableComputable<?, IOException>)() -> {
        file.rename(this, newName);
        return null;
      });
      assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("foo.Foo" + i, scope));
    }
  }

  public void test_no_index_stamp_update_when_no_change() throws IOException {
    final VirtualFile vFile = myFixture.addClass("class Foo {}").getContainingFile().getVirtualFile();
    long stamp = FileBasedIndex.getInstance().getIndexModificationStamp(IdIndex.NAME, getProject());
    //assertIsIndexed(vFile) FIXME-ank: not indexed, because indexing changes file's modCounter

    WriteAction.run(() -> VfsUtil.saveText(vFile, "Foo class"));
    IndexingRequestToken indexingRequest =
      getProject().getService(ProjectIndexingDependenciesService.class).getLatestIndexingRequestToken();
    assertFalse(IndexingFlag.isFileIndexed(vFile, indexingRequest.getFileIndexingStamp(vFile)));
    assertEquals(stamp, FileBasedIndex.getInstance().getIndexModificationStamp(IdIndex.NAME, getProject()));
    assertIsIndexed(vFile);

    WriteAction.run(() -> VfsUtil.saveText(vFile, "class Foo2 {}"));
    assertTrue(stamp != FileBasedIndex.getInstance().getIndexModificationStamp(IdIndex.NAME, getProject()));

    final Document document = FileDocumentManager.getInstance().getDocument(vFile);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText("Foo2 class"));
    stamp = FileBasedIndex.getInstance().getIndexModificationStamp(IdIndex.NAME, getProject());
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText("class Foo2"));
    assertEquals(stamp, FileBasedIndex.getInstance().getIndexModificationStamp(IdIndex.NAME, getProject()));

    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText("Foo3 class"));
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assertTrue(stamp != FileBasedIndex.getInstance().getIndexModificationStamp(IdIndex.NAME, getProject()));

    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText("class Foo { Runnable r = ( ) -> {}; }"));
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    stamp = FileBasedIndex.getInstance().getIndexModificationStamp(JavaFunctionalExpressionIndex.INDEX_ID, getProject());
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText("class Foo { Runnable x = () -> { }; }"));
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assertEquals(stamp, FileBasedIndex.getInstance().getIndexModificationStamp(JavaFunctionalExpressionIndex.INDEX_ID, getProject()));
  }

  private void assertIsIndexed(VirtualFile vFile) {
    ScanningRequestToken indexingRequest = getProject().getService(ProjectIndexingDependenciesService.class).getReadOnlyTokenForTest();
    assertTrue(
      IndexingFlag.isFileIndexed(vFile, indexingRequest.getFileIndexingStamp(vFile)) || IndexingFlag.isIndexedFlagDisabled());
    assertThat(IndexingFlag.isFileChanged(vFile, indexingRequest.getFileIndexingStamp(vFile))).isEqualTo(IsFileChangedResult.NO);
  }

  public void test_no_index_stamp_update_when_no_change_2() throws IOException {
    @Language("JAVA") String text0 = """
                  class Main111 {
                      static void staticMethod(Object o) {
                        staticMethod(null);
                      }
                  }
      """;
    final VirtualFile vFile = myFixture.configureByText(JavaFileType.INSTANCE, text0).getVirtualFile();
    long stamp = FileBasedIndex.getInstance().getIndexModificationStamp(JavaNullMethodArgumentIndex.INDEX_ID, getProject());
    JavaNullMethodArgumentIndex.MethodCallData data = new JavaNullMethodArgumentIndex.MethodCallData("staticMethod", 0);
    Collection<VirtualFile> files = FileBasedIndex.getInstance()
      .getContainingFiles(JavaNullMethodArgumentIndex.INDEX_ID, data, GlobalSearchScope.projectScope(getProject()));
    assertEquals(1, files.size());
    assertEquals(files.iterator().next(), vFile);

    @Language("JAVA") final String text = """
                  class Main {
                      static void staticMethod(Object o) {
                        staticMethod(null);
                      }
                  }
      """;
    WriteAction.run(() -> VfsUtil.saveText(vFile, text));
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    assertEquals(stamp, (FileBasedIndex.getInstance()).getIndexModificationStamp(JavaNullMethodArgumentIndex.INDEX_ID, getProject()));
    files = FileBasedIndex.getInstance()
      .getContainingFiles(JavaNullMethodArgumentIndex.INDEX_ID, data, GlobalSearchScope.projectScope(getProject()));
    assertEquals(1, files.size());
    assertEquals(files.iterator().next(), vFile);
  }

  public void test_snapshot_index_in_memory_state_after_commit_of_unsaved_document() {
    final VirtualFile vFile = myFixture.addClass("class Foo {}").getContainingFile().getVirtualFile();
    IdIndexEntry classEntry = new IdIndexEntry("class", true);
    FileBasedIndex.ValueProcessor<Integer> findValueProcessor = (file, value) -> !file.equals(vFile);

    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(getProject());
    boolean result = FileBasedIndex.getInstance().processValues(IdIndex.NAME, classEntry, null, findValueProcessor, projectScope);
    assertFalse(result);

    final Document document = FileDocumentManager.getInstance().getDocument(vFile);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText(""));
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    result = FileBasedIndex.getInstance().processValues(IdIndex.NAME, classEntry, null, findValueProcessor, projectScope);
    assertTrue(result);
  }

  public void test_no_stub_index_stamp_update_when_no_change() throws IOException {
    final VirtualFile vFile = myFixture.addClass("class Foo {}").getContainingFile().getVirtualFile();
    long stamp = ((StubIndexImpl)StubIndex.getInstance()).getIndexModificationStamp(JavaStubIndexKeys.CLASS_SHORT_NAMES, getProject());

    WriteAction.run(() -> VfsUtil.saveText(vFile, "class Foo { int foo; }"));
    assertEquals(stamp,
                 ((StubIndexImpl)StubIndex.getInstance()).getIndexModificationStamp(JavaStubIndexKeys.CLASS_SHORT_NAMES, getProject()));

    WriteAction.run(() -> VfsUtil.saveText(vFile, "class Foo2 { }"));
    assertTrue(
      stamp != ((StubIndexImpl)StubIndex.getInstance()).getIndexModificationStamp(JavaStubIndexKeys.CLASS_SHORT_NAMES, getProject()));

    final Document document = FileDocumentManager.getInstance().getDocument(vFile);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText("class Foo3 {}"));
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    stamp = ((StubIndexImpl)StubIndex.getInstance()).getIndexModificationStamp(JavaStubIndexKeys.CLASS_SHORT_NAMES, getProject());

    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText("class Foo3 { int foo; }"));
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assertEquals(stamp,
                 ((StubIndexImpl)StubIndex.getInstance()).getIndexModificationStamp(JavaStubIndexKeys.CLASS_SHORT_NAMES, getProject()));

    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText("class Foo2 { }"));
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assertTrue(
      stamp != ((StubIndexImpl)StubIndex.getInstance()).getIndexModificationStamp(JavaStubIndexKeys.CLASS_SHORT_NAMES, getProject()));
  }

  public void test_internalErrorOfStubProcessingInvalidatesIndex() {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());

    final VirtualFile vFile = myFixture.addClass("class Foo {}").getContainingFile().getVirtualFile();

    Ref<PsiClass> clazz = Ref.create(findClass("Foo"));
    assertNotNull(clazz.get());

    runFindClassStubIndexQueryThatProducesInvalidResult("Foo");

    GCWatcher.fromClearedRef(clazz).ensureCollected();

    assertNotNull(findClass("Foo"));

    // check invalidation of transient indices state
    final Document document = FileDocumentManager.getInstance().getDocument(vFile);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText("class Foo2 {}"));
    PsiDocumentManager.getInstance(getProject()).commitDocument(document);

    clazz = Ref.create(findClass("Foo2"));
    assertNotNull(clazz.get());

    runFindClassStubIndexQueryThatProducesInvalidResult("Foo2");

    GCWatcher.fromClearedRef(clazz).ensureCollected();

    assertNotNull(findClass("Foo2"));
  }

  private void runFindClassStubIndexQueryThatProducesInvalidResult(final String qName) {
    final GlobalSearchScope searchScope = GlobalSearchScope.allScope(getProject());

    class StubMismatchLikeException extends RuntimeException {
    }

    final Processor<PsiClass> processor = file -> {
      throw new StubMismatchLikeException();
    };

    try {
      StubIndex.getInstance()
        .processElements(JavaStubIndexKeys.CLASS_FQN, qName, getProject(), searchScope, PsiClass.class, aClass -> {
          StubIndex.getInstance()
            .processElements(JavaStubIndexKeys.CLASS_FQN, qName, getProject(), searchScope, PsiClass.class, processor);
          return false;
        });
      fail("Should fail with StubMismatchLikeException");
    }
    catch (StubMismatchLikeException ignored) {
      // expected
    }
  }

  public void test_do_not_collect_stub_tree_while_holding_stub_elements() {
    final VirtualFile vFile = myFixture.addClass("class Foo {}").getContainingFile().getVirtualFile();

    PsiFileWithStubSupport psiFile = (PsiFileWithStubSupport)getPsiManager().findFile(vFile);
    assertNotNull(psiFile);

    PsiClass clazz = findClass("Foo");
    assertNotNull(clazz);
    int stubTreeHash = psiFile.getStubTree().hashCode();

    GCUtil.tryGcSoftlyReachableObjects();
    StubTree stubTree = psiFile.getStubTree();
    assertNotNull(stubTree);
    assertEquals(stubTreeHash, stubTree.hashCode());
  }

  public void test_report_using_index_from_other_index() {
    final String className = "Foo";
    final VirtualFile vfile = myFixture.addClass("class " + className + " { void bar() {} }").getContainingFile().getVirtualFile();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    final List<Boolean> foundClass = new ArrayList<>(List.of(false));
    final List<Boolean> foundMethod = new ArrayList<>(List.of(false));

    StubIndex.getInstance()
      .processElements(JavaStubIndexKeys.CLASS_SHORT_NAMES, className, getProject(), scope, PsiClass.class, aClass -> {
        foundClass.set(0, true);
        StubIndex.getInstance()
          .processElements(JavaStubIndexKeys.METHODS, "bar", getProject(), scope, PsiMethod.class, method -> {
            foundMethod.set(0, true);
            return true;
          });
        return true;
      });


    assertTrue(foundClass.get(0));
    assertTrue(foundMethod.get(0));// allow access stub index processing other index

    final List<Boolean> foundClassProcessAll = new ArrayList<>(List.of(false));
    final List<Boolean> foundClassStub = new ArrayList<>(List.of(false));

    StubIndex.getInstance().processAllKeys(JavaStubIndexKeys.CLASS_SHORT_NAMES, getProject(), aClass -> {
      if (!className.equals(aClass)) return true;
      foundClassProcessAll.set(0, true);
      StubIndex.getInstance()
        .processElements(JavaStubIndexKeys.CLASS_SHORT_NAMES, aClass, getProject(), scope, PsiClass.class, clazz -> {
          foundClassStub.set(0, true);
          return true;
        });
      return true;
    });

    assertTrue(foundClassProcessAll.get(0));
    assertTrue(foundClassStub.get(0));

    final List<Boolean> foundId = new ArrayList<>(List.of(false));
    final List<Boolean> foundStub = new ArrayList<>(List.of(false));

    FileBasedIndex.getInstance()
      .processValues(IdIndex.NAME, new IdIndexEntry("Foo", true), null, (file, value) -> {
        foundId.set(0, true);
        FileBasedIndex.getInstance()
          .processValues(StubUpdatingIndex.INDEX_ID, assertInstanceOf(vfile, VirtualFileWithId.class).getId(), null,
                         (file2, value2) -> {
                           foundStub.set(0, true);
                           return true;
                         }, scope);
        return true;
      }, scope);


    assertTrue(foundId.get(0));
    assertTrue(foundStub.get(0));
  }

  public void testNullProjectScope() throws Throwable {
    final GlobalSearchScope allScope = new EverythingGlobalScope();
    // create file to be indexed
    final VirtualFile testFile = myFixture.addFileToProject("test.txt", "test").getVirtualFile();
    UsefulTestCase.assertNoException(IllegalArgumentException.class, (ThrowableRunnable<Throwable>)() -> {
      //force to index new file with null project scope
      FileBasedIndex.getInstance().ensureUpToDate(IdIndex.NAME, getProject(), allScope);
    });
    assertNotNull(testFile);
  }

  public static class RecordingVfsListener extends IndexedFilesListener {
    @Override
    protected void iterateIndexableFiles(@NotNull VirtualFile file, @NotNull ContentIterator iterator) {
      VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor<Void>() {
        @Override
        public boolean visitFile(@NotNull VirtualFile visitedFile) {
          iterator.processFile(visitedFile);
          return true;
        }
      });
    }

    public String indexingOperation(VirtualFile file) {
      Ref<String> operation = new Ref<>();
      getEventMerger().processChanges(info -> {
        operation.set(info.toString());
        return true;
      });

      return StringUtil.replace(operation.get(), file.getPath(), file.getName());
    }
  }

  public void testIndexedFilesListener() throws Throwable {
    RecordingVfsListener listener = new RecordingVfsListener();

    VirtualFileManager.getInstance().addAsyncFileListener(listener, myFixture.getTestRootDisposable());

    final VirtualFile testFile = myFixture.addFileToProject("test.txt", "test").getVirtualFile();
    final int testFileId = ((VirtualFileWithId)testFile).getId();

    assertEquals(("file: " + testFileId + "; operation: CONTENT_CHANGE ADD"), listener.indexingOperation(testFile));

    FileContentUtilCore.reparseFiles(Collections.singletonList(testFile));

    assertEquals(("file: " + testFileId + "; operation: ADD"), listener.indexingOperation(testFile));

    WriteAction.run(() -> VfsUtil.saveText(testFile, "foo"));
    WriteAction.run(() -> VfsUtil.saveText(testFile, "bar"));

    assertEquals(("file: " + testFileId + "; operation: CONTENT_CHANGE"), listener.indexingOperation(testFile));

    WriteAction.run(() -> VfsUtil.saveText(testFile, "baz"));
    WriteAction.run(() -> testFile.delete(null));

    assertEquals(("file: " + testFileId + "; operation: REMOVE"), listener.indexingOperation(testFile));
  }

  public void test_files_inside_copied_directory_are_indexed() throws IOException {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());

    final PsiFile srcFile = myFixture.addFileToProject("foo/bar/A.java", "class A {}");
    assertNotNull(facade.findClass("A", GlobalSearchScope.moduleScope(getModule())));

    final VirtualFile anotherDir = myFixture.getTempDirFixture().findOrCreateDir("another");
    Module anotherModule = PsiTestUtil.addModule(getProject(), StdModuleTypes.JAVA, "another", anotherDir);
    assertNull(facade.findClass("A", GlobalSearchScope.moduleScope(anotherModule)));

    WriteAction.run(() -> srcFile.getVirtualFile().getParent().copy(this, anotherDir, "doo"));

    assertNotNull(facade.findClass("A", GlobalSearchScope.moduleScope(anotherModule)));
    assertTrue(JavaFileElementType.isInSourceContent(myFixture.getTempDirFixture().getFile("another/doo/A.java")));
  }

  public void test_requesting_nonexisted_index_fails_as_expected() {
    ID<Object, Object> myId = ID.create("my.id");
    try {
      FileBasedIndex.getInstance().getContainingFiles(myId, "null", GlobalSearchScope.allScope(getProject()));
      FileBasedIndex.getInstance().processAllKeys(myId, CommonProcessors.alwaysTrue(), getProject());
      fail();
    }
    catch (IllegalStateException ignored) {
    }
  }

  public void test_read_only_index_access() throws IOException {
    StringIndex index = createIndex(getTestName(false), new EnumeratorStringDescriptor(), true);

    try {
      IndexDebugProperties.IS_UNIT_TEST_MODE = false;
      assertFalse(index.update("qwe/asd", "some_string"));
      Throwable rebuildThrowable = index.getRebuildThrowable();
      assertInstanceOf(rebuildThrowable, StorageException.class);
      Throwable rebuildCause = rebuildThrowable.getCause();
      assertInstanceOf(rebuildCause, IncorrectOperationException.class);
    }
    finally {
      IndexDebugProperties.IS_UNIT_TEST_MODE = true;
      index.dispose();
    }
  }

  public void test_commit_without_reparse_properly_changes_index() {
    PsiFile srcFile = myFixture.addFileToProject("A.java", "class A {}");
    assertNotNull(findClass("A"));

    final Document document = FileDocumentManager.getInstance().getDocument(srcFile.getVirtualFile());
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.replaceString(0, document.getTextLength(), "class B {}"));
    assertNotNull(findClass("A"));

    PsiDocumentManager.getInstance(getProject()).commitDocument(document);

    assertNull(findClass("A"));
    assertNotNull(findClass("B"));

    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.replaceString(0, document.getTextLength(), "class C {}"));
    assertNotNull(findClass("B"));

    FileDocumentManager.getInstance().saveDocument(document);
    PsiDocumentManager.getInstance(getProject()).commitDocument(document);

    assertNull(findClass("B"));
    assertNotNull(findClass("C"));
  }

  public void test_reload_from_disk_after_adding_import() {
    final PsiJavaFile file = (PsiJavaFile)myFixture.addFileToProject("Foo.java", "class Foo {}");
    WriteCommandAction.runWriteCommandAction(getProject(),
                                             (Computable<PsiElement>)() -> file.getImportList().add(
                                               JavaPsiFacade.getElementFactory(getProject()).createImportStatementOnDemand("java.util")));
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();

    WriteCommandAction.runWriteCommandAction(getProject(), () -> getPsiManager().reloadFromDisk(file));

    assertNotNull(findClass("Foo"));
  }

  public void test_read_only_index_has_read_only_storages() throws IOException {
    MapReduceIndex<String, String, ?> index = createIndex(getTestName(false), new EnumeratorStringDescriptor(), true).getIndex();

    try {
      MapIndexStorage<String, String> storage = assertInstanceOf(index.getStorage(), MapIndexStorage.class);
      PersistentMapImpl<String, UpdatableValueContainer<String>> map =
        assertInstanceOf(storage.getIndexMap(), PersistentMapImpl.class);
      assertTrue(map.getReadOnly());
      assertTrue(map.getValueStorage().isReadOnly());
    }
    finally {
      index.dispose();
    }
  }

  public void test_Vfs_Event_Processing_Performance() {
    final String filename = "A.java";
    myFixture.addFileToProject("foo/bar/" + filename, "class A {}");

    Benchmark.newBenchmark("Vfs Event Processing By Index", () -> {
      PsiFile[] files = FilenameIndex.getFilesByName(getProject(), filename, GlobalSearchScope.moduleScope(getModule()));
      assertEquals(1, files.length);

      VirtualFile file = files[0].getVirtualFile();

      String filename2 = "B.java";
      int max = 100000;
      List<VFileEvent> eventList = new ArrayList<>(max);
      int len = max / 2;

      for (int i = 0; i < len; ++i) {
        eventList.add(new VFilePropertyChangeEvent(null, file, VirtualFile.PROP_NAME, filename, filename2));
        eventList.add(new VFilePropertyChangeEvent(null, file, VirtualFile.PROP_NAME, filename2, filename));
        eventList.add(new VFileDeleteEvent(null, file));
        eventList.add(new VFileCreateEvent(null, file.getParent(), filename, false, null, null, null));
      }


      AsyncFileListener.ChangeApplier applier =
        ((FileBasedIndexImpl)FileBasedIndex.getInstance()).getChangedFilesCollector().prepareChange(eventList);
      applier.beforeVfsChange();
      applier.afterVfsChange();

      files = FilenameIndex.getFilesByName(getProject(), filename, GlobalSearchScope.moduleScope(getModule()));
      assertEquals(1, files.length);
    }).start();
  }

  public void test_class_file_in_src_content_isn_t_returned_from_index() throws IOException {
    PsiClass runnable =
      JavaPsiFacade.getInstance(getProject()).findClass(Runnable.class.getName(), GlobalSearchScope.allScope(getProject()));
    final PsiClass thread =
      JavaPsiFacade.getInstance(getProject()).findClass(Thread.class.getName(), GlobalSearchScope.allScope(getProject()));
    final VirtualFile srcRoot = myFixture.getTempDirFixture().getFile("");
    WriteCommandAction.runWriteCommandAction(getProject(),
                                             (ThrowableComputable<VirtualFile, IOException>)() -> VfsUtil.copy(this,
                                                                                                               thread.getContainingFile()
                                                                                                                 .getVirtualFile(),
                                                                                                               srcRoot));

    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(getProject());
    assertTrue(JavaOverridingMethodUtil.getOverridingMethodsIfCheapEnough(runnable.getMethods()[0], projectScope,
                                                                          unused -> true).findFirst().isEmpty());
    assertTrue(StubIndex.getElements(JavaStubIndexKeys.METHODS, "run", getProject(), projectScope, PsiMethod.class).isEmpty());
  }

  public void test_text_todo_indexing_checks_for_cancellation() {
    TodoPattern pattern = new TodoPattern("(x+x+)+y", TodoAttributesUtil.createDefault(), true);

    TodoPattern[] oldPatterns = TodoConfiguration.getInstance().getTodoPatterns();
    TodoPattern[] newPatterns = new TodoPattern[]{pattern};
    TodoConfiguration.getInstance().setTodoPatterns(newPatterns);
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    FileBasedIndex.getInstance().ensureUpToDate(IdIndex.NAME, getProject(), GlobalSearchScope.allScope(getProject()));
    myFixture.addFileToProject("Foo.txt", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");

    try {
      final CountDownLatch progressStarted = new CountDownLatch(1);
      final ProgressIndicatorBase progressIndicatorBase = new ProgressIndicatorBase();
      final AtomicBoolean canceled = new AtomicBoolean(false);
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        try {
          progressStarted.await();
          TimeoutUtil.sleep(1000);
          progressIndicatorBase.cancel();
          TimeoutUtil.sleep(500);
          assertTrue(canceled.get());
        } catch (Exception e) {
          throw new AssertionError("Should not throw exceptions", e);
        }
      });
      ProgressManager.getInstance().runProcess(() ->  {
          try {
            progressStarted.countDown();
            FileBasedIndex.getInstance().ensureUpToDate(IdIndex.NAME, getProject(), GlobalSearchScope.allScope(getProject()));
          }
          catch (ProcessCanceledException ignore) {
            canceled.set(true);
          }
        }, progressIndicatorBase
      );
    }
    finally {
      TodoConfiguration.getInstance().setTodoPatterns(oldPatterns);
    }
  }

  public void test_stub_updating_index_problem_during_processAllKeys() {
    final String className = "Foo";
    myFixture.addClass("class " + className + " {}");
    final GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());

    final List<Boolean> foundClassProcessAll = new ArrayList<>(List.of(false));
    final List<Boolean> foundClassStub = new ArrayList<>(List.of(false));

    StubIndex.getInstance().processAllKeys(JavaStubIndexKeys.CLASS_SHORT_NAMES, getProject(), aClass -> {
      if (!className.equals(aClass)) return true;
      foundClassProcessAll.set(0, true);
      // adding file will add file to index's dirty set, but it should not be processed within current read action
      myFixture.addFileToProject("Bar.java", "class Bar { }");
      StubIndex.getInstance()
        .processElements(JavaStubIndexKeys.CLASS_SHORT_NAMES, aClass, getProject(), scope, PsiClass.class, clazz -> {
          foundClassStub.set(0, true);
          return true;
        });
      return true;
    });

    assertTrue(foundClassProcessAll.get(0));
    assertTrue(foundClassStub.get(0));// allow access stub index processing other index
  }

  public void test_document_increases_beyond_too_large_limit() {
    final String item = createLongSequenceOfCharacterConstants();
    final String fileText = "class Bar { char[] item = { " + item + "};\n }";
    VirtualFile file = myFixture.addFileToProject("foo/Bar.java", fileText).getVirtualFile();
    assertNotNull(findClass("Bar"));

    final Document document = FileDocumentManager.getInstance().getDocument(file);

    for (int i = 0; i < 2; ++i) {
      WriteCommandAction.runWriteCommandAction(getProject(), () -> document.replaceString(0, document.getTextLength(), item + item));
      PsiDocumentManager.getInstance(getProject()).commitDocument(document);
      assertNull(findClass("Bar"));

      WriteCommandAction.runWriteCommandAction(getProject(), () -> document.replaceString(0, document.getTextLength(), fileText));
      PsiDocumentManager.getInstance(getProject()).commitDocument(document);
      assertNotNull(findClass("Bar"));
    }
  }

  private static String createLongSequenceOfCharacterConstants() {
    String item = "'c',";
    return item.repeat(Integer.highestOneBit(FileUtilRt.getUserFileSizeLimit()) / item.length());
  }

  public void test_file_increases_beyond_too_large_limit() throws IOException {
    FileBasedIndexImpl fileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
    final String item = createLongSequenceOfCharacterConstants();
    final String fileText = "class Bar { char[] item = { " + item + "};\n }";
    final VirtualFile file = myFixture.addFileToProject("foo/Bar.java", fileText).getVirtualFile();
    int fileId = ((VirtualFileWithId)file).getId();
    assertNotNull(findClass("Bar"));
    assertNotNull(fileBasedIndex.getIndexableFilesFilterHolder().findProjectForFile(fileId));

    for (int i = 0; i < 2; ++i) {
      WriteAction.run(() -> VfsUtil.saveText(file, "class Bar { char[] item = { " + item + item + "};\n }"));
      assertNull(findClass("Bar"));
      assertNull(fileBasedIndex.getIndexableFilesFilterHolder().findProjectForFile(fileId));

      WriteAction.run(() -> VfsUtil.saveText(file, fileText));
      assertNotNull(findClass("Bar"));
      assertNotNull(fileBasedIndex.getIndexableFilesFilterHolder().findProjectForFile(fileId));
    }
  }

  public void test_indexed_state_for_file_without_content_requiring_indices() throws IOException {
    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    FileBasedIndex.getInstance().ensureUpToDate(FileTypeIndex.NAME, getProject(), scope);

    PsiFile[] files = FilenameIndex.getFilesByName(getProject(), "intellij.exe", scope);
    final VirtualFile file = assertOneElement(files).getVirtualFile();
    assertIsIndexed(file);

    WriteCommandAction.runWriteCommandAction(getProject(), (ThrowableComputable<?, IOException>)() -> {
      file.rename(this, "intellij2.exe");
      return null;
    });
    FileBasedIndex.getInstance().ensureUpToDate(FileTypeIndex.NAME, getProject(), scope);
    assertIsIndexed(file);
  }

  public void test_IDEA_188028() throws IOException {
    final PsiJavaFileImpl file = (PsiJavaFileImpl)myFixture.addFileToProject("a.java", "class Foo {}");
    WriteCommandAction.runWriteCommandAction(getProject(), (ThrowableComputable<?, IOException>)() -> {
      Document document = file.getViewProvider().getDocument();
      document.setText("");
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      PsiManager.getInstance(getProject()).reloadFromDisk(file);
      document.setText("");
      assertNull(findClass("Foo"));
      file.getVirtualFile().rename(this, "a1.java");
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      assertNull(findClass("Foo"));
      return null;
    });
  }

  public void test_every_directory_and_file_are_marked_as_indexed_in_open_project() {
    VirtualFile foo = myFixture.addFileToProject("src/main/a.java", "class Foo {}").getVirtualFile();
    VirtualFile main = foo.getParent();
    VirtualFile src = main.getParent();

    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    assertEquals(foo, assertOneElement(FilenameIndex.getVirtualFilesByName("a.java", scope)));
    assertEquals(main, assertOneElement(FilenameIndex.getVirtualFilesByName("main", scope)));
    assertEquals(src, assertOneElement(FilenameIndex.getVirtualFilesByName("src", scope)));

    // content-less indexes has been passed
    // now all directories are indexed


    IndexingRequestToken indexingRequest =
      getProject().getService(ProjectIndexingDependenciesService.class).getLatestIndexingRequestToken();
    assertFalse(IndexingFlag.isFileIndexed(foo, indexingRequest.getFileIndexingStamp(foo)));
    assertIsIndexed(main);
    assertIsIndexed(src);

    assertNotNull(findClass("Foo"));

    //assertIsIndexed(foo) // FIXME-ank: not indexed, because indexing changes file's modCounter
  }

  public void test_stub_updating_index_stamp() throws IOException {
    final VirtualFile vFile = myFixture.addClass("class Foo {}").getContainingFile().getVirtualFile();
    long stamp = FileBasedIndex.getInstance().getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, getProject());
    WriteAction.run(() -> VfsUtil.saveText(vFile, "class Foo { void m() {} }"));
    assertTrue(stamp != FileBasedIndex.getInstance().getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, getProject()));
    stamp = FileBasedIndex.getInstance().getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, getProject());
    WriteAction.run(() -> VfsUtil.saveText(vFile, "class Foo { void m() { int k = 0; } }"));
    assertEquals(stamp, FileBasedIndex.getInstance().getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, getProject()));
  }

  public void test_index_stamp_update_on_transient_data_deletion() {
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      long stamp = FileBasedIndex.getInstance().getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, getProject());
      PsiFile file = myFixture.addClass("class Foo {}").getContainingFile();

      ((PsiJavaFile)file).getImportList().add(JavaPsiFacade.getElementFactory(getProject()).createImportStatementOnDemand("java.io"));
      assertNotNull(findClass("Foo"));
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      assertTrue(stamp != FileBasedIndex.getInstance().getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, getProject()));
      stamp = FileBasedIndex.getInstance().getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, getProject());

      assertNotNull(findClass("Foo"));
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      assertEquals(stamp, FileBasedIndex.getInstance().getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, getProject()));
      stamp = FileBasedIndex.getInstance().getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, getProject());

      PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
      PsiManager.getInstance(getProject()).reloadFromDisk(file);
      assertTrue(stamp != FileBasedIndex.getInstance().getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, getProject()));

      assertNotNull(findClass("Foo"));
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      assertTrue(stamp != FileBasedIndex.getInstance().getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, getProject()));
      stamp = FileBasedIndex.getInstance().getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, getProject());

      findClass("Foo").replace(findClass("Foo").copy());
      assertNotNull(findClass("Foo"));
      assertEquals(stamp, FileBasedIndex.getInstance().getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, getProject()));
    });
  }

  public void test_non_empty_memory_storage_cleanup_advances_index_modification_stamp() {
    long stamp = FileBasedIndex.getInstance().getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, getProject());
    final PsiFile file = myFixture.addClass("class Foo {}").getContainingFile();
    assertNotNull(findClass("Foo"));
    assertTrue(stamp != FileBasedIndex.getInstance().getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, getProject()));
    stamp = FileBasedIndex.getInstance().getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, getProject());

    WriteCommandAction.runWriteCommandAction(getProject(), (Computable<PsiElement>)() -> ((PsiJavaFile)file).getImportList()
      .add(JavaPsiFacade.getElementFactory(getProject()).createImportStatementOnDemand("java.io")));
    assertNotNull(findClass("Foo"));
    assertTrue(stamp != FileBasedIndex.getInstance().getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, getProject()));
    stamp = FileBasedIndex.getInstance().getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, getProject());

    WriteCommandAction.runWriteCommandAction(getProject(),
                                             () -> ((FileDocumentManagerImpl)FileDocumentManager.getInstance()).dropAllUnsavedDocuments());
    assertNotNull(findClass("Foo"));
    assertTrue(stamp != FileBasedIndex.getInstance().getIndexModificationStamp(StubUpdatingIndex.INDEX_ID, getProject()));
  }

  public void test_index_clear_increments_modification_stamp() throws IOException {
    StringIndex index = createIndex(getTestName(false), new EnumeratorStringDescriptor(), false);
    try {
      long stamp = index.getModificationStamp();
      index.clear();
      assertTrue(stamp != index.getModificationStamp());
    }
    finally {
      index.dispose();
    }
  }

  public void test_unsaved_document_is_still_indexed_on_dumb_mode_ignoring_access() {
    final PsiJavaFile file = (PsiJavaFile)myFixture.addFileToProject("Foo.java", "class Foo {}");
    final PsiIdentifier nameIdentifier = file.getClasses()[0].getNameIdentifier();

    final Project project = getProject();
    final VirtualFile virtualFile = file.getVirtualFile();

    assertTrue(findWordInDumbMode("Foo", virtualFile, false));
    assertFalse(findWordInDumbMode("Bar", virtualFile, false));

    DumbModeTestUtils.runInDumbModeSynchronously(project, () -> {
      assertTrue(findWordInDumbMode("Foo", virtualFile, true));
      assertFalse(findWordInDumbMode("Bar", virtualFile, true));

      WriteCommandAction.runWriteCommandAction(project,
                                               (Computable<PsiElement>)() -> nameIdentifier.replace(
                                                 JavaPsiFacade.getElementFactory(project).createIdentifier("Bar")));
      assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(PsiDocumentManager.getInstance(project).getDocument(file)));

      assertTrue(findWordInDumbMode("Bar", virtualFile, true));
      assertFalse(findWordInDumbMode("Foo", virtualFile, true));
    });

    assertTrue(findWordInDumbMode("Bar", virtualFile, false));
    assertFalse(findWordInDumbMode("Foo", virtualFile, false));
  }

  public void test_change_file_type_association_from_groovy_to_java() {
    @Language("JAVA") String text = "class Foo { void m() {" + " String x = 'qwerty';" + "}}";
    PsiFile file = myFixture.addFileToProject("Foo.groovy", text);
    VirtualFile virtualFile = file.getVirtualFile();

    Map<IdIndexEntry, Integer> idIndexData = getIdIndexData(virtualFile);
    assertTrue(idIndexData.containsKey(new IdIndexEntry("Foo", false)));
    assertTrue(idIndexData.containsKey(new IdIndexEntry("qwerty", false)));
    assertEquals(UsageSearchContext.IN_STRINGS | UsageSearchContext.IN_CODE, idIndexData.get(new IdIndexEntry("qwerty", false)).intValue());
    assertEquals(GroovyFileType.GROOVY_FILE_TYPE, FileTypeIndex.getIndexedFileType(virtualFile, getProject()));
    ObjectStubTree<?> stub = StubTreeLoader.getInstance().readFromVFile(getProject(), virtualFile);
    assertStubLanguage(GroovyLanguage.INSTANCE, stub);
    assertEquals(GroovyLanguage.INSTANCE, file.getLanguage());
    assertNotNull(findClass("Foo"));
    final ExactFileNameMatcher matcher = new ExactFileNameMatcher("Foo.groovy");
    try {
      WriteCommandAction.runWriteCommandAction(getProject(), () -> FileTypeManager.getInstance().associate(JavaFileType.INSTANCE, matcher));
      IndexingTestUtil.waitUntilIndexesAreReady(getProject());

      assertEquals(JavaFileType.INSTANCE, FileTypeIndex.getIndexedFileType(virtualFile, getProject()));
      stub = StubTreeLoader.getInstance().readFromVFile(getProject(), virtualFile);
      assertStubLanguage(JavaLanguage.INSTANCE, stub);
      idIndexData = getIdIndexData(virtualFile);
      assertTrue(idIndexData.containsKey(new IdIndexEntry("Foo", false)));
      assertFalse(idIndexData.containsKey(new IdIndexEntry("qwerty", false)));
      PsiClass javaFooClass = findClass("Foo");
      assertEquals(JavaLanguage.INSTANCE, javaFooClass.getLanguage());
    }
    finally {
      WriteCommandAction.runWriteCommandAction(getProject(),
                                               () -> FileTypeManager.getInstance().removeAssociation(JavaFileType.INSTANCE, matcher));
    }
  }

  public void _test_composite_index_with_snapshot_mappings_hash_id() throws IOException {
    int groovyFileId = ((VirtualFileWithId)myFixture.addFileToProject("Foo.groovy", "class Foo {}").getVirtualFile()).getId();
    int javaFileId = ((VirtualFileWithId)myFixture.addFileToProject("Foo.java", "class Foo {}").getVirtualFile()).getId();

    FileBasedIndex fbi = FileBasedIndex.getInstance();
    fbi.ensureUpToDate(IdIndex.NAME, getProject(), GlobalSearchScope.allScope(getProject()));
    UpdatableIndex<IdIndexEntry, Integer, FileContent, ?> idIndex =
      ((FileBasedIndexImpl)fbi).getIndex(IdIndex.NAME);

    IntForwardIndex idIndexForwardIndex = (IntForwardIndex)((VfsAwareMapReduceIndex<?, ?, ?>)idIndex).getForwardIndex();

    // id index depends on file type
    assertFalse(idIndexForwardIndex.getInt(javaFileId) == 0);
    assertFalse(idIndexForwardIndex.getInt(groovyFileId) == 0);
    assertFalse(idIndexForwardIndex.getInt(groovyFileId) == idIndexForwardIndex.getInt(javaFileId));
  }

  private boolean findWordInDumbMode(String word, final VirtualFile file, boolean inDumbMode) {
    assertEquals(inDumbMode, DumbService.isDumb(getProject()));

    final IdIndexEntry wordHash = new IdIndexEntry(word, true);
    final GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    final FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();
    final AtomicBoolean found = new AtomicBoolean(false);
    Runnable runnable = () -> found.set(fileBasedIndex.getContainingFiles(IdIndex.NAME, wordHash, scope).contains(file));
    if (inDumbMode) {
      fileBasedIndex.ignoreDumbMode(DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE, runnable);
    }
    else {
      runnable.run();
    }

    return found.get();
  }

  private static void assertStubLanguage(@NotNull com.intellij.lang.Language expectedLanguage, @NotNull ObjectStubTree<?> stub) {
    ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(expectedLanguage);
    PsiFileStub fileStub = assertInstanceOf(stub.getPlainList().get(0), PsiFileStub.class);
    assertEquals(parserDefinition.getFileNodeType(), fileStub.getType());
  }

  @NotNull
  private Map<IdIndexEntry, Integer> getIdIndexData(@NotNull VirtualFile file) {
    return FileBasedIndex.getInstance().getFileData(IdIndex.NAME, file, getProject());
  }

  public void test_no_caching_for_index_queries_with_different_ignoreDumbMode_kinds() {
    RecursionManager.disableMissedCacheAssertions(getTestRootDisposable());

    final PsiClass clazz = myFixture.addClass("class Foo {}");
    assertEquals(clazz, myFixture.findClass("Foo"));

    DumbModeTestUtils.runInDumbModeSynchronously(getProject(), () -> {
      final AtomicInteger indexQueries = new AtomicInteger(0);
      final AtomicInteger plainQueries = new AtomicInteger(0);

      final CachedValue<PsiClass> stubQuery = CachedValuesManager.getManager(getProject()).createCachedValue(() -> {
        indexQueries.incrementAndGet();
        return CachedValueProvider.Result.create(myFixture.findClass("Foo"), PsiModificationTracker.MODIFICATION_COUNT);
      });
      final CachedValue<Boolean> idQuery = CachedValuesManager.getManager(getProject()).createCachedValue(() -> {
        indexQueries.incrementAndGet();
        GlobalSearchScope fileScope = GlobalSearchScope.fileScope(clazz.getContainingFile());
        IdIndexEntry key = new IdIndexEntry("Foo", true);
        boolean hasId = !FileBasedIndex.getInstance().getContainingFiles(IdIndex.NAME, key, fileScope).isEmpty();
        return CachedValueProvider.Result.create(hasId, PsiModificationTracker.MODIFICATION_COUNT);
      });
      final CachedValue<String> plainValue = CachedValuesManager.getManager(getProject()).createCachedValue(() -> {
        plainQueries.incrementAndGet();
        return CachedValueProvider.Result.create("x", PsiModificationTracker.MODIFICATION_COUNT);
      });

      // index queries aren't cached
      for (int i = 0; i < 5; i++) {
        assertEquals(clazz, FileBasedIndex.getInstance().ignoreDumbMode(DumbModeAccessType.RELIABLE_DATA_ONLY, asComputable(stubQuery)));
      }
      assertTrue(indexQueries.get() >= 5);

      indexQueries.set(0);
      assertTrue(FileBasedIndex.getInstance().ignoreDumbMode(DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE, asComputable(idQuery)));
      assertTrue(FileBasedIndex.getInstance().ignoreDumbMode(DumbModeAccessType.RELIABLE_DATA_ONLY, asComputable(idQuery)));
      assertTrue(indexQueries.get() >= 2);

      // non-index queries should work as usual
      for (int i = 0; i < 3; i++) {
        assertEquals("x",
                     FileBasedIndex.getInstance().ignoreDumbMode(DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE, asComputable(plainValue)));
        assertEquals("x", FileBasedIndex.getInstance().ignoreDumbMode(DumbModeAccessType.RELIABLE_DATA_ONLY, asComputable(plainValue)));
      }
      assertTrue(plainQueries.get() > 0 && plainQueries.get() < 3 * 2);

      // cache queries inside single ignoreDumbMode
      indexQueries.set(0);
      getPsiManager().dropPsiCaches();
      FileBasedIndex.getInstance()
        .ignoreDumbMode(DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE, () -> {
          for (int i = 0; i < 5; i++) {
            assertTrue(idQuery.getValue());
          }
          assertTrue(indexQueries.get() > 0 && indexQueries.get() < 5);
        });

      indexQueries.set(0);
      getPsiManager().dropPsiCaches();
      FileBasedIndex.getInstance()
        .ignoreDumbMode(DumbModeAccessType.RELIABLE_DATA_ONLY, () -> {
          for (int i = 0; i < 5; i++) {
            assertEquals(clazz, stubQuery.getValue());
          }
          assertTrue(indexQueries.get() > 0 && indexQueries.get() < 5);
        });
    });
  }

  public void test_no_caching_on_write_action_inside_ignoreDumbMode() {
    RecursionManager.disableMissedCacheAssertions(getTestRootDisposable());

    final PsiClass clazz = myFixture.addClass("class Foo {}");
    assertEquals(clazz, myFixture.findClass("Foo"));

    DumbModeTestUtils.runInDumbModeSynchronously(getProject(), () -> {
      final CachedValue<PsiClass> stubQuery = CachedValuesManager.getManager(getProject()).createCachedValue(() -> {
        return CachedValueProvider.Result.create(myFixture.getJavaFacade().findClass("Foo", GlobalSearchScope.allScope(getProject())),
                                                 PsiModificationTracker.MODIFICATION_COUNT);
      });

      FileBasedIndex.getInstance().ignoreDumbMode(DumbModeAccessType.RELIABLE_DATA_ONLY, () -> {
        assertEquals(clazz, stubQuery.getValue());
        WriteCommandAction.runWriteCommandAction(getProject(), (Computable<PsiElement>)() -> clazz.setName("Bar"));
        assertNull(stubQuery.getValue());
      });
    });
  }

  public void test_indexes_should_be_wiped_after_scratch_removal() {
    WorkspaceEntityLifecycleSupporterUtils.INSTANCE.withAllEntitiesInWorkspaceFromProvidersDefinedOnEdt(getProject(), () -> {
      final VirtualFile file =
        ScratchRootType.getInstance().createScratchFile(getProject(), "Foo.java", JavaLanguage.INSTANCE, "class Foo {}");
      int fileId = ((VirtualFileWithId)file).getId();
      deleteOnTearDown(file);

      FileBasedIndexImpl fileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
      ID<Integer, Void> trigramId = TrigramIndex.INDEX_ID;

      fileBasedIndex.ensureUpToDate(trigramId, getProject(), GlobalSearchScope.everythingScope(getProject()));
      try {
        assertNotEmpty(fileBasedIndex.getIndex(trigramId).getIndexedFileData(fileId).values());

        WriteCommandAction.runWriteCommandAction(getProject(), (ThrowableComputable<?, IOException>)() -> {
          file.delete(null);
          return null;
        });
        fileBasedIndex.ensureUpToDate(trigramId, getProject(), GlobalSearchScope.everythingScope(getProject()));
        assertEmpty(fileBasedIndex.getIndex(trigramId).getIndexedFileData(fileId).values());
      }
      catch (StorageException | IOException e) {
        throw new RuntimeException(e);
      }
      return Unit.INSTANCE;
    });
  }

  public void test_requestReindex() {
    WorkspaceEntityLifecycleSupporterUtils.INSTANCE.withAllEntitiesInWorkspaceFromProvidersDefinedOnEdt(getProject(), () -> {
      VirtualFile file = ScratchRootType.getInstance().createScratchFile(getProject(), "Foo.java", JavaLanguage.INSTANCE, "class Foo {}");
      deleteOnTearDown(file);

      CountingFileBasedIndexExtension.registerCountingFileBasedIndex(getTestRootDisposable());

      FileBasedIndex.getInstance().getFileData(CountingFileBasedIndexExtension.getINDEX_ID(), file, getProject());
      assertTrue(CountingFileBasedIndexExtension.getCOUNTER().get() > 0);

      CountingFileBasedIndexExtension.getCOUNTER().set(0);
      FileBasedIndex.getInstance().requestReindex(file);

      FileBasedIndex.getInstance().getFileData(CountingFileBasedIndexExtension.getINDEX_ID(), file, getProject());
      assertTrue(CountingFileBasedIndexExtension.getCOUNTER().get() > 0);
      return Unit.INSTANCE;
    });
  }

  public void test_modified_excluded_file_not_present_in_index() throws StorageException, IOException {
    // we don't update excluded file index data, so we should wipe it to be consistent
    final VirtualFile file = myFixture.addFileToProject("src/to_be_excluded/A.java", "class A {}").getVirtualFile();
    assertNotNull(findClass("A"));

    FileBasedIndexImpl fileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
    ID<Integer, Void> trigramId = TrigramIndex.INDEX_ID;
    int fileId = ((VirtualFileWithId)file).getId();

    fileBasedIndex.ensureUpToDate(trigramId, getProject(), GlobalSearchScope.everythingScope(getProject()));
    assertNotEmpty(fileBasedIndex.getIndex(trigramId).getIndexedFileData(fileId).values());

    VirtualFile parentDir = file.getParent();
    PsiTestUtil.addExcludedRoot(myFixture.getModule(), parentDir);
    WriteAction.run(() -> VfsUtil.saveText(file, "class B {}"));

    fileBasedIndex.ensureUpToDate(trigramId, getProject(), GlobalSearchScope.everythingScope(getProject()));
    assertEmpty(fileBasedIndex.getIndex(trigramId).getIndexedFileData(fileId).values());
    IndexingRequestToken indexingRequest = getProject().getService(ProjectIndexingDependenciesService.class).getLatestIndexingRequestToken();
    assertFalse(IndexingFlag.isFileIndexed(file, indexingRequest.getFileIndexingStamp(file)));
  }

  public void test_modified_excluded_file_not_present_in_indexable_files_filter() throws IOException {
    final VirtualFile file = myFixture.addFileToProject("src/to_be_excluded/A.java", "class A {}").getVirtualFile();
    assertNotNull(findClass("A"));

    FileBasedIndexImpl fileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
    int fileId = ((VirtualFileWithId)file).getId();

    fileBasedIndex.getChangedFilesCollector().ensureUpToDate();
    assertNotNull(fileBasedIndex.getIndexableFilesFilterHolder().findProjectForFile(fileId));

    VirtualFile parentDir = file.getParent();
    PsiTestUtil.addExcludedRoot(myFixture.getModule(), parentDir);
    WriteAction.run(() -> VfsUtil.saveText(file, "class B {}"));

    fileBasedIndex.getChangedFilesCollector().ensureUpToDate();
    assertNull(fileBasedIndex.getIndexableFilesFilterHolder().findProjectForFile(fileId));
  }

  public void test_dirty_file_transfers_between_collectors_in_fbi() throws IOException {
    final VirtualFile file = myFixture.addFileToProject("A.java", "class A {}").getVirtualFile();

    FileBasedIndexImpl fileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
    int fileId = ((VirtualFileWithId)file).getId();

    assertTrue(fileBasedIndex.getChangedFilesCollector().getDirtyFiles().getProjectDirtyFiles(null).containsFile(fileId));
    assertFalse(fileBasedIndex.getFilesToUpdateCollector().getDirtyFiles().getProjectDirtyFiles(null).containsFile(fileId));
    fileBasedIndex.getChangedFilesCollector().ensureUpToDate(); // schedule file for update
    // now new file is already added to ProjectIndexableFilesFilter and should be moved to the dirty files queue of the project
    assertFalse(fileBasedIndex.getChangedFilesCollector().getDirtyFiles().getProjectDirtyFiles(getProject()).containsFile(fileId));
    assertTrue(fileBasedIndex.getFilesToUpdateCollector().getDirtyFiles().getProjectDirtyFiles(getProject()).containsFile(fileId));

    WriteAction.run(() -> VfsUtil.saveText(file, "class B {}")); // add new file event
    assertTrue(fileBasedIndex.getChangedFilesCollector().getDirtyFiles().getProjectDirtyFiles(getProject()).containsFile(fileId));
    assertTrue(fileBasedIndex.getFilesToUpdateCollector().getDirtyFiles().getProjectDirtyFiles(getProject()).containsFile(fileId));
  }

  public void test_stub_index_updated_after_language_level_change() {
    VirtualFile file = myFixture.addFileToProject("src1/A.java", "class A {}").getVirtualFile();
    FilePropertyKey<LanguageLevel> javaLanguageLevelKey = FilePropertyPusher.EP_NAME.findExtension(JavaLanguageLevelPusher.class).getFilePropertyKey();

    LanguageLevel languageLevel = javaLanguageLevelKey.getPersistentValue(file.getParent());
    assertNotNull(languageLevel);
    assertNotNull(findClass("A"));

    // be a :hacker:😀
    // do it manually somehow
    // seems property pushers are crazy, we know it from its name
    javaLanguageLevelKey.setPersistentValue(file.getParent(), null);
    // fire any event
    FileContentUtilCore.reparseFiles(file);

    assertNull(javaLanguageLevelKey.getPersistentValue(file.getParent()));
    assertNull(findClass("A"));

    // and return everything to a normal state
    javaLanguageLevelKey.setPersistentValue(file.getParent(), languageLevel);
    FileContentUtilCore.reparseFiles(file);

    assertNotNull(javaLanguageLevelKey.getPersistentValue(file.getParent()));
    assertNotNull(findClass("A"));
  }

  private static <T> ThrowableComputable<T, RuntimeException> asComputable(final CachedValue<T> cachedValue) {
    return () -> cachedValue.getValue();
  }

  private void deleteOnTearDown(VirtualFile fileOrDir) {
    Disposer.register(myFixture.getTestRootDisposable(), () -> VfsTestUtil.deleteFile(fileOrDir));
  }

  public void test_essential_GlobalSearchScopes_implement_VirtualFileEnumeration() {
    VirtualFile src = myFixture.addFileToProject("src1/A.java", "class A { int xxxyyy;}").getVirtualFile();
    assertNotNull(findClass("A"));
    VirtualFile scratch = ScratchRootType.getInstance().createScratchFile(getProject(), "Foo.java", JavaLanguage.INSTANCE, "class Foo { }");
    deleteOnTearDown(scratch);

    assertNotNull(VirtualFileEnumeration.extract(new ModuleWithDependentsScope(getProject(), Collections.singletonList(getModule()))));
    assertNotNull(VirtualFileEnumeration.extract(getModule().getModuleWithDependenciesScope()));
    assertNotNull(VirtualFileEnumeration.extract(getModule().getModuleWithDependentsScope()));
    assertNotNull(VirtualFileEnumeration.extract(ScratchesSearchScope.getScratchesScope(getProject())));
    assertNotNull(VirtualFileEnumeration.extract(getModule().getModuleScope()));

    VirtualFileEnumeration scratchEnum = VirtualFileEnumeration.extract(ScratchesSearchScope.getScratchesScope(getProject()));
    assertTrue(scratchEnum.contains(((VirtualFileWithId)scratch).getId()));
    assertFalse(scratchEnum.contains(((VirtualFileWithId)src).getId()));
    VirtualFileEnumeration moduleEnum = VirtualFileEnumeration.extract(getModule().getModuleScope());
    assertFalse(moduleEnum.contains(((VirtualFileWithId)scratch).getId()));
    assertTrue(moduleEnum.contains(((VirtualFileWithId)src).getId()));

    // assert that union merges enumerations correctly
    GlobalSearchScope scratchPlusSrc = getModule().getModuleScope().uniteWith(ScratchesSearchScope.getScratchesScope(getProject()));
    VirtualFileEnumeration scratchPlusSrcEnum = VirtualFileEnumeration.extract(scratchPlusSrc);
    assertNotNull(scratchPlusSrcEnum);
    assertTrue(scratchPlusSrcEnum.contains(((VirtualFileWithId)scratch).getId()));
    assertTrue(scratchPlusSrcEnum.contains(((VirtualFileWithId)src).getId()));

    VirtualFile src2 = myFixture.addFileToProject("src1/B.java", "class B { }").getVirtualFile();
    GlobalSearchScope files = GlobalSearchScope.filesScope(getProject(), Arrays.asList(src, src2));
    GlobalSearchScope filesAndSrc = getModule().getModuleScope().intersectWith(files);
    VirtualFileEnumeration filesAndSrcEnum = VirtualFileEnumeration.extract(filesAndSrc);
    assertNotNull(filesAndSrcEnum);
    assertFalse(filesAndSrcEnum.contains(((VirtualFileWithId)scratch).getId()));
    assertTrue(filesAndSrcEnum.contains(((VirtualFileWithId)src).getId()));
    assertTrue(filesAndSrcEnum.contains(((VirtualFileWithId)src2).getId()));

    VirtualFile scratchTxt = ScratchRootType.getInstance().createScratchFile(getProject(), "Foo.txt", PlainTextLanguage.INSTANCE, "xxx");
    assertTrue(ScratchesSearchScope.getScratchesScope(getProject()).contains(scratchTxt));
    GlobalSearchScope scratchJava = GlobalSearchScope.getScopeRestrictedByFileTypes(ScratchesSearchScope.getScratchesScope(getProject()), JavaFileType.INSTANCE);
    assertFalse(scratchJava.contains(scratchTxt));
    VirtualFileEnumeration scratchJavaEnum = VirtualFileEnumeration.extract(scratchJava);
    assertNotNull(scratchJavaEnum);
    assertTrue(scratchJavaEnum.contains(((VirtualFileWithId)scratch).getId()));
    assertFalse(scratchJavaEnum.contains(((VirtualFileWithId)src).getId()));
    assertFalse(scratchJavaEnum.contains(((VirtualFileWithId)src2).getId()));
  }
}
