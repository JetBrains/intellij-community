package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassToInnerProcessor;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;

import java.io.File;

/**
 * @author yole
 */
public class MoveClassToInnerTest extends CodeInsightTestCase {
  public void testContextChange1() throws Exception {
    doTest(new String[] { "pack1.Class1" }, "pack2.A");
  }

  public void testContextChange2() throws Exception {
    doTest(new String[] { "pack1.Class1" }, "pack2.A");
  }

  public void testInnerImport() throws Exception {
    doTest(new String[] { "pack1.Class1" }, "pack2.A");
  }

  public void testInsertInnerClassImport() throws Exception {
    final boolean imports = CodeStyleSettingsManager.getSettings(myProject).INSERT_INNER_CLASS_IMPORTS;
    try {
      CodeStyleSettingsManager.getSettings(myProject).INSERT_INNER_CLASS_IMPORTS = true;
      doTest(new String[] { "pack1.Class1" }, "pack2.A");
    }
    finally {
      CodeStyleSettingsManager.getSettings(myProject).INSERT_INNER_CLASS_IMPORTS = imports;
    }
  }

  public void testSimultaneousMove() throws Exception {
    doTest(new String[] { "pack1.Class1", "pack0.Class0" }, "pack2.A");
  }

  public void testMoveMultiple1() throws Exception {
    doTest(new String[] { "pack1.Class1", "pack1.Class2" }, "pack2.A");
  }

  public void testRefToInner() throws Exception {
    doTest(new String[] { "pack1.Class1" }, "pack2.A");
  }

  public void testRefToConstructor() throws Exception {
    doTest(new String[] { "pack1.Class1" }, "pack2.A");
  }

  public void testSecondaryClass() throws Exception {
    doTest(new String[] { "pack1.Class2" }, "pack1.User");
  }

  public void testStringsAndComments() throws Exception {
    doTest(new String[] { "pack1.Class1" }, "pack1.A");
  }

  public void testStringsAndComments2() throws Exception {
    doTest(new String[] { "pack1.Class1" }, "pack1.A");
  }

  public void testNonJava() throws Exception {
    doTest(new String[] { "pack1.Class1" }, "pack1.A");
  }

  public void testLocallyUsedPackageLocalToPublicInterface() throws Exception {
    doTest(new String[]{"pack1.Class1"}, "pack2.A");
  }

  public void testPackageLocalClass() throws Exception {
    doTestConflicts("pack1.Class1", "pack2.A", "Field <b><code>Class1.c2</code></b> uses a package-local class <b><code>pack1.Class2</code></b>.");
  }

  public void testMoveIntoPackageLocalClass() throws Exception {
    doTestConflicts("pack1.Class1", "pack2.A", "Class <b><code>Class1</code></b> will no longer be accessible from field <b><code>Class2.c1</code></b>");
  }

  public void testMoveOfPackageLocalClass() throws Exception {
    doTestConflicts("pack1.Class1", "pack2.A", "Class <b><code>Class1</code></b> will no longer be accessible from field <b><code>Class2.c1</code></b>");
  }

  public void testMoveIntoPrivateInnerClass() throws Exception {
    doTestConflicts("pack1.Class1", "pack1.A.PrivateInner", "Class <b><code>Class1</code></b> will no longer be accessible from field <b><code>Class2.c1</code></b>");
  }

  public void testMoveWithPackageLocalMember() throws Exception {
    doTestConflicts("pack1.Class1", "pack2.A", "Method <b><code>Class1.doStuff()</code></b> will no longer be accessible from method <b><code>Class2.test()</code></b>");
  }

  public void testDuplicateInner() throws Exception {
    doTestConflicts("pack1.Class1", "pack2.A", "Class <b><code>pack2.A</code></b> already contains an inner class named <b><code>Class1</code></b>");
  }

  private void doTest(String[] classNames, String targetClassName) throws Exception{
    VirtualFile rootDir = prepareTest();

    performAction(classNames, targetClassName);

    String rootAfter = getRoot() + "/after";
    VirtualFile rootDir2 = LocalFileSystem.getInstance().findFileByPath(rootAfter.replace(File.separatorChar, '/'));
    myProject.getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    IdeaTestUtil.assertDirectoriesEqual(rootDir2, rootDir, IdeaTestUtil.CVS_FILE_FILTER);
  }

  private VirtualFile prepareTest() throws Exception {
    String rootBefore = getRoot() + "/before";
    PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk17());
    return PsiTestUtil.createTestProjectStructure(myProject, myModule, rootBefore, myFilesToDelete);
  }

  private String getRoot() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/moveClassToInner/" + getTestName(true);
  }

  private void doTestConflicts(String className, String targetClassName, String... expectedConflicts) throws Exception {
    prepareTest();
    PsiClass classToMove = myJavaFacade.findClass(className, ProjectScope.getAllScope(myProject));
    PsiClass targetClass = myJavaFacade.findClass(targetClassName, ProjectScope.getAllScope(myProject));
    MoveClassToInnerProcessor processor = new MoveClassToInnerProcessor(myProject, new PsiClass[]{classToMove}, targetClass, true, true, null);
    UsageInfo[] usages = processor.findUsages();
    MultiMap<PsiElement,String> conflicts = processor.getConflicts(usages);
    assertSameElements(conflicts.values() , expectedConflicts);
  }

  private void performAction(String[] classNames, String targetClassName) throws Exception{
    final PsiClass[] classes = new PsiClass[classNames.length];
    for(int i = 0; i < classes.length; i++){
      String className = classNames[i];
      classes[i] = myJavaFacade.findClass(className, ProjectScope.getAllScope(myProject));
      assertNotNull("Class " + className + " not found", classes[i]);
    }

    PsiClass targetClass = myJavaFacade.findClass(targetClassName, ProjectScope.getAllScope(myProject));
    assertNotNull(targetClass);

    new MoveClassToInnerProcessor(myProject, classes, targetClass, true, true, null).run();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();
  }
}
