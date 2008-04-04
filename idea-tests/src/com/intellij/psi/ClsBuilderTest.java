/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.compiled.ClsStubBuilder;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.impl.java.stubs.impl.PsiClassStubImpl;
import com.intellij.testFramework.LightIdeaTestCase;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;

public class ClsBuilderTest extends LightIdeaTestCase {
  public void testSimple() throws Exception {
    doTest("java/util/List.class", "UtilList.txt");
  }

  /*
  public void testManyGenerics() throws Exception {
    doTest("java/util/Collections.class", "UtilCollections.txt");
  }
  */

  public void testWithAnnotations() throws Exception {
    doTest("org/jetbrains/annotations/Nullable.class", "Nullable.txt");
  }

  private void doTest(final String classname, final String goldFile) throws IOException {
    final InputStream stream = getClass().getClassLoader().getResourceAsStream(classname);

    final byte[] bytes = FileUtil.adaptiveLoadBytes(stream);
    final PsiClassStub stub = ClsStubBuilder.build(bytes);
    final String butWas = ((PsiClassStubImpl)stub).printTree();
    String expected = FileUtil.loadTextAndClose(new FileReader(PathManagerEx.getTestDataPath() + "/psi/cls/stubBuilder/" + goldFile));
    expected = StringUtil.convertLineSeparators(expected);
    assertEquals(expected, butWas);
  }
}