package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;

public class MoveClassTest extends CodeInsightTestCase {
  public void testContextChange() throws Exception{
    doTest("contextChange1", new String[]{"pack1.Class1"}, "pack2");
    doTest("contextChange2", new String[]{"pack1.Class1"}, "pack2");
  }

  public void testMoveMultiple() throws Exception{
    doTest("moveMultiple1", new String[]{"pack1.Class1", "pack1.Class2"}, "pack2");
  }

  public void testSecondaryClass() throws Exception{
    doTest("secondaryClass", new String[]{"pack1.Class2"}, "pack1");
  }

  public void testStringsAndComments() throws Exception{
    doTest("stringsAndComments", new String[]{"pack1.Class1"}, "pack2");
  }

  public void testStringsAndComments2() throws Exception{
    doTest("stringsAndComments2", new String[]{"pack1.AClass"}, "pack2");
  }

  public void testNonJava() throws Exception{
    doTest("nonJava", new String[]{"pack1.Class1"}, "pack2");
  }

  /* IMPLEMENT: getReferences() in JspAttributeValueImpl should be dealed with (soft refs?)

  public void testJsp() throws Exception{
    doTest("jsp", new String[]{"pack1.TestClass"}, "pack2");
  }
  */

  public void testLocalClass() throws Exception{
    doTest("localClass", new String[]{"pack1.A"}, "pack2");
  }

  public void testClassAndSecondary() throws Exception{
    try {
      doTest("classAndSecondary", new String[]{"pack1.Class1", "pack1.Class2"}, "pack2");
      fail("Conflicts expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("A package-local class <b><code>Class2</code></b> will no longer be accessible from field <b><code>User.class2</code></b>", e.getMessage());
    }
  }

  public void testIdeadev27996() throws Exception {
    doTest("ideadev27996", new String[] { "pack1.X" }, "pack2");
  }

  public void testUnusedImport() throws Exception {
    doTest("unusedImport", new String[]{"p2.F2"}, "p1");
  }

  private void doTest(String testName, String[] classNames, String newPackageName) throws Exception{
    String root = JavaTestUtil.getJavaTestDataPath() + "/refactoring/moveClass/" + testName;

    String rootBefore = root + "/before";
    PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk17());
    VirtualFile rootDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, rootBefore, myFilesToDelete);

    performAction(classNames, newPackageName);

    String rootAfter = root + "/after";
    VirtualFile rootDir2 = LocalFileSystem.getInstance().findFileByPath(rootAfter.replace(File.separatorChar, '/'));
    myProject.getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    IdeaTestUtil.assertDirectoriesEqual(rootDir2, rootDir, IdeaTestUtil.CVS_FILE_FILTER);
  }

  private void performAction(String[] classNames, String newPackageName) throws Exception{
    final PsiClass[] classes = new PsiClass[classNames.length];
    for(int i = 0; i < classes.length; i++){
      String className = classNames[i];
      classes[i] = myJavaFacade.findClass(className, GlobalSearchScope.projectScope(getProject()));
      assertNotNull("Class " + className + " not found", classes[i]);
    }

    PsiPackage aPackage = JavaPsiFacade.getInstance(myPsiManager.getProject()).findPackage(newPackageName);
    assertNotNull("Package " + newPackageName + " not found", aPackage);
    final PsiDirectory[] dirs = aPackage.getDirectories();
    assertEquals(dirs.length, 1);

    new MoveClassesOrPackagesProcessor(myProject, classes,
                                       new SingleSourceRootMoveDestination(PackageWrapper.create(JavaDirectoryService
                                         .getInstance().getPackage(dirs[0])), dirs[0]),
                                       true, true, null).run();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();
  }
}
