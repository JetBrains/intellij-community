package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.varScopeCanBeNarrowed.FieldCanBeLocalInspection;
import com.intellij.testFramework.InspectionTestCase;

/**
 * @author ven
 */
public class FieldCanBeLocalTest extends InspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() throws Exception {
    doTest("fieldCanBeLocal/" + getTestName(true), new FieldCanBeLocalInspection());
  }

  public void testSimple () throws Exception { doTest(); }

  public void testTwoMethods () throws Exception { doTest(); }
  public void testTwoMethodsNotIgnoreMultipleMethods () throws Exception {
    final FieldCanBeLocalInspection inspection = new FieldCanBeLocalInspection();
    inspection.IGNORE_FIELDS_USED_IN_MULTIPLE_METHODS = false;
    doTestConfigured(inspection); 
  }

  public void testConstructor () throws Exception { doTest(); }
  public void testConstructorThisRef() throws Exception { doTest(); }
  public void testStaticFinal() throws Exception { doTest(); }
  public void testStaticAccess() throws Exception { doTest(); }
  public void testInnerClassConstructor() throws Exception { doTest(); }
  public void testLocalVar2InnerClass() throws Exception { doTest(); }
  public void testStateField() throws Exception { doTest(); }
  public void testLocalStateVar2InnerClass() throws Exception { doTest(); }
  public void testNotConstantInitializer() throws Exception {doTest();}
  public void testInnerClassFieldInitializer() throws Exception {doTest();}
  public void testFieldUsedInConstantInitialization() throws Exception {doTest();}
  public void testFieldWithImmutableType() throws Exception {doTest();}
  public void testFieldUsedForWritingInLambda() throws Exception {doTest();}
  public void testStaticQualifiedFieldAccessForWriting() throws Exception {doTest();}
  public void testFieldReferencedFromAnotherObject() throws Exception {doTest();}
  public void testIgnoreAnnotated() throws Exception {
    final FieldCanBeLocalInspection inspection = new FieldCanBeLocalInspection();
    doTestConfigured(inspection);
  }

  public void testFieldUsedInAnotherMethodAsQualifier() throws Exception {
    doTest();
  }

  private void doTestConfigured(FieldCanBeLocalInspection inspection) {
    inspection.EXCLUDE_ANNOS.add(Deprecated.class.getName());
    doTest("fieldCanBeLocal/" + getTestName(true), inspection);
  }
}
