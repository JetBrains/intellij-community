// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.codeInsight.JavaCodeInsightTestCase;
import com.intellij.configurationStore.StateStorageManagerKt;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.FixtureRuleKt;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.util.Processor;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ref.GCUtil;
import com.intellij.util.ref.GCWatcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * @author Dmitry Avdeev
 */
@SkipSlowTestLocally
public class PsiModificationTrackerTest extends JavaCodeInsightTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    PsiTestUtil.addSourceContentToRoots(getModule(), getProject().getBaseDir());
  }

  public void testAnnotationNotChanged() {
    doReplaceTest("@SuppressWarnings(\"zz\")\n" +
                  "public class Foo { <selection></selection>}",
                  "hi");
  }

  public void testAnnotationNameChanged() {
    doReplaceTest("@Suppr<selection>ess</selection>Warnings(\"zz\")\n" +
                  "public class Foo { }",
                  "hi");
  }

  public void testAnnotationParameterChanged() {
    doReplaceTest("@SuppressWarnings(\"<selection>zz</selection>\")\n" +
                  "public class Foo { }",
                  "hi");
  }

  public void testAnnotationRemoved() {
    doReplaceTest("<selection>@SuppressWarnings(\"zz\")</selection>\n" +
                  "public class Foo { }",
                  "");
  }

  public void testAnnotationWithClassRemoved() {
    doReplaceTest("<selection>@SuppressWarnings(\"zz\")\n" +
                  "public </selection> class Foo { }",
                  "");
  }

  public void testRemoveAnnotatedMethod() {
    doReplaceTest("public class Foo {\n" +
                  "  <selection>  " +
                  "   @SuppressWarnings(\"\")\n" +
                  "    public void method() {}\n" +
                  "</selection>" +
                  "}",
                  "");
  }

  public void testRenameAnnotatedMethod() {
    doReplaceTest("public class Foo {\n" +
                  "   @SuppressWarnings(\"\")\n" +
                  "    public void me<selection>th</selection>od() {}\n" +
                  "}",
                  "zzz");
  }

  public void testRenameAnnotatedClass() {
    doReplaceTest("   @SuppressWarnings(\"\")\n" +
                  "public class F<selection>o</selection>o {\n" +
                  "    public void method() {}\n" +
                  "}",
                  "zzz");
  }

  public void testRemoveAll() {
    doReplaceTest("<selection>@SuppressWarnings(\"zz\")\n" +
                  "public  class Foo { }</selection>",
                  "");
  }

  public void testRemoveFile() {
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

  private void doTest(@NonNls String text, Processor<? super PsiFile> run) {
    PsiFile file = configureByText(JavaFileType.INSTANCE, text);
    PsiModificationTracker tracker = PsiModificationTracker.SERVICE.getInstance(getProject());
    long count = tracker.getModificationCount();
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      run.process(file);
    });

    assertFalse(tracker.getModificationCount() == count);
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
    long count = getJavaTracker().getModificationCount();

    ApplicationManager.getApplication().runWriteAction(() -> file.getContainingDirectory().delete());

    assertTrue(count + ":" + getJavaTracker().getModificationCount(), getJavaTracker().getModificationCount() > count);
  }

  public void testClassShouldNotAppearWithoutEvents_WithPsi() throws IOException {
    final VirtualFile file = createTempFile("java", null, "", StandardCharsets.UTF_8);
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    assertNotNull(document);
    assertNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", GlobalSearchScope.allScope(getProject())));
    PsiManager psiManager = PsiManager.getInstance(getProject());
    long count1 = getJavaTracker().getModificationCount();
    PsiJavaFile psiFile = (PsiJavaFile)psiManager.findFile(file);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(0, "class Foo {}"));


    assertEquals(count1, getJavaTracker().getModificationCount()); // no PSI changes yet
    //so the class should not exist
    assertNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", GlobalSearchScope.allScope(getProject())));
    assertSize(0, psiFile.getClasses());
    assertEquals("", psiManager.findFile(file).getText());

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    assertFalse(count1 == getJavaTracker().getModificationCount());
    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", GlobalSearchScope.allScope(getProject())));
    assertEquals("class Foo {}", psiManager.findFile(file).getText());
    assertEquals("class Foo {}", psiManager.findFile(file).getNode().getText());
    assertSize(1, psiFile.getClasses());
  }

  public void testClassShouldNotAppearWithoutEvents_WithoutPsi() throws Exception {
    final GlobalSearchScope allScope = GlobalSearchScope.allScope(getProject());
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    final PsiManager psiManager = PsiManager.getInstance(getProject());

    final VirtualFile file = createTempFile("java", null, "", StandardCharsets.UTF_8);
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    assertNotNull(document);
    assertNull(facade.findClass("Foo", allScope));
    long count1 = getJavaTracker().getModificationCount();

    GCWatcher.tracking(PsiDocumentManager.getInstance(getProject()).getCachedPsiFile(document)).ensureCollected();
    assertNull(PsiDocumentManager.getInstance(getProject()).getCachedPsiFile(document));

    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(0, "class Foo {}"));
    DocumentCommitThread.getInstance().waitForAllCommits(100, TimeUnit.SECONDS);

    assertFalse(count1 == getJavaTracker().getModificationCount());
    assertTrue(PsiDocumentManager.getInstance(getProject()).isCommitted(document));
    assertNotNull(facade.findClass("Foo", allScope));

    PsiJavaFile psiFile = (PsiJavaFile)psiManager.findFile(file);
    assertSize(1, psiFile.getClasses());
    assertEquals("class Foo {}", psiFile.getText());
    assertEquals("class Foo {}", psiFile.getNode().getText());
  }

  public void testClassShouldNotDisappearWithoutEvents() throws Exception {
    long count0 = getJavaTracker().getModificationCount();

    final VirtualFile file = addFileToProject("Foo.java", "class Foo {}").getVirtualFile();
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    assertNotNull(document);

    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", GlobalSearchScope.allScope(getProject())));
    long count1 = getJavaTracker().getModificationCount();
    assertFalse(count1 == count0);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.deleteString(0, document.getTextLength()));
    DocumentCommitThread.getInstance().waitForAllCommits(100, TimeUnit.SECONDS);
    gcPsi(file);

    assertFalse(count1 == getJavaTracker().getModificationCount());
    assertNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", GlobalSearchScope.allScope(getProject())));
  }

  private void gcPsi(VirtualFile file) {
    PsiManagerEx psiManager = PsiManagerEx.getInstanceEx(getProject());
    GCWatcher.tracking(psiManager.getFileManager().getCachedPsiFile(file)).ensureCollected();
    assertNull(psiManager.getFileManager().getCachedPsiFile(file));
  }

  public void testClassShouldNotDisappearWithoutEvents_NoDocument() throws IOException {
    final VirtualFile file = addFileToProject("Foo.java", "class Foo {}").getVirtualFile();
    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", GlobalSearchScope.allScope(getProject())));
    long count1 = getJavaTracker().getModificationCount();

    gcPsiAndDocument(file);

    setFileText(file, "");
    assertNull(FileDocumentManager.getInstance().getCachedDocument(file));

    assertNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", GlobalSearchScope.allScope(getProject())));
    assertFalse(count1 == getJavaTracker().getModificationCount());
  }

  private void gcPsiAndDocument(VirtualFile file) {
    PsiManagerEx psiManager = PsiManagerEx.getInstanceEx(getProject());
    GCWatcher.tracking(FileDocumentManager.getInstance().getCachedDocument(file), psiManager.getFileManager().getCachedPsiFile(file)).ensureCollected();
    assertNull(FileDocumentManager.getInstance().getCachedDocument(file));
    assertNull(psiManager.getFileManager().getCachedPsiFile(file));
  }

  public void testClassShouldNotAppearWithoutEvents_NoPsiDirectory() throws IOException {
    long count0 = getJavaTracker().getModificationCount();

    final PsiManagerEx psiManager = PsiManagerEx.getInstanceEx(getProject());
    VirtualFile parentDir = createChildDirectory(getProject().getBaseDir(), "tmp");

    assertNull(((FileManagerImpl)psiManager.getFileManager()).getCachedDirectory(parentDir));

    File file = new File(parentDir.getPath(), "Foo.java");
    FileUtil.writeToFile(file, "class Foo {}");
    assertNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file));

    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", GlobalSearchScope.allScope(getProject())));
    assertFalse(count0 == getJavaTracker().getModificationCount());
  }

  public void testClassShouldNotAppearWithoutEvents_NoPsiGrandParentDirectory() throws IOException {
    long count0 = getJavaTracker().getModificationCount();

    final PsiManagerEx psiManager = PsiManagerEx.getInstanceEx(getProject());
    VirtualFile parentDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(createTempDirectory());
    assertNull(((FileManagerImpl)psiManager.getFileManager()).getCachedDirectory(parentDir));

    File file = new File(parentDir.getPath() + "/foo", "Foo.java");
    FileUtil.writeToFile(file, "package foo; class Foo {}");
    assertNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file));

    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("foo.Foo", GlobalSearchScope.allScope(getProject())));
    assertFalse(count0 == getJavaTracker().getModificationCount());
  }

  public void testClassShouldNotDisappearWithoutEvents_VirtualFileDeleted() throws IOException {
    final VirtualFile file = addFileToProject("Foo.java", "class Foo {}").getVirtualFile();
    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", GlobalSearchScope.allScope(getProject())));
    long count1 = getJavaTracker().getModificationCount();

    gcPsiAndDocument(file);
    delete(file);

    assertNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", GlobalSearchScope.allScope(getProject())));
    assertFalse(count1 == getJavaTracker().getModificationCount());
  }

  public void testClassShouldNotDisappearWithoutEvents_ParentVirtualDirectoryDeleted() throws Exception {
    final VirtualFile file = addFileToProject("foo/Foo.java", "package foo; class Foo {}").getVirtualFile();
    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("foo.Foo", GlobalSearchScope.allScope(getProject())));

    long count1 = getJavaTracker().getModificationCount();

    gcPsiAndDocument(file);
    delete(file.getParent());

    assertNull(JavaPsiFacade.getInstance(getProject()).findClass("foo.Foo", GlobalSearchScope.allScope(getProject())));
    assertFalse(count1 == getJavaTracker().getModificationCount());
  }

  public void testClassShouldNotDisappearWithoutEvents_InCodeBlock() throws Exception {
    String barStr = "class Bar {}";
    PsiFile file = addFileToProject("Foo.java", "class Foo {{" + barStr + "}}");
    JBIterable<PsiClass> barQuery = SyntaxTraverser.psiTraverser(file).filter(PsiClass.class).filter(o -> "Bar".equals(o.getName()));
    assertNotNull(barQuery.first());
    Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
    int index = document.getText().indexOf(barStr);
    long count1 = getJavaTracker().getModificationCount();
    //WriteCommandAction.runWriteCommandAction(getProject(), () -> bar.delete());
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.replaceString(index, index + barStr.length(), ""));
    PsiDocumentManager.getInstance(getProject()).commitDocument(document);

    assertNull(barQuery.first());
    assertFalse(count1 == getJavaTracker().getModificationCount());
  }

  public void testClassShouldNotAppearWithoutEvents_InCodeBlock() throws Exception {
    String barStr = "class Bar {}";
    PsiFile file = addFileToProject("Foo.java", "class Foo {{" + "}}");
    JBIterable<PsiClass> barQuery = SyntaxTraverser.psiTraverser(file).filter(PsiClass.class).filter(o -> "Bar".equals(o.getName()));
    assertNull(barQuery.first());
    Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
    int index = document.getText().indexOf("}}");
    long count1 = getJavaTracker().getModificationCount();
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(index, barStr));
    PsiDocumentManager.getInstance(getProject()).commitDocument(document);

    assertNotNull(barQuery.first());
    assertFalse(count1 == getJavaTracker().getModificationCount());
  }

  public void testVirtualFileRename_WithPsi() throws IOException {
    final PsiManagerEx psiManager = PsiManagerEx.getInstanceEx(getProject());
    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());

    final VirtualFile file = addFileToProject("foo/Foo.java", "package foo; class Foo {}").getVirtualFile();
    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("foo.Foo", scope));
    long count1 = getTracker().getModificationCount();
    long hc = psiManager.findFile(file).hashCode();
    long stamp1 = psiManager.findFile(file).getModificationStamp();

    rename(file, "Bar.java");

    assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("foo.Foo", scope));
    assertTrue(count1 != getTracker().getModificationCount());
    assertTrue(stamp1 != psiManager.findFile(file).getModificationStamp());
    assertEquals(hc, psiManager.findFile(file).hashCode());
  }

  public void testLanguageLevelChange() throws IOException {
    //noinspection unused
    PsiFile psiFile = addFileToProject("Foo.java", "class Foo {}");
    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());

    GCUtil.tryGcSoftlyReachableObjects();

    PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass("Foo", scope);
    assertNotNull(psiClass);

    long count = getJavaTracker().getModificationCount();

    IdeaTestUtil.setModuleLanguageLevel(getModule(), LanguageLevel.JDK_1_3);

    assertTrue(count != getJavaTracker().getModificationCount());

    psiClass = JavaPsiFacade.getInstance(getProject()).findClass("Foo", scope);
    assertNotNull(psiClass);
    assertTrue(psiClass.isValid());
  }

  private PsiFile addFileToProject(String fileName, String text) throws IOException {
    File file = new File(getProject().getBasePath(), fileName);
    file.getParentFile().mkdirs();
    setContentOnDisk(file, null, text, StandardCharsets.UTF_8);
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    return PsiManager.getInstance(getProject()).findFile(virtualFile);
  }

  public void testRootsChangeIncreasesCounts() {
    PsiModificationTracker tracker = (PsiModificationTracker)getTracker();
    long mc = tracker.getModificationCount();
    long js = getJavaTracker().getModificationCount();
    long ocb = tracker.getModificationCount();

    WriteAction.run(() -> ProjectRootManagerEx.getInstanceEx(getProject()).makeRootsChange(EmptyRunnable.INSTANCE, false, true));

    assertTrue(mc != tracker.getModificationCount());
    assertTrue(js != getJavaTracker().getModificationCount());
    assertTrue(ocb != tracker.getModificationCount());
  }

  public void testNoIncrementOnWorkspaceFileChange() {
    FixtureRuleKt.runInLoadComponentStateMode(myProject, () -> {
      StateStorageManagerKt.saveComponentManager(getProject(), true);

      ModificationTracker tracker = getTracker();
      long mc = tracker.getModificationCount();

      VirtualFile ws = myProject.getWorkspaceFile();
      assertNotNull(ws);
      try {
        WriteCommandAction.writeCommandAction(myProject).run(() -> VfsUtil.saveText(ws, VfsUtilCore.loadText(ws) + " "));
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      assertEquals(mc, tracker.getModificationCount());

      return null;
    });
  }

  public void testNoIncrementOnReadOnlyStatusChange() throws IOException {
    VirtualFile file = addFileToProject("Foo.java", "class Foo {}").getVirtualFile();

    ModificationTracker tracker = getTracker();
    long mc = tracker.getModificationCount();

    WriteAction.run(() -> file.setWritable(false));
    assertEquals(mc, tracker.getModificationCount());

    gcPsi(file);

    WriteAction.run(() -> file.setWritable(true));
    assertEquals(mc, tracker.getModificationCount());
  }

  public void testJavaStructureModCountMustNotBeAdvancedOnJavadocChange() {
    configureByText(JavaFileType.INSTANCE, "/* <selection>abc</selection> */ class A{}");

    PsiModificationTracker tracker = (PsiModificationTracker)getTracker();
    long javaCount = getJavaTracker().getModificationCount();
    long codeBlockCount = tracker.getModificationCount();

    WriteCommandAction.runWriteCommandAction(getProject(), () -> replaceSelection("cde"));

    assertEquals(javaCount, getJavaTracker().getModificationCount());
    assertFalse(codeBlockCount == tracker.getModificationCount());
  }

  public void testJavaStructureModCountMustNotBeAdvancedOnAddingSpace() {
    configureByText(JavaFileType.INSTANCE, "class A{ <selection></selection> }");

    PsiModificationTracker tracker = (PsiModificationTracker)getTracker();
    long javaCount = getJavaTracker().getModificationCount();
    long codeBlockCount = tracker.getModificationCount();

    WriteCommandAction.runWriteCommandAction(getProject(), () -> replaceSelection(" "));

    assertEquals(javaCount, getJavaTracker().getModificationCount());
    assertFalse(codeBlockCount == tracker.getModificationCount());
  }

  public void testChangeBothInsideAnonymousAndOutsideShouldAdvanceJavaModStructureAndClearCaches() {
    PsiFile file = configureByText(JavaFileType.INSTANCE, "class A{ void bar() {\n" +
                                                          "int a = foo().goo();\n" +
                                                          "Object r = new Object() {\n" +
                                                          "  void foo() {}\n" +
                                                          "};\n" +
                                                          "}}");

    PsiAnonymousClass anon = SyntaxTraverser.psiTraverser(file).filter(PsiAnonymousClass.class).first();
    Arrays.stream(anon.getAllMethods()).forEach(PsiUtilCore::ensureValid);

    long javaCount = getJavaTracker().getModificationCount();

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      TextRange methodRange = anon.getMethods()[0].getTextRange();
      getEditor().getDocument().deleteString(methodRange.getStartOffset(), methodRange.getEndOffset());

      int gooIndex = file.getText().indexOf("goo");
      getEditor().getDocument().deleteString(gooIndex, gooIndex + 3);

      PsiDocumentManager.getInstance(myProject).commitDocument(getEditor().getDocument());
    });

    Arrays.stream(anon.getAllMethods()).forEach(PsiUtilCore::ensureValid);
    assertFalse(javaCount == getJavaTracker().getModificationCount());
  }

  public void testDeleteLocalClass() {
    PsiFile file = configureByText(JavaFileType.INSTANCE, "class A{ void bar() {\n" +
                                                          "abstract class Local { abstract void foo(); }\n" +
                                                          "int a = 1;" +
                                                          "while (true) {\n" +
                                                          "Local r = new Local() {\n" +
                                                          "  public void foo() {}\n" +
                                                          "}" +
                                                          "};\n" +
                                                          "}}");

    PsiAnonymousClass anon = SyntaxTraverser.psiTraverser(file).filter(PsiAnonymousClass.class).first();
    PsiMethod method = anon.getMethods()[0];

    PsiUtilCore.ensureValid(method);
    Arrays.stream(method.findSuperMethods()).forEach(PsiUtilCore::ensureValid);

    long javaCount = getJavaTracker().getModificationCount();

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      TextRange localRange = anon.getSuperClass().getTextRange();
      getEditor().getDocument().deleteString(localRange.getStartOffset(), localRange.getEndOffset());

      PsiDocumentManager.getInstance(myProject).commitDocument(getEditor().getDocument());
    });

    PsiUtilCore.ensureValid(method);
    Arrays.stream(method.findSuperMethods()).forEach(PsiUtilCore::ensureValid);

    assertFalse(javaCount == getJavaTracker().getModificationCount());
  }

  @NotNull
  private ModificationTracker getTracker() {
    return PsiModificationTracker.SERVICE.getInstance(getProject());
  }

  @NotNull
  ModificationTracker getJavaTracker() {
    return PsiModificationTracker.SERVICE.getInstance(getProject());
  }

  public static class JavaLanguageTrackerTest extends PsiModificationTrackerTest {
    @Override
    @NotNull
    ModificationTracker getJavaTracker() {
      return ((PsiModificationTrackerImpl)PsiModificationTracker.SERVICE.getInstance(getProject()))
        .forLanguage(JavaLanguage.INSTANCE);
    }

    public void testJavaStructureModCountMustNotBeAdvancedOnJavadocChange() {}
    public void testJavaStructureModCountMustNotBeAdvancedOnAddingSpace() {}
  }
}
