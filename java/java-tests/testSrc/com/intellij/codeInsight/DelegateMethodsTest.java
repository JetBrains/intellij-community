package com.intellij.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.generation.GenerateDelegateHandler;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class DelegateMethodsTest extends LightCodeInsightTestCase {

  private static final String BASE_PATH = "/codeInsight/delegateMethods/";

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testMethodWithJavadoc () throws Exception {
    doTest("1");
  }

  public void testStaticMemberWithNonStaticField() throws Exception {
      doTest(getTestName(false));
  }

  public void testTypeParam() throws Exception {
    doTest(getTestName(false));
  }

  public void testExistingMethodWithAnnotation() throws Exception {
    doTest(getTestName(false));
  }

  public void testDelegateToContainingClassField() throws Exception {
    doTest(getTestName(false));
  }
  
  public void testDelegateFromStaticClassField() throws Exception {
      doTest(getTestName(false));
  }
  
  public void testCopyJavadoc() throws Exception {
    String testName = getTestName(false);
    configureByFile(BASE_PATH + "before" + testName + ".java");
    final GenerateDelegateHandler handler = new GenerateDelegateHandler();
    try {
      handler.setToCopyJavaDoc(true);
      handler.invoke(getProject(), getEditor(), getFile());
    }
    finally {
      handler.setToCopyJavaDoc(false);
    }
    checkResultByFile(BASE_PATH + "after" + testName + ".java");
  }
  
  public void testSuperSubstitution() throws Exception {
    doTest(getTestName(false));
  }
  
  public void testCopyAnnotationWithParams() throws Exception {
    doTest(getTestName(false));
  }

  public void testMultipleOverrideAnnotations() throws Exception {
    doTest(getTestName(false));
  }

  public void testStripSuppressWarningsAnnotation() throws Exception {
    doTest(getTestName(false));
  }

  public void testDoNotOverrideFinal() throws Exception {
    doTest(getTestName(false));
  }

  public void testAllowDelegateToFinal() throws Exception {
    doTest(getTestName(false));
  }

  public void testDelegateWithSubstitutionOverrides() throws Exception {
    doTest(getTestName(false));
  }

  public void testDelegateWithSubstitutionNoOverrides() throws Exception {
    doTest(getTestName(false));
  }

  private void doTest(String testName) throws Exception {
    configureByFile(BASE_PATH + "before" + testName+ ".java");
    new GenerateDelegateHandler().invoke(getProject(), getEditor(), getFile());
    checkResultByFile(BASE_PATH + "after" + testName + ".java");
  }
}
