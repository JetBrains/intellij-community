package com.intellij.psi;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.compiled.DefaultClsStubBuilderFactory;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.util.cls.ClsFormatException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;

/**
 * @author max
 */
public class ClsBuilderTest extends LightIdeaTestCase {
  public void testUtilList() throws Exception {
    doTest("java/util/List.class");
  }

  public void testNullable() throws Exception {
    doTest("org/jetbrains/annotations/Nullable.class");
  }

  public void testUtilCollections() throws Exception {
    doTest("java/util/Collections.class");
  }

  public void testUtilHashMap() throws Exception {
    doTest("java/util/HashMap.class");
  }

  public void testUtilMap() throws Exception {
    doTest("java/util/Map.class");
  }

  public void testTimeUnit() throws Exception {
    doTest("java/util/concurrent/TimeUnit.class");
  }

  public void testTestSuite() throws Exception {
    doTestFromTestData();
  }

  public void testDoubleTest() throws Exception {  // IDEA-53195
    doTestFromTestData();
  }

  public void testAnnotatedNonStaticInnerClassConstructor() throws Exception {
    doTestFromTestData();
  }

  public void testAnnotatedEnumConstructor() throws Exception {
    doTestFromTestData();
  }

  public void testModifiers() throws Exception {
    final String clsFilePath = JavaTestUtil.getJavaTestDataPath() + "/psi/repositoryUse/cls/pack/" + getTestName(false) + ".class";
    final VirtualFile clsFile = LocalFileSystem.getInstance().findFileByPath(clsFilePath);
    assert clsFile != null : clsFilePath;
    doTest(clsFile, getTestName(false) + ".txt");
  }

  private void doTestFromTestData() throws ClsFormatException, IOException {
    final String clsFilePath = JavaTestUtil.getJavaTestDataPath() + "/psi/cls/stubBuilder/" + getTestName(false) + ".class";
    final VirtualFile clsFile = LocalFileSystem.getInstance().findFileByPath(clsFilePath);
    assert clsFile != null : clsFilePath;
    doTest(clsFile, getTestName(false) + ".txt");
  }

  private void doTest(final String className) throws IOException, ClsFormatException {
    final VirtualFile clsFile = findFile(className);
    doTest(clsFile, getTestName(false) + ".txt");
  }

  private static void doTest(VirtualFile vFile, String goldFile) throws ClsFormatException, IOException {
    final PsiFileStub stub = (new DefaultClsStubBuilderFactory()).buildFileStub(vFile, vFile.contentsToByteArray());
    assert stub != null : vFile;
    final String butWas = ((StubBase)stub).printTree();

    final String goldFilePath = JavaTestUtil.getJavaTestDataPath() + "/psi/cls/stubBuilder/" + goldFile;
    String expected = "";
    try {
      expected = FileUtil.loadFile(new File(goldFilePath));
      expected = StringUtil.convertLineSeparators(expected);
    }
    catch (FileNotFoundException e) {
      System.out.println("No expected data found at: " + goldFilePath + ", creating one.");
      final FileWriter fileWriter = new FileWriter(goldFilePath);
      try {
        fileWriter.write(butWas);
        fileWriter.close();
      }
      finally {
        fileWriter.close();
        fail("No test data found. Created one");
      }
    }

    assertEquals(expected, butWas);
  }

  private VirtualFile findFile(final String className) {
    final VirtualFile[] roots = getProjectJDK().getRootProvider().getFiles(OrderRootType.CLASSES);
    for (VirtualFile root : roots) {
      VirtualFile vFile = root.findFileByRelativePath(className);
      if (vFile != null) return vFile;
    }

    fail("Cannot file class file for: " + className);
    return null;
  }
}
