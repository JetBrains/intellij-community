/**
 * created at Jan 25, 2002
 * @author Jeka
 */
package com.intellij.compiler;



public class FieldPropertiesTest extends CompilerTestCase {
  public FieldPropertiesTest() {
    super("fieldProperties");
  }

  public void testInnerConstantChange() throws Exception {doTest();}

  public void testStringConstantChange() throws Exception {doTest();}

  public void testStringConstantLessAccessible() throws Exception {doTest();}

  public void testIntConstantChange() throws Exception {doTest();}

  public void testFloatConstantChange() throws Exception {doTest();}

  public void testDoubleConstantChange() throws Exception {doTest();}

  public void testLongConstantChange() throws Exception {doTest();}

  public void testNonCompileTimeConstant() throws Exception {doTest();}

  public void testConstantChain() throws Exception {doTest();}

  public void testConstantChain1() throws Exception {doTest();}

  public void testConstantChain2() throws Exception {doTest();}

  public void testConstantRemove() throws Exception {doTest();}
}
