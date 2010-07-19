package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.testFramework.InspectionTestCase;

/**
 * @author max
 */
public class UnusedDeclarationTest extends InspectionTestCase {
  private UnusedDeclarationInspection myTool;

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  protected void setUp() throws Exception {
    super.setUp();
    myTool = new UnusedDeclarationInspection();
  }

  private void doTest() throws Exception {
    doTest("deadCode/" + getTestName(true), myTool);
  }

  public void testSCR6067() throws Exception {
    boolean old = myTool.ADD_NONJAVA_TO_ENTRIES;
    myTool.ADD_NONJAVA_TO_ENTRIES = false;
    doTest();
    myTool.ADD_NONJAVA_TO_ENTRIES = old;
  }

  public void testsingleton() throws Exception {
    boolean old = myTool.ADD_NONJAVA_TO_ENTRIES;
    myTool.ADD_NONJAVA_TO_ENTRIES = false;
    doTest();
    myTool.ADD_NONJAVA_TO_ENTRIES = old;
  }

  public void testSCR9690() throws Exception {
    boolean old = myTool.ADD_NONJAVA_TO_ENTRIES;
    myTool.ADD_NONJAVA_TO_ENTRIES = false;
    doTest();
    myTool.ADD_NONJAVA_TO_ENTRIES = old;
  }

  public void testFormUsage() throws Exception {
    boolean old = myTool.ADD_NONJAVA_TO_ENTRIES;
    myTool.ADD_NONJAVA_TO_ENTRIES = false;
    doTest();
    myTool.ADD_NONJAVA_TO_ENTRIES = old;
  }

  public void testSserializable() throws Exception {
    doTest();
  }

  public void testpackageLocal() throws Exception {
    doTest();
  }

  public void testreachableFromMain() throws Exception{
    boolean old = myTool.ADD_MAINS_TO_ENTRIES;
    myTool.ADD_MAINS_TO_ENTRIES = true;
    doTest();
    myTool.ADD_MAINS_TO_ENTRIES = old;
  }

  public void testmutableCalls() throws Exception{
    doTest();
  }

  public void teststaticMethods() throws Exception{
    doTest();
  }

  //-------------- suppressed ----------------

  private void doTest15() throws Exception {
    final JavaPsiFacade facade = getJavaFacade();
    final LanguageLevel effectiveLanguageLevel = LanguageLevelProjectExtension.getInstance(facade.getProject()).getLanguageLevel();
    LanguageLevelProjectExtension.getInstance(facade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
    doTest();
    LanguageLevelProjectExtension.getInstance(facade.getProject()).setLanguageLevel(effectiveLanguageLevel);
  }

  public void testsuppress() throws Exception{
    doTest15();
  }

  public void testsuppress1() throws Exception {
    doTest15();
  }

  public void testsuppress2() throws Exception {
    doTest15();
  }

  public void testchainOfSuppressions() throws Exception{
    doTest15();
  }

  public void testreachableFromXml() throws Exception {
    doTest();
  }

  public void testchainOfCalls() throws Exception {
    doTest();
  }

  public void testreachableFromFieldInitializer() throws Exception {
    doTest();
  }

  public void testreachableFromFieldArrayInitializer() throws Exception {
    doTest();
  }

  public void testconstructorReachableFromFieldInitializer() throws Exception {
    doTest();
  }

  public void testadditionalAnnotations() throws Exception {
    final String testAnnotation = "Annotated";
    myTool.ADDITIONAL_ANNOTATIONS.add(testAnnotation);
    try {
      doTest();
    }
    finally {
      myTool.ADDITIONAL_ANNOTATIONS.remove(testAnnotation);
    }
  }

  public void testannotationInterface() throws Exception {
    doTest15();
  }

  public void testjunitEntryPoint() throws Exception {
    doTest();
  }
  
  public void testjunitEntryPointCustomRunWith() throws Exception {
    doTest();
  }

  public void testconstructorCalls() throws Exception {
    doTest();
  }

  public void testconstructorCalls1() throws Exception {
    doTest();
  }

  public void testnonJavaReferences() throws Exception {
    doTest();
  }

  public void testenumInstantiation() throws Exception {
    doTest();
  }
}
