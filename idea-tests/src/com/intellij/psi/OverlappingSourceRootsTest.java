package com.intellij.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.PsiTestCase;

import java.io.File;
import java.io.IOException;

public class OverlappingSourceRootsTest extends PsiTestCase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.OverlappingSourceRootsTest");

  private VirtualFile myProjectRoot;
  private VirtualFile mySourceRoot1;
  private VirtualFile mySourceRoot2;
  private VirtualFile mySourceRoot11;
  private VirtualFile mySourceRoot21;
  private VirtualFile myFile1;
  private VirtualFile myFile2;
  private VirtualFile myFile11;
  private VirtualFile myFile21;

  protected void setUp() throws Exception {
    super.setUp();

    ApplicationManager.getApplication().runWriteAction(
            new Runnable() {
              public void run() {

                try {
                  File dir = createTempDir("test");

                  myProjectRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.getPath().replace(File.separatorChar, '/'));

                  mySourceRoot1 = myProjectRoot.createChildDirectory(null, "root1");
                  mySourceRoot2 = myProjectRoot.createChildDirectory(null, "root2");
                  mySourceRoot11 = mySourceRoot1.createChildDirectory(null, "root11");
                  mySourceRoot21 = mySourceRoot2.createChildDirectory(null, "root21");

                  final ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
                  final ContentEntry contentEntry = rootModel.addContentEntry(myProjectRoot);
                  contentEntry.addSourceFolder(mySourceRoot21, false);
                  contentEntry.addSourceFolder(mySourceRoot1, false);
                  contentEntry.addSourceFolder(mySourceRoot2, false);
                  contentEntry.addSourceFolder(mySourceRoot11, false);
                  rootModel.commit();

                  myFile1 = mySourceRoot1.createChildData(null, "File1.java");
                  myFile2 = mySourceRoot2.createChildData(null, "File2.java");
                  myFile11 = mySourceRoot11.createChildData(null, "File11.java");
                  myFile21 = mySourceRoot21.createChildData(null, "File21.java");
                } catch (IOException e) {
                  LOG.error(e);
                }
              }
            }
    );
  }

  public void testFindRoot1() {
    checkFindRoot();
  }

  public void testFindRoot2() {
    checkFindRoot();
  }

  private void checkFindRoot() {
    PsiDirectory psiDir1 = myPsiManager.findDirectory(mySourceRoot1);
    PsiDirectory psiDir2 = myPsiManager.findDirectory(mySourceRoot2);
    PsiDirectory psiDir11 = myPsiManager.findDirectory(mySourceRoot11);
    PsiDirectory psiDir21 = myPsiManager.findDirectory(mySourceRoot21);

    assertEquals(psiDir1, findSourceRootDirectory(psiDir1));
    assertEquals(psiDir2, findSourceRootDirectory(psiDir2));
    assertEquals(psiDir11, findSourceRootDirectory(psiDir11));
    assertEquals(psiDir21, findSourceRootDirectory(psiDir21));

    PsiFile psiFile1 = myPsiManager.findFile(myFile1);
    PsiFile psiFile2 = myPsiManager.findFile(myFile2);
    PsiFile psiFile11 = myPsiManager.findFile(myFile11);
    PsiFile psiFile21 = myPsiManager.findFile(myFile21);

    assertEquals(psiDir1, findSourceRootDirectory(psiFile1));
    assertEquals(psiDir2, findSourceRootDirectory(psiFile2));
    assertEquals(psiDir11, findSourceRootDirectory(psiFile11));
    assertEquals(psiDir21, findSourceRootDirectory(psiFile21));
  }

  private PsiDirectory findSourceRootDirectory(PsiElement element) {
    final VirtualFile virtualFile = PsiUtil.getVirtualFile(element);
    final VirtualFile sourceRoot = ProjectRootManager.getInstance(myProject).getFileIndex().getSourceRootForFile(virtualFile);
    return myPsiManager.findDirectory(sourceRoot);
  }

  public void testFindPackage1() {
    checkFindPackage();
  }

  public void testFindPackage2() {
    checkFindPackage();
  }

  private void checkFindPackage() {
    PsiDirectory psiDir1 = myPsiManager.findDirectory(mySourceRoot1);
    PsiDirectory psiDir2 = myPsiManager.findDirectory(mySourceRoot2);
    PsiDirectory psiDir11 = myPsiManager.findDirectory(mySourceRoot11);
    PsiDirectory psiDir21 = myPsiManager.findDirectory(mySourceRoot21);

    String pack1 = JavaDirectoryService.getInstance().getPackage(psiDir1).getQualifiedName();
    String pack2 = JavaDirectoryService.getInstance().getPackage(psiDir2).getQualifiedName();
    String pack11 = JavaDirectoryService.getInstance().getPackage(psiDir11).getQualifiedName();
    String pack21 = JavaDirectoryService.getInstance().getPackage(psiDir21).getQualifiedName();

    assertEquals("", pack1);
    assertEquals("", pack2);
    assertEquals("", pack11);
    assertEquals("", pack21);
  }
}
