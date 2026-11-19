package com.intellij.codeInspection.tests.kotlin;

import com.intellij.java.codeInspection.UnusedDeclarationFixture;
import com.intellij.java.codeInspection.UnusedDeclarationInspectionTest;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.java.codeInspection.UnusedDeclarationFixtureKt.unusedDeclarationFixture;
import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionProjectFixture;

@TestApplication
public abstract class KotlinUnusedDeclarationTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaInspectionProjectFixture();
  protected final TestFixture<UnusedDeclarationFixture> unusedDeclaration =
    unusedDeclarationFixture(projectFixture, PathManagerEx.getTestDataPath(UnusedDeclarationInspectionTest.class) + "/inspection/jvm");

  @Test
  public void testSingleton() {
    unusedDeclaration.get().getTool().ADD_NONJAVA_TO_ENTRIES = false;
    unusedDeclaration.get().doTest();
  }

  //TODO
  @Test
  public void testDefaultConstructor() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testReferenceFromSuperTypeList() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testImplementedInterface() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testReachableFromMain() {
    unusedDeclaration.get().getTool().ADD_MAINS_TO_ENTRIES = true;
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testMutableCalls() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testOverridingProperty() {
    unusedDeclaration.get().getTool().getSharedLocalInspectionTool().PARAMETER = true;
    unusedDeclaration.get().getTool().getSharedLocalInspectionTool().LOCAL_VARIABLE = false;
    unusedDeclaration.get().doTest("deadCode/" + unusedDeclaration.get().getTestName(true), unusedDeclaration.get().getToolWrapper());
  }

  @Test
  public void testPrimaryConstructor() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testPrimaryConstructor2() {
    unusedDeclaration.get().getTool().getSharedLocalInspectionTool().PARAMETER = true;
    unusedDeclaration.get().getTool().getSharedLocalInspectionTool().LOCAL_VARIABLE = false;
    unusedDeclaration.get().doTest("deadCode/" + unusedDeclaration.get().getTestName(true), unusedDeclaration.get().getToolWrapper());
  }
  
  @Test
  public void testReceiverParameter() {
    unusedDeclaration.get().getTool().getSharedLocalInspectionTool().PARAMETER = true;
    unusedDeclaration.get().getTool().getSharedLocalInspectionTool().LOCAL_VARIABLE = false;
    unusedDeclaration.get().doTest("deadCode/" + unusedDeclaration.get().getTestName(true), unusedDeclaration.get().getToolWrapper());
  }

  @Test
  public void testOperatorInWhen() {
    unusedDeclaration.get().doTest("deadCode/" + unusedDeclaration.get().getTestName(true), unusedDeclaration.get().getToolWrapper());
  }

  @Test
  public void testCallableParameter() {
    unusedDeclaration.get().getTool().getSharedLocalInspectionTool().PARAMETER = true;
    unusedDeclaration.get().getTool().getSharedLocalInspectionTool().LOCAL_VARIABLE = false;
    unusedDeclaration.get().doTest("deadCode/" + unusedDeclaration.get().getTestName(true), unusedDeclaration.get().getToolWrapper());
  }

  @Test
  public void testCallableProperty() {
    unusedDeclaration.get().getTool().getSharedLocalInspectionTool().FIELD = true;
    unusedDeclaration.get().doTest("deadCode/" + unusedDeclaration.get().getTestName(true), unusedDeclaration.get().getToolWrapper());
  }

  @Test
  public void testUsagesInCallableReceiver() {
    unusedDeclaration.get().getTool().getSharedLocalInspectionTool().PARAMETER = true;
    unusedDeclaration.get().doTest("deadCode/" + unusedDeclaration.get().getTestName(true), unusedDeclaration.get().getToolWrapper());
  }

  @Test
  public void testUsagesInClassLiteral() {
    unusedDeclaration.get().getTool().getSharedLocalInspectionTool().PARAMETER = true;
    unusedDeclaration.get().doTest("deadCode/" + unusedDeclaration.get().getTestName(true), unusedDeclaration.get().getToolWrapper());
  }

  @Test
  public void testMainParameter() {
    unusedDeclaration.get().getTool().getSharedLocalInspectionTool().PARAMETER = true;
    unusedDeclaration.get().getTool().getSharedLocalInspectionTool().LOCAL_VARIABLE = false;
    unusedDeclaration.get().doTest("deadCode/" + unusedDeclaration.get().getTestName(true), unusedDeclaration.get().getToolWrapper());
  }

  @Test
  public void testStaticMethods() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testChainOfCalls() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testReachableFromFieldInitializer() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testReachableFromFieldArrayInitializer() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testJunitEntryPoint() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testConstructorCalls() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testPropertyReference() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testReferenceInLambda() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testNonJvmReferences() {
    unusedDeclaration.get().doTest();
  }

  //TODO uast kotlin enum support
  public void _testEnumInstantiation() {
    unusedDeclaration.get().doTest();
  }

  public void _testEnumValues() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testUsagesInAnonymous() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testClassLiteralRef() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testTopLevelFunction() {
    unusedDeclaration.get().doTest();
  }
}
