/**
 * created at Jan 28, 2002
 * @author Jeka
 */
package com.intellij.compiler;

public class ClassPropertiesTest extends CompilerTestCase {
  public ClassPropertiesTest() {
    super("classProperties");
  }

  public void testAddExtends() throws Exception {doTest();}
  public void testRemoveExtends() throws Exception {doTest();}
  public void testChangeExtends() throws Exception {doTest();}

  public void testAddImplements() throws Exception {doTest();}
  public void testRemoveImplements() throws Exception {doTest();}
  public void testRemoveImplements2() throws Exception {doTest();}
  public void testRemoveImplements3() throws Exception {doTest();}
}
