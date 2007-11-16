/**
 * created at Jan 25, 2002
 * @author Jeka
 */
package com.intellij.compiler;

public class CommonTest extends CompilerTestCase {
  public CommonTest() {
    super("common");
  }

  public void testAnonymous()  throws Exception {doTest();}

  public void testDeleteClass()  throws Exception {doTest();}

  // TODO: SCR 10823
  //public void testDeleteClass1()  throws Exception {doTest();}

  public void testNoSecondFileCompile()  throws Exception {doTest();}

  public void testNoSecondFileCompile1()  throws Exception {doTest();}

  public void testInner() throws Exception  {doTest();}

  public void testChangeDefinitionToClass() throws Exception  {doTest();}

  public void testChangeDefinitionToClass2() throws Exception  {doTest();}
  
  public void testChangeDefinitionToInterface() throws Exception  {doTest();}
}
