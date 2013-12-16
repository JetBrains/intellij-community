package com.intellij.codeInspection;

import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.InspectionTestCase;

public class RedundantCast15Test extends InspectionTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(myJavaFacade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
    ModuleRootModificationUtil.setModuleSdk(getModule(), getTestProjectSdk());
  }

  private void doTest() throws Exception {
    final LocalInspectionToolWrapper toolWrapper = new LocalInspectionToolWrapper(new RedundantCastInspection());
    doTest("redundantCast/generics/" + getTestName(false), toolWrapper, "java 1.5");
  }

  public void testBoxingInRef() throws Exception { doTest(); }

  public void testBoxingInConditional() throws Exception { doTest(); }

  public void testInference1() throws Exception { doTest(); }

  public void testInference2() throws Exception { doTest(); }

  public void testInference3() throws Exception { doTest(); }

  public void testNullInVarargsParameter() throws Exception { doTest(); }

  public void testWrapperToPrimitiveCast() throws Exception { doTest(); }

  public void testEnumConstant() throws Exception { doTest(); }

  public void testRawCast() throws Exception { doTest();}
  public void testCastToUnboundWildcard() throws Exception { doTest();}

  public void testRawCastsToAvoidIncompatibility() throws Exception { doTest();}

  public void testIDEA22899() throws Exception { doTest();}
  public void testRawCast1() throws Exception { doTest();}
  public void testInferenceFromCast() throws Exception { doTest();}
  public void testGetClassProcessing() throws Exception { doTest();}
  public void testInstanceOfChecks() throws Exception { doTest();}
  public void testForEachValue() throws Exception { doTest();}
  public void testCaseThrowable() throws Exception { doTest();}
  public void testSafeTempVarName() throws Exception { doTest();}

  public void testTypeParameterAccessChecksJava7() throws Exception {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_7, getModule(), getTestRootDisposable());
    doTest();
  }

  public void testIgnore() throws Exception {
    final RedundantCastInspection castInspection = new RedundantCastInspection();
    castInspection.IGNORE_ANNOTATED_METHODS = true;
    castInspection.IGNORE_SUSPICIOUS_METHOD_CALLS = true;
    final LocalInspectionToolWrapper tool = new LocalInspectionToolWrapper(castInspection);
    doTest("redundantCast/generics/" + getTestName(false), tool, "java 1.5");
  }
}