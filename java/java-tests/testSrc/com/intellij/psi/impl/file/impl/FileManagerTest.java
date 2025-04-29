// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.file.impl;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.JavaPsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.util.ui.UIUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

public class FileManagerTest extends JavaPsiTestCase {
  private static final Logger LOG = Logger.getInstance(FileManagerTest.class);

  private VirtualFile myPrjDir1;
  private VirtualFile myPrjDir2;
  private VirtualFile mySrcDir1;
  private VirtualFile mySrcDir2;
  private VirtualFile myClsDir1;
  private VirtualFile myExcludedDir1;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    VirtualFile rootVFile = getTempDir().createVirtualDir();

    ApplicationManager.getApplication().runWriteAction(() -> {
      VirtualFile found = VirtualFileManager.getInstance().findFileByUrl(rootVFile.getUrl());
      LOG.assertTrue(Comparing.equal(found, rootVFile));

      myPrjDir1 = createChildDirectory(rootVFile, "prj1");
      mySrcDir1 = createChildDirectory(myPrjDir1, "src1");
      mySrcDir2 = createChildDirectory(myPrjDir1, "src2");

      myPrjDir2 = createChildDirectory(rootVFile, "prj2");

      myClsDir1 = createChildDirectory(myPrjDir1, "cls1");

      myExcludedDir1 = createChildDirectory(mySrcDir1, "excluded");

      PsiTestUtil.addContentRoot(myModule, myPrjDir1);
      PsiTestUtil.addSourceRoot(myModule, mySrcDir1);
      PsiTestUtil.addSourceRoot(myModule, mySrcDir2);
      PsiTestUtil.addExcludedRoot(myModule, myExcludedDir1);
      ModuleRootModificationUtil.addModuleLibrary(myModule, myClsDir1.getUrl());
      PsiTestUtil.addContentRoot(myModule, myPrjDir2);
    });
  }

  public void testFindFile() {
    VirtualFile txtFile = createChildData(myPrjDir1, "a.txt");
    VirtualFile txtFile1 = createChildData(myPrjDir1.getParent(), "a.txt");
    VirtualFile javaFile = createChildData(mySrcDir1, "a.java");
    VirtualFile javaFile1 = createChildData(myPrjDir1, "a.java");
    VirtualFile compiledFileInSrc = createChildData(mySrcDir1, "a.class");
    VirtualFile compiledFileInCls = createChildData(myClsDir1, "a.class");
    VirtualFile excludedFile = createChildData(myExcludedDir1, "a.txt");

    FileManagerEx fileManager = myPsiManager.getFileManagerEx();

    PsiFile txtPsiFile = fileManager.findFile(txtFile);
    assertTrue(txtPsiFile instanceof PsiPlainTextFile);

    PsiFile txtPsiFile1 = fileManager.findFile(txtFile1);
    assertTrue(txtPsiFile1 instanceof PsiPlainTextFile);

    PsiFile javaPsiFile = fileManager.findFile(javaFile);
    assertTrue(javaPsiFile instanceof PsiJavaFile);

    PsiFile javaPsiFile1 = fileManager.findFile(javaFile1);
    assertTrue(javaPsiFile1 instanceof PsiJavaFile);

    PsiFile compiledPsiFile = fileManager.findFile(compiledFileInSrc);
    assertInstanceOf(compiledPsiFile, PsiBinaryFile.class);

    PsiFile compiledPsiFile1 = fileManager.findFile(compiledFileInCls);
    assertTrue(compiledPsiFile1 instanceof PsiJavaFile);
    assertTrue(compiledPsiFile1 instanceof PsiCompiledElement);

    PsiFile excludedPsiFile = fileManager.findFile(excludedFile);
    assertNotNull(excludedPsiFile);
  }

  public void testFindDirectory() {
    VirtualFile dir = createChildDirectory(myPrjDir1, "dir");
    VirtualFile dir1 = createChildDirectory(myPrjDir1.getParent(), "dir");
    VirtualFile excludedDir = createChildDirectory(myExcludedDir1, "dir");
    VirtualFile ignoredDir = createChildDirectory(mySrcDir1, "CVS");

    FileManagerEx fileManager = myPsiManager.getFileManagerEx();

    PsiDirectory psiDir = fileManager.findDirectory(dir);
    assertNotNull(psiDir);

    PsiDirectory psiDir1 = fileManager.findDirectory(dir1);
    assertNotNull(psiDir1);

    PsiDirectory excludedPsiDir = fileManager.findDirectory(excludedDir);
    assertNotNull(excludedPsiDir);

    PsiDirectory ignoredPsiDir = fileManager.findDirectory(ignoredDir);
    assertNull(ignoredPsiDir);
  }

  public void testDeleteFile() {
    final VirtualFile file = createChildData(myPrjDir1, "a.txt");

    FileManagerEx fileManager = myPsiManager.getFileManagerEx();
    fileManager.findFile(file);

    WriteCommandAction.writeCommandAction(getProject()).run(() -> delete(file));


    fileManager.checkConsistency();
  }

  public void testDeleteDir() {
    final VirtualFile dir = createChildDirectory(myPrjDir1, "dir");
    VirtualFile dir1 = createChildDirectory(dir, "dir1");
    VirtualFile file = createChildData(dir1, "a.txt");

    FileManagerEx fileManager = myPsiManager.getFileManagerEx();
    fileManager.findFile(file);

    WriteCommandAction.writeCommandAction(getProject()).run(() -> delete(dir));


    fileManager.checkConsistency();
  }

  public void testChangeFileTypeOnRename() throws IOException {
    final VirtualFile file = createChildData(myPrjDir1, "aaa.txt");

    FileManagerEx fileManager = myPsiManager.getFileManagerEx();
    fileManager.findFile(file);

    WriteCommandAction.writeCommandAction(getProject()).run(() -> file.rename(null, "bbb.jsp"));


    fileManager.checkConsistency();
  }

  public void testIgnoreFileOnRename() throws IOException {
    final VirtualFile file = createChildData(myPrjDir1, "aaa.txt");

    FileManagerEx fileManager = myPsiManager.getFileManagerEx();
    fileManager.findFile(file);

    WriteCommandAction.writeCommandAction(getProject()).run(() -> file.rename(null, "CVS"));


    fileManager.checkConsistency();
  }

  public void testIgnoreDirOnRename() throws IOException {
    final VirtualFile dir = createChildDirectory(myPrjDir1, "dir");
    VirtualFile dir1 = createChildDirectory(dir, "dir1");
    VirtualFile file = createChildData(dir, "aaa.txt");

    FileManagerEx fileManager = myPsiManager.getFileManagerEx();
    fileManager.findDirectory(dir);
    fileManager.findDirectory(dir1);
    fileManager.findFile(file);

    WriteCommandAction.writeCommandAction(getProject()).run(() -> dir.rename(null, "CVS"));


    fileManager.checkConsistency();
  }

  public void testMoveUnderExcluded() throws IOException {
    final VirtualFile dir = createChildDirectory(myPrjDir1, "dir");

    VirtualFile file2 = createChildData(myPrjDir2, "bbb.txt");
    VirtualFile dir2 = createChildDirectory(myPrjDir2, "dir2");

    FileManagerEx fileManager = myPsiManager.getFileManagerEx();
    assertNotNull(fileManager.findDirectory(dir));

    PsiFile psiFile2 = fileManager.findFile(file2);
    PsiDirectory psiDir2 = fileManager.findDirectory(dir2);

    WriteCommandAction.writeCommandAction(getProject()).run(() -> dir.move(null, myExcludedDir1));


    PsiFile _psiFile2 = fileManager.findFile(file2);
    assertEquals(psiFile2, _psiFile2);
    PsiDirectory _psiDir2 = fileManager.findDirectory(dir2);
    assertEquals(psiDir2, _psiDir2);

    fileManager.checkConsistency();
  }

  public void testAddExcludedDir() {
    VirtualFile dir1 = createChildDirectory(createChildDirectory(myPrjDir1, "dir"), "dir1");

    VirtualFile file2 = createChildData(myPrjDir2, "bbb.txt");
    VirtualFile dir2 = createChildDirectory(myPrjDir2, "dir2");

    FileManagerEx fileManager = myPsiManager.getFileManagerEx();
    assertNotNull(fileManager.findDirectory(dir1));

    PsiFile psiFile2 = fileManager.findFile(file2);
    PsiDirectory psiDir2 = fileManager.findDirectory(dir2);
    assertNotNull(psiDir2);
    PsiTestUtil.addExcludedRoot(myModule, dir1);
    PsiFile _psiFile2 = fileManager.findFile(file2);
    assertSame(psiFile2, _psiFile2);
    PsiDirectory _psiDir2 = fileManager.findDirectory(dir2);
    assertNotNull(_psiDir2);
    assertEquals(psiDir2, _psiDir2);

    fileManager.checkConsistency();
  }

  public void testChangeFileTypes() {
    final VirtualFile file = createChildData(myPrjDir1, "aaa.txt");

    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      FileTypeManagerImpl fileTypeManager = (FileTypeManagerImpl)FileTypeManager.getInstance();
      try {
        FileManagerEx fileManager = myPsiManager.getFileManagerEx();
        fileManager.findFile(file);

        fileTypeManager.removeAssociation(FileTypes.PLAIN_TEXT, new ExtensionFileNameMatcher("txt"));
        fileTypeManager.associate(JavaFileType.INSTANCE, new ExtensionFileNameMatcher("txt"));

        fileManager.checkConsistency();
      }
      finally {
        fileTypeManager.associate(FileTypes.PLAIN_TEXT, new ExtensionFileNameMatcher("txt"));
        fileTypeManager.removeAssociation(JavaFileType.INSTANCE, new ExtensionFileNameMatcher("txt"));
      }
    });
  }

  public void testFindClass1() {
    VirtualFile packDir = createChildDirectory(mySrcDir1, "pack");
    final VirtualFile file1 = createChildData(packDir, "A.java");
    final VirtualFile file2 = createChildData(mySrcDir2, "B.java");
    ApplicationManager.getApplication().runWriteAction(() -> {
      setFileText(file1, "package pack; class A{ class Inner{ class InnerInner }}");
      setFileText(file2, "class B{ }");
    });


    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    JavaFileManager fileManager = JavaFileManager.getInstance(myProject);
    PsiClass aClass1 = fileManager.findClass("pack.A", GlobalSearchScope.allScope(myProject));
    assertNotNull(aClass1);
    assertEquals("pack.A", aClass1.getQualifiedName());

    PsiClass aClass2 = fileManager.findClass("B", GlobalSearchScope.allScope(myProject));
    assertNotNull(aClass2);
    assertEquals("B", aClass2.getQualifiedName());

    PsiClass aClass3 = fileManager.findClass("pack.A.Inner", GlobalSearchScope.allScope(myProject));
    assertNotNull(aClass3);
    assertEquals("pack.A.Inner", aClass3.getQualifiedName());

    PsiClass aClass4 = fileManager.findClass("pack.A.Inner.InnerInner", GlobalSearchScope.allScope(myProject));
    assertNotNull(aClass4);
    assertEquals("pack.A.Inner.InnerInner", aClass4.getQualifiedName());
  }

  public void testDeleteRootFile() {
    WriteCommandAction.writeCommandAction(getProject()).run(() -> delete(myPrjDir1));


    FileManagerEx fileManager = myPsiManager.getFileManagerEx();
    fileManager.checkConsistency();
  }

  public void testModifyJar() throws Exception {
    String jarPath = myPrjDir1.getPath().replace('/', File.separatorChar) + File.separatorChar + "test.jar";
    try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jarPath))) {
      out.putNextEntry(new ZipEntry("Test.java"));
      out.write("class Text{}".getBytes(StandardCharsets.UTF_8));
    }
    LocalFileSystem.getInstance().refreshAndFindFileByPath(jarPath);

    VirtualFile jarVFile =
      JarFileSystem.getInstance().findFileByPath(jarPath.replace(File.separatorChar, '/') + JarFileSystem.JAR_SEPARATOR);

    FileManagerEx fileManager = myPsiManager.getFileManagerEx();

    ModuleRootModificationUtil.addModuleLibrary(myModule, "myLib", Collections.emptyList(), Collections.singletonList(jarVFile.getUrl()));

    fileManager.checkConsistency();

    PsiDirectory psiDir = myPsiManager.findDirectory(jarVFile);
    PsiJavaFile psiFile = (PsiJavaFile)psiDir.findFile("Test.java");
    PsiClass[] classes = psiFile.getClasses();
    assertEquals(1, classes.length);
    final long oldTimestamp = new File(jarPath).lastModified();

    Thread.sleep(DELAY_FOR_JAR_MODIFICATION);

    try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jarPath))) {
      out.putNextEntry(new ZipEntry("Test1.java"));
      out.write("class Test1{}".getBytes(StandardCharsets.UTF_8));
    }
    final long newTimestamp = new File(jarPath).lastModified();

    assertFalse(oldTimestamp == newTimestamp);

    VfsTestUtil.syncRefresh();
    UIUtil.dispatchAllInvocationEvents();

    fileManager.checkConsistency();

    myJavaFacade.findClass("Test");
    myJavaFacade.findClass("Test1");

    assertFalse(psiFile.isValid());

    VirtualFile jarVFile1 =
      JarFileSystem.getInstance().findFileByPath(jarPath.replace(File.separatorChar, '/') + JarFileSystem.JAR_SEPARATOR);
    PsiDirectory psiDir1 = myPsiManager.findDirectory(jarVFile1);
    psiDir1.getFiles();
    PsiFile file1 = psiDir1.findFile("Test1.java");
    if (!(file1 instanceof PsiJavaFile)) {
      System.err.println("Wrong file: " + file1);
      System.err.println("Java ext file type " + FileTypeManager.getInstance().getFileTypeByExtension("java"));
      System.err.println("Java assocs:" + FileTypeManager.getInstance().getAssociations(JavaFileType.INSTANCE));
      System.err.println("Plain text assocs" + FileTypeManager.getInstance().getAssociations(FileTypes.PLAIN_TEXT));
      System.err.println("File path: " + file1.getVirtualFile().getPresentableUrl());
      fail();
    }
    PsiJavaFile psiFile1 = (PsiJavaFile)file1;
    PsiClass[] classes1 = psiFile1.getClasses();
    assertEquals(1, classes1.length);
  }

  public void testReloadFromDisk() {
    VirtualFile file = createChildData(mySrcDir1, "file.java");
    final String DISK_CONTENT = "class A{}";
    setBinaryContent(file, DISK_CONTENT.getBytes(StandardCharsets.UTF_8));

    final PsiFile psiFile = myPsiManager.findFile(file);

    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      try {
        ApplicationManager.getApplication().runWriteAction(() -> {
          CodeStyleManager.getInstance(myProject).reformat(psiFile);
          PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
          myPsiManager.reloadFromDisk(psiFile);
        });
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }, "", null);

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assertEquals(DISK_CONTENT, psiFile.getText());
    assertEquals(file.getModificationStamp(), psiFile.getViewProvider().getModificationStamp());
  }

  // timestamps should differ by at least 2 sec (see JarFileInfo.initZipFile())
  // [dsl] unfortunately, 2000 is too small a value for Linux
  private static final int DELAY_FOR_JAR_MODIFICATION = SystemInfo.isWindows ? 3000 : 10000;
}
