/**
 * created at Jan 25, 2002
 * @author Jeka
 */
package com.intellij.compiler;



public class FieldModifiersTest extends CompilerTestCase {
  public FieldModifiersTest() {
    super("fieldModifiers");
  }

  public void testSetFinal() throws Exception {doTest();}

  public void testSetStatic() throws Exception {doTest();}

  public void testUnsetStatic() throws Exception {doTest();}

  public void testUnsetStaticFinal() throws Exception {doTest();}

}
