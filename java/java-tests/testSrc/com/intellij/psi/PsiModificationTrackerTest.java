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
package com.intellij.psi;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.DocumentCommitThread;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;

/**
 * @author Dmitry Avdeev
 */
@SkipSlowTestLocally
public class PsiModificationTrackerTest extends CodeInsightTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    PsiTestUtil.addSourceContentToRoots(getModule(), getProject().getBaseDir());
  }

  public void testAnnotationNotChanged() throws Exception {
    doReplaceTest("@SuppressWarnings(\"zz\")\n" +
                  "public class Foo { <selection></selection>}",
                  "hi");
  }

  public void testAnnotationNameChanged() throws Exception {
    doReplaceTest("@Suppr<selection>ess</selection>Warnings(\"zz\")\n" +
                  "public class Foo { }",
                  "hi");
  }

  public void testAnnotationParameterChanged() throws Exception {
    doReplaceTest("@SuppressWarnings(\"<selection>zz</selection>\")\n" +
                  "public class Foo { }",
                  "hi");
  }

  public void testAnnotationRemoved() throws Exception {
    doReplaceTest("<selection>@SuppressWarnings(\"zz\")</selection>\n" +
                  "public class Foo { }",
                  "");
  }

  public void testAnnotationWithClassRemoved() throws Exception {
    doReplaceTest("<selection>@SuppressWarnings(\"zz\")\n" +
                  "public </selection> class Foo { }",
                  "");
  }

  public void testRemoveAnnotatedMethod() throws Exception {
    doReplaceTest("public class Foo {\n" +
                  "  <selection>  " +
                  "   @SuppressWarnings(\"\")\n" +
                  "    public void method() {}\n" +
                  "</selection>" +
                  "}",
                  "");
  }

  public void testRenameAnnotatedMethod() throws Exception {
    doReplaceTest("public class Foo {\n" +
                  "   @SuppressWarnings(\"\")\n" +
                  "    public void me<selection>th</selection>od() {}\n" +
                  "}",
                  "zzz");
  }

  public void testRenameAnnotatedClass() throws Exception {
    doReplaceTest("   @SuppressWarnings(\"\")\n" +
                  "public class F<selection>o</selection>o {\n" +
                  "    public void method() {}\n" +
                  "}",
                  "zzz");
  }

  public void testRemoveAll() throws Exception {
    doReplaceTest("<selection>@SuppressWarnings(\"zz\")\n" +
                  "public  class Foo { }</selection>",
                  "");
  }

  public void testRemoveFile() throws Exception {
    doTest("<selection>@SuppressWarnings(\"zz\")\n" +
           "public  class Foo { }</selection>",
           psiFile -> {
             final VirtualFile vFile = psiFile.getVirtualFile();
             assert vFile != null : psiFile;
             FileEditorManager.getInstance(getProject()).closeFile(vFile);
             delete(vFile);
             return false;
           });
  }

  private void doReplaceTest(@NonNls String text, @NonNls final String with) {
    doTest(text, psiFile -> {
      replaceSelection(with);
      return false;
    });
  }

  private void doTest(@NonNls String text, Processor<PsiFile> run) {
    PsiFile file = configureByText(JavaFileType.INSTANCE, text);
    PsiModificationTracker modificationTracker = PsiManager.getInstance(getProject()).getModificationTracker();
    long count = modificationTracker.getModificationCount();
    WriteCommandAction.runWriteCommandAction(getProject(), ()->{run.process(file);});

    assertFalse(modificationTracker.getModificationCount() == count);
  }

  private void replaceSelection(final String with) {
    SelectionModel sel = getEditor().getSelectionModel();
    getEditor().getDocument().replaceString(sel.getSelectionStart(), sel.getSelectionEnd(), with);
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
  }

  public void testJavaStructureModificationChangesAfterPackageDelete() throws IOException {
    final VirtualFile baseDir = getProject().getBaseDir();
    VirtualFile virtualFile = createChildData(createChildDirectory(createChildDirectory(baseDir, "x"), "y"), "Z.java");
    setFileText(virtualFile, "text");
    configureByFile(virtualFile);
    PsiFile file = getFile();
    PsiModificationTracker modificationTracker = PsiManager.getInstance(getProject()).getModificationTracker();
    long count = modificationTracker.getJavaStructureModificationCount();

    ApplicationManager.getApplication().runWriteAction(() -> {
      file.getContainingDirectory().delete();
    });

    assertTrue(count+":"+modificationTracker.getJavaStructureModificationCount(), modificationTracker.getJavaStructureModificationCount() > count);
  }

  public void testClassShouldNotAppearWithoutEvents_WithPsi() throws IOException {
    final VirtualFile file = createTempFile("java", null, "", CharsetToolkit.UTF8_CHARSET);
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    assertNotNull(document);
    assertNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", GlobalSearchScope.allScope(getProject())));
    PsiManager psiManager = PsiManager.getInstance(getProject());
    PsiModificationTracker tracker = psiManager.getModificationTracker();
    long count1 = tracker.getJavaStructureModificationCount();
    PsiJavaFile psiFile = (PsiJavaFile)psiManager.findFile(file);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      document.insertString(0, "class Foo {}");
    });


    assertEquals(count1, tracker.getJavaStructureModificationCount()); // no PSI changes yet
    //so the class should not exist
    assertNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", GlobalSearchScope.allScope(getProject())));
    assertSize(0, psiFile.getClasses());
    assertEquals("", psiManager.findFile(file).getText());
    PlatformTestUtil.tryGcSoftlyReachableObjects();

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    assertFalse(count1 == tracker.getJavaStructureModificationCount());
    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", GlobalSearchScope.allScope(getProject())));
    assertEquals("class Foo {}", psiManager.findFile(file).getText());
    assertEquals("class Foo {}", psiManager.findFile(file).getNode().getText());
    assertSize(1, psiFile.getClasses());
  }

  public void testClassShouldNotAppearWithoutEvents_WithoutPsi() throws Exception {
    final GlobalSearchScope allScope = GlobalSearchScope.allScope(getProject());
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    final PsiManager psiManager = PsiManager.getInstance(getProject());
    final PsiModificationTracker tracker = psiManager.getModificationTracker();

    final VirtualFile file = createTempFile("java", null, "", CharsetToolkit.UTF8_CHARSET);
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    assertNotNull(document);
    assertNull(facade.findClass("Foo", allScope));
    long count1 = tracker.getJavaStructureModificationCount();

    PlatformTestUtil.tryGcSoftlyReachableObjects();
    assertNull(PsiDocumentManager.getInstance(getProject()).getCachedPsiFile(document));

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      document.insertString(0, "class Foo {}");
    });
    DocumentCommitThread.getInstance().waitForAllCommits();

    assertFalse(count1 == tracker.getJavaStructureModificationCount());
    assertTrue(PsiDocumentManager.getInstance(getProject()).isCommitted(document));
    assertNotNull(facade.findClass("Foo", allScope));

    PsiJavaFile psiFile = (PsiJavaFile)psiManager.findFile(file);
    assertSize(1, psiFile.getClasses());
    assertEquals("class Foo {}", psiFile.getText());
    assertEquals("class Foo {}", psiFile.getNode().getText());
  }

  public void testClassShouldNotDisappearWithoutEvents() throws Exception {
    PsiModificationTracker tracker = PsiManager.getInstance(getProject()).getModificationTracker();
    long count0 = tracker.getJavaStructureModificationCount();

    final VirtualFile file = addFileToProject("Foo.java", "class Foo {}").getVirtualFile();
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    assertNotNull(document);

    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", GlobalSearchScope.allScope(getProject())));
    long count1 = tracker.getJavaStructureModificationCount();
    assertFalse(count1 == count0);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      document.deleteString(0, document.getTextLength());
    });
    DocumentCommitThread.getInstance().waitForAllCommits();

    // gc softly-referenced file and AST
    PlatformTestUtil.tryGcSoftlyReachableObjects();
    final PsiManagerEx psiManager = (PsiManagerEx)PsiManager.getInstance(getProject());
    assertNull(psiManager.getFileManager().getCachedPsiFile(file));

    assertFalse(count1 == tracker.getJavaStructureModificationCount());
    assertNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", GlobalSearchScope.allScope(getProject())));
  }


  public void testClassShouldNotDisappearWithoutEvents_NoDocument() throws IOException {
    PsiModificationTracker tracker = PsiManager.getInstance(getProject()).getModificationTracker();
    final PsiManagerEx psiManager = (PsiManagerEx)PsiManager.getInstance(getProject());

    final VirtualFile file = addFileToProject("Foo.java", "class Foo {}").getVirtualFile();
    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", GlobalSearchScope.allScope(getProject())));
    long count1 = tracker.getJavaStructureModificationCount();

    // gc softly-referenced file and document
    PlatformTestUtil.tryGcSoftlyReachableObjects();
    assertNull(FileDocumentManager.getInstance().getCachedDocument(file));
    assertNull(psiManager.getFileManager().getCachedPsiFile(file));

    setFileText(file, "");
    assertNull(FileDocumentManager.getInstance().getCachedDocument(file));

    assertNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", GlobalSearchScope.allScope(getProject())));
    assertFalse(count1 == tracker.getJavaStructureModificationCount());
  }

  public void testClassShouldNotAppearWithoutEvents_NoPsiDirectory() throws IOException {
    PsiModificationTracker tracker = PsiManager.getInstance(getProject()).getModificationTracker();
    long count0 = tracker.getJavaStructureModificationCount();

    final PsiManagerEx psiManager = (PsiManagerEx)PsiManager.getInstance(getProject());
    VirtualFile parentDir = createChildDirectory(getProject().getBaseDir(), "tmp");

    assertNull(((FileManagerImpl)psiManager.getFileManager()).getCachedDirectory(parentDir));

    File file = new File(parentDir.getPath(), "Foo.java");
    FileUtil.writeToFile(file, "class Foo {}");
    assertNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file));

    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", GlobalSearchScope.allScope(getProject())));
    assertFalse(count0 == tracker.getJavaStructureModificationCount());
  }

  public void testClassShouldNotAppearWithoutEvents_NoPsiGrandParentDirectory() throws IOException {
    PsiModificationTracker tracker = PsiManager.getInstance(getProject()).getModificationTracker();
    long count0 = tracker.getJavaStructureModificationCount();

    final PsiManagerEx psiManager = (PsiManagerEx)PsiManager.getInstance(getProject());
    VirtualFile parentDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(createTempDirectory());
    assertNull(((FileManagerImpl)psiManager.getFileManager()).getCachedDirectory(parentDir));

    File file = new File(parentDir.getPath() + "/foo", "Foo.java");
    FileUtil.writeToFile(file, "package foo; class Foo {}");
    assertNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file));

    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("foo.Foo", GlobalSearchScope.allScope(getProject())));
    assertFalse(count0 == tracker.getJavaStructureModificationCount());
  }

  public void testClassShouldNotDisappearWithoutEvents_VirtualFileDeleted() throws IOException {
    PsiModificationTracker tracker = PsiManager.getInstance(getProject()).getModificationTracker();
    final PsiManagerEx psiManager = (PsiManagerEx)PsiManager.getInstance(getProject());

    final VirtualFile file = addFileToProject("Foo.java", "class Foo {}").getVirtualFile();
    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", GlobalSearchScope.allScope(getProject())));
    long count1 = tracker.getJavaStructureModificationCount();

    // gc softly-referenced file and document
    PlatformTestUtil.tryGcSoftlyReachableObjects();
    assertNull(FileDocumentManager.getInstance().getCachedDocument(file));
    assertNull(psiManager.getFileManager().getCachedPsiFile(file));
    delete(file);

    assertNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", GlobalSearchScope.allScope(getProject())));
    assertFalse(count1 == tracker.getJavaStructureModificationCount());
  }

  public void testClassShouldNotDisappearWithoutEvents_ParentVirtualDirectoryDeleted() throws Exception {
    PsiModificationTracker tracker = PsiManager.getInstance(getProject()).getModificationTracker();
    final PsiManagerEx psiManager = (PsiManagerEx)PsiManager.getInstance(getProject());

    final VirtualFile file = addFileToProject("foo/Foo.java", "package foo; class Foo {}").getVirtualFile();
    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("foo.Foo", GlobalSearchScope.allScope(getProject())));

    long count1 = tracker.getJavaStructureModificationCount();

    // gc softly-referenced file and document
    PlatformTestUtil.tryGcSoftlyReachableObjects();
    assertNull(FileDocumentManager.getInstance().getCachedDocument(file));
    assertNull(psiManager.getFileManager().getCachedPsiFile(file));
    delete(file.getParent());

    assertNull(JavaPsiFacade.getInstance(getProject()).findClass("foo.Foo", GlobalSearchScope.allScope(getProject())));
    assertFalse(count1 == tracker.getJavaStructureModificationCount());
  }

  public void testVirtualFileRename_WithPsi() throws IOException {
    PsiModificationTracker tracker = PsiManager.getInstance(getProject()).getModificationTracker();
    final PsiManagerEx psiManager = (PsiManagerEx)PsiManager.getInstance(getProject());
    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());

    final VirtualFile file = addFileToProject("foo/Foo.java", "package foo; class Foo {}").getVirtualFile();
    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("foo.Foo", scope));
    long count1 = tracker.getModificationCount();
    long hc = psiManager.findFile(file).hashCode();
    long stamp1 = psiManager.findFile(file).getModificationStamp();

    rename(file, "Bar.java");

    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("foo.Foo", scope));
    assertTrue(count1 != tracker.getModificationCount());
    assertTrue(stamp1 != psiManager.findFile(file).getModificationStamp());
    assertEquals(hc, psiManager.findFile(file).hashCode());
  }

  public void testLanguageLevelChange() throws IOException {
    //noinspection unused
    PsiFile psiFile = addFileToProject("Foo.java", "class Foo {}");
    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());

    PlatformTestUtil.tryGcSoftlyReachableObjects();

    PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass("Foo", scope);
    assertNotNull(psiClass);

    long count = PsiManager.getInstance(getProject()).getModificationTracker().getJavaStructureModificationCount();

    IdeaTestUtil.setModuleLanguageLevel(getModule(), LanguageLevel.JDK_1_3);

    assertTrue(count != PsiManager.getInstance(getProject()).getModificationTracker().getJavaStructureModificationCount());

    psiClass = JavaPsiFacade.getInstance(getProject()).findClass("Foo", scope);
    assertNotNull(psiClass);
    assertTrue(psiClass.isValid());
  }

  private PsiFile addFileToProject(String fileName, String text) throws IOException {
    File file = new File(getProject().getBaseDir().getPath(), fileName);
    file.getParentFile().mkdirs();
    setContentOnDisk(file, null, text, CharsetToolkit.UTF8_CHARSET);
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    return PsiManager.getInstance(getProject()).findFile(virtualFile);
  }
}
