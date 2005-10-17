/**
 * created at Jan 28, 2002
 * @author Jeka
 */
package com.intellij.compiler;

public class ClassModifiersTest extends CompilerTestCase {
  public ClassModifiersTest() {
    super("classModifiers");
  }

  public void testDecAccess() throws Exception {doTest();}

  public void testSetAbstract() throws Exception {doTest();}

  public void testSetFinal() throws Exception {doTest();}

  public void testSetFinal1() throws Exception {doTest();}
}
