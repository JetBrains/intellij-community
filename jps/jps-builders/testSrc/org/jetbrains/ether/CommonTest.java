package org.jetbrains.ether;

/**
 * @author: db
 * Date: 22.09.11
 */
public class CommonTest extends IncrementalTestCase {
  public CommonTest() throws Exception {
    super("common");
  }

  public void testAnonymous() throws Exception {
    doTest();
  }

  public void testChangeDefinitionToClass() throws Exception {
    doTest();
  }

  public void testChangeDefinitionToClass2() throws Exception {
    doTest();
  }

  public void testDeleteClass() throws Exception {
    doTest();
  }

  public void testDeleteClass1() throws Exception {
    doTest();
  }

  public void testDeleteClass2() throws Exception {
    doTest();
  }

  public void testDeleteClassAfterCompileErrors() throws Exception {
    setupInitialProject();
  
    doTestBuild(2);
  }

  public void testDeleteClassPackageDoesntMatchRoot() throws Exception {
    doTest();
  }

  public void testInner() throws Exception {
    doTest();
  }

  public void testNoResourceDelete() throws Exception {
    doTest();
  }

  public void testNoSecondFileCompile() throws Exception {
    doTest();
  }

  public void testNoSecondFileCompile1() throws Exception {
    doTest();
  }

  public void testDependencyUpdate() throws Exception {
    doTest();
  }

  public void testClass2Interface1() throws Exception {
    doTest();
  }

  public void testClass2Interface2() throws Exception {
    doTest();
  }

  public void testClass2Interface3() throws Exception {
    doTest();
  }

  public void testDeleteClass3() throws Exception {
      doTest();
  }

  public void testDeleteClass4() throws Exception {
      doTest();
  }

  public void testAddClass() throws Exception {
      doTest();
  }
}
