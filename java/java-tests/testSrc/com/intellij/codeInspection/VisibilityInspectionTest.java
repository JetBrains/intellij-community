package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.visibility.VisibilityInspection;
import com.intellij.testFramework.InspectionTestCase;

public class VisibilityInspectionTest extends InspectionTestCase {
  private final VisibilityInspection myTool = new VisibilityInspection();

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() throws Exception {
    doTest("visibility/" + getTestName(false), myTool);
  }

  public void testinnerConstructor() throws Exception {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = false;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;

    doTest();
  }

  public void testpackageLevelTops() throws Exception {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = false;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = false;

    doTest();
  }

  public void testSCR5008() throws Exception {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;

    doTest();
  }

  public void testSCR6856() throws Exception {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;

    doTest();
  }

  public void testSCR11792() throws Exception {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;

    doTest();
  }

  public void testIDEADEV10312() throws Exception {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = false;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = false;
    doTest();
  }

  public void testIDEADEV10883() throws Exception {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = false;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = false;
    doTest();
  }

  public void testDefaultConstructor() throws Exception {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = false;
    doTest("visibility/defaultConstructor", myTool, false, true);
  }

  public void testImplicitConstructor() throws Exception {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = false;
    doTest("visibility/implicitConstructor", myTool, false, true);
  }

  public void testEnumConstants() throws Exception {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = false;
    doTest("visibility/enumConstantsVisibility", myTool, false, true);
  }

  public void testUsagesFromAnnotations() throws Exception {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = false;
    doTest("visibility/annotationUsages", myTool, false, true);
  }

  public void testTypeArguments() throws Exception {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = false;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = false;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = false;
    doTest("visibility/typeArguments", myTool, false, true);
  }
  
  public void testUsedFromAnnotationsExtendsList() throws Exception {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;
    doTest("visibility/usedFromAnnotationsExtendsList", myTool, false, true);
  }
}
