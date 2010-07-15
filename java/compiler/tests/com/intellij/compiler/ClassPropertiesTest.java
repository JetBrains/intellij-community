/**
 * created at Jan 28, 2002
 * @author Jeka
 */
package com.intellij.compiler;

public class ClassPropertiesTest extends CompilerTestCase {
  public ClassPropertiesTest() {
    super("classProperties");
  }

  protected void setUp() throws Exception {
    //if ("removeExtends".equals(getTestName(true))) {
    //  System.out.println("================BEGIN removeExtends====================");
    //  CompileDriver.ourDebugMode = true;
    //  TranslatingCompilerFilesMonitor.ourDebugMode = true;
    //}
    super.setUp();
  }

  protected void tearDown() throws Exception {
    //if ("removeExtends".equals(getTestName(true))) {
    //  System.out.println("================END removeExtends====================");
    //}
    //CompileDriver.ourDebugMode = false;
    //TranslatingCompilerFilesMonitor.ourDebugMode = false;
    super.tearDown();
  }

  public void testAddExtends() throws Exception {doTest();}
  public void testRemoveExtends() throws Exception {doTest();}
  public void testChangeExtends() throws Exception {doTest();}

  public void testAddImplements() throws Exception {doTest();}
  public void testRemoveImplements() throws Exception {doTest();}
  public void testRemoveImplements2() throws Exception {doTest();}
  public void testRemoveImplements3() throws Exception {doTest();}
}
