package com.intellij.refactoring;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;

public class RenamePackageTest extends CodeInsightTestCase {
  public void testJspImport() throws Exception {
    doTest("jspImport", "pack1", "pack2");
  }

  public void testNonJava() throws Exception {
    doTest("nonJava", "com.foo", "fooNew");
  }

  public void testInBrokenXml() throws Exception {
    doTest("inBrokenXml", "somepckg", "somepckg1");
  }


  public void testJsp() throws Exception {
    doTest("jsp", "pack1", "pack2");
  }

  private void doTest(String testName, String packageName, String newPackageName) throws Exception {
    String root = PathManagerEx.getTestDataPath()+ "/refactoring/renamePackage/" + testName;

    String rootBefore = root + "/before";
    PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk17());
    VirtualFile rootDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, rootBefore, myFilesToDelete);

    performAction(packageName, newPackageName);

    String rootAfter = root + "/after";
    VirtualFile rootDir2 = LocalFileSystem.getInstance().findFileByPath(rootAfter.replace(File.separatorChar, '/'));
    IdeaTestUtil.assertDirectoriesEqual(rootDir2, rootDir, IdeaTestUtil.CVS_FILE_FILTER);
  }

  private void performAction(String packageName, String newPackageName) throws Exception {
    PsiPackage aPackage = JavaPsiFacade.getInstance(myPsiManager.getProject()).findPackage(packageName);
    assertNotNull("Package " + packageName + " not found", aPackage);

    //PsiDirectory dir = aPackage.getDirectories()[0];
    //it is now impossible to rename dir without renaming corresponding package via rename processor - move processor would be used instead
    new RenameProcessor(myProject, aPackage, newPackageName, true, true).run();
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  @Override
  protected boolean isRunInWriteAction() {
    return false;
  }
}
