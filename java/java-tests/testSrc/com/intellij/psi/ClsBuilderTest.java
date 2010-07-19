/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.compiled.ClsStubBuilder;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.util.cls.ClsFormatException;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ClsBuilderTest extends LightIdeaTestCase {
  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk17();
  }

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

  private void doTestFromTestData() throws ClsFormatException, IOException {
    final String clsFilePath = JavaTestUtil.getJavaTestDataPath() + "/psi/cls/stubBuilder/" + getTestName(false) + ".class";
    VirtualFile clsFile = LocalFileSystem.getInstance().findFileByPath(clsFilePath);
    doTest(clsFile, getTestName(false) + ".txt");
  }

  private void doTest(final String classname) throws IOException, ClsFormatException {
    VirtualFile vFile = findFile(classname);
    doTest(vFile, getTestName(false)+".txt");
  }

  private static void doTest(VirtualFile vFile, String goldFile) throws ClsFormatException, IOException {
    final PsiFileStub stub = ClsStubBuilder.build(vFile, vFile.contentsToByteArray());
    final String butWas = ((StubBase)stub).printTree();

    final String goldFilePath = JavaTestUtil.getJavaTestDataPath() + "/psi/cls/stubBuilder/" + goldFile;
    String expected = "";
    try {
      expected = FileUtil.loadTextAndClose(new FileReader(goldFilePath));
      expected = StringUtil.convertLineSeparators(expected);
    }
    catch (FileNotFoundException e) {
      System.out.println("No expected data found at:" + goldFilePath);
      System.out.println("Creating one.");
      final FileWriter fileWriter = new FileWriter(goldFilePath);
      fileWriter.write(butWas);
      fileWriter.close();
      fail("No test data found. Created one");
    }

    assertEquals(expected, butWas);
  }

  private VirtualFile findFile(final String classname) {
    final VirtualFile[] roots = getProjectJDK().getRootProvider().getFiles(OrderRootType.CLASSES);
    for (VirtualFile root : roots) {
      VirtualFile vFile = root.findFileByRelativePath(classname);
      if (vFile != null) return vFile;
    }

    fail("Cannot file classfile for: " + classname);
    return null;
  }
}
