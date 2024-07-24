package com.intellij.codeInspection.tests.kotlin;

import com.intellij.java.codeInspection.AbstractUnusedDeclarationTest;
import com.intellij.java.codeInspection.UnusedDeclarationInspectionTest;
import com.intellij.openapi.application.ex.PathManagerEx;
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider;

public abstract class KotlinUnusedDeclarationTest extends AbstractUnusedDeclarationTest implements KotlinPluginModeProvider {
  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getTestDataPath(UnusedDeclarationInspectionTest.class) + "/inspection/jvm";
  }

  public void testSingleton() {
    myTool.ADD_NONJAVA_TO_ENTRIES = false;
    doTest();
  }

  //TODO
  public void testDefaultConstructor() {
    doTest();
  }

  public void testReferenceFromSuperTypeList() {
    doTest();
  }

  public void testImplementedInterface() {
    doTest();
  }

  public void testReachableFromMain() {
    myTool.ADD_MAINS_TO_ENTRIES = true;
    doTest();
  }

  public void testMutableCalls() {
    doTest();
  }

  public void testOverridingProperty() {
    myTool.getSharedLocalInspectionTool().PARAMETER = true;
    myTool.getSharedLocalInspectionTool().LOCAL_VARIABLE = false;
    doTest("deadCode/" + getTestName(true), myToolWrapper);
  }

  public void testPrimaryConstructor() {
    doTest();
  }

  public void testPrimaryConstructor2() {
    myTool.getSharedLocalInspectionTool().PARAMETER = true;
    myTool.getSharedLocalInspectionTool().LOCAL_VARIABLE = false;
    doTest("deadCode/" + getTestName(true), myToolWrapper);
  }
  
  public void testReceiverParameter() {
    myTool.getSharedLocalInspectionTool().PARAMETER = true;
    myTool.getSharedLocalInspectionTool().LOCAL_VARIABLE = false;
    doTest("deadCode/" + getTestName(true), myToolWrapper);
  }

  public void testOperatorInWhen() {
    doTest("deadCode/" + getTestName(true), myToolWrapper);
  }

  public void testCallableParameter() {
    myTool.getSharedLocalInspectionTool().PARAMETER = true;
    myTool.getSharedLocalInspectionTool().LOCAL_VARIABLE = false;
    doTest("deadCode/" + getTestName(true), myToolWrapper);
  }

  public void testCallableProperty() {
    myTool.getSharedLocalInspectionTool().FIELD = true;
    doTest("deadCode/" + getTestName(true), myToolWrapper);
  }

  public void testUsagesInCallableReceiver() {
    myTool.getSharedLocalInspectionTool().PARAMETER = true;
    doTest("deadCode/" + getTestName(true), myToolWrapper);
  }

  public void testUsagesInClassLiteral() {
    myTool.getSharedLocalInspectionTool().PARAMETER = true;
    doTest("deadCode/" + getTestName(true), myToolWrapper);
  }

  public void testMainParameter() {
    myTool.getSharedLocalInspectionTool().PARAMETER = true;
    myTool.getSharedLocalInspectionTool().LOCAL_VARIABLE = false;
    doTest("deadCode/" + getTestName(true), myToolWrapper);
  }

  public void testStaticMethods() {
    doTest();
  }

  public void testChainOfCalls() {
    doTest();
  }

  public void testReachableFromFieldInitializer() {
    doTest();
  }

  public void testReachableFromFieldArrayInitializer() {
    doTest();
  }

  public void testJunitEntryPoint() {
    doTest();
  }

  public void testConstructorCalls() {
    doTest();
  }

  public void testPropertyReference() {
    doTest();
  }

  public void testReferenceInLambda() {
    doTest();
  }

  public void testNonJvmReferences() {
    doTest();
  }

  //TODO uast kotlin enum support
  public void _testEnumInstantiation() {
    doTest();
  }

  public void _testEnumValues() {
    doTest();
  }

  public void testUsagesInAnonymous() {
    doTest();
  }

  public void testClassLiteralRef() {
    doTest();
  }

  public void testTopLevelFunction() {
    doTest();
  }
}
