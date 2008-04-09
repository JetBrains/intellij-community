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

import java.io.FileReader;
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
  
  private void doTest(final String classname, final String goldFile) throws IOException, ClsFormatException {
    VirtualFile vFile = findFile(classname);

    final PsiFileStub stub = ClsStubBuilder.build(vFile, vFile.contentsToByteArray());
    final String butWas = ((StubBase)stub).printTree();

    String expected = FileUtil.loadTextAndClose(new FileReader(PathManagerEx.getTestDataPath() + "/psi/cls/stubBuilder/" + goldFile));
    expected = StringUtil.convertLineSeparators(expected);
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