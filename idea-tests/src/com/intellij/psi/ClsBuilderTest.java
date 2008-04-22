/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
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
    return JavaSdkImpl.getMockJdk15("1.5");
  }

  public void testSimple() throws Exception {
    doTest("java/util/List.class", "UtilList.txt");
  }

  public void testWithAnnotations() throws Exception {
    doTest("org/jetbrains/annotations/Nullable.class", "Nullable.txt");
  }

  public void testManyGenerics() throws Exception {
    doTest("java/util/Collections.class", "UtilCollections.txt");
  }

  public void testHashMap() throws Exception {
    doTest("java/util/HashMap.class", "UtilHashMap.txt");
  }

  public void testMap() throws Exception {
    doTest("java/util/Map.class", "UtilMap.txt");
  }

  public void testTimeUnit() throws Exception {
    doTest("java/util/concurrent/TimeUnit.class", "TimeUnit.txt");
  }

  private void doTest(final String classname, final String goldFile) throws IOException, ClsFormatException {
    VirtualFile vFile = findFile(classname);

    final PsiFileStub stub = ClsStubBuilder.build(vFile, vFile.contentsToByteArray());
    final String butWas = ((StubBase)stub).printTree();

    final String goldFilePath = PathManagerEx.getTestDataPath() + "/psi/cls/stubBuilder/" + goldFile;
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