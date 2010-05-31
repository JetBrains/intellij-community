/*
 * User: anna
 * Date: 25-Nov-2009
 */
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.refactoring.changeSignature.JavaChangeSignatureHandler;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NonNls;

public class ChangeSignatureTargetTest extends LightCodeInsightTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testInMethodParameters() throws Exception {
    doTest("foo");
  }

  public void testInMethodArguments() throws Exception {
    doTest("foo");
  }

  public void testInClassTypeParameters() throws Exception {
    doTest("A1");
  }

  public void testInTypeArguments() throws Exception {
    doTest("A1");
  }

  @Override
  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk15("java 1.5");
  }

  private void doTest(String expectedMemberName) throws Exception {
    String basePath = "/refactoring/changeSignatureTarget/" + getTestName(true);
    @NonNls final String filePath = basePath + ".java";
    configureByFile(filePath);
    final PsiElement member = new JavaChangeSignatureHandler().findTargetMember(getFile(), getEditor());
    assertNotNull(member);
    assertEquals(expectedMemberName, ((PsiMember)member).getName());
  }
}
