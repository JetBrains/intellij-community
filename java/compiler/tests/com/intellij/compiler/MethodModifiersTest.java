/**
 * created at Jan 28, 2002
 * @author Jeka
 */
package com.intellij.compiler;

public class MethodModifiersTest extends CompilerTestCase {
  public MethodModifiersTest() {
    super("methodModifiers");
  }

  public void testIncAccess() throws Exception {doTest();}

  public void testDecConstructorAccess() throws Exception {doTest();}

  public void testSetAbstract() throws Exception {doTest();}

  public void testSetFinal() throws Exception {doTest();}

  public void testUnsetFinal() throws Exception {doTest();}

  public void testUnsetStatic() throws Exception {doTest();}
}
