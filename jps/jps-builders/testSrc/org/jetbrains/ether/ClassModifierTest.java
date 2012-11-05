package org.jetbrains.ether;

/**
 * @author: db
 * Date: 09.08.11
 */
public class ClassModifierTest extends IncrementalTestCase {
  public ClassModifierTest() throws Exception {
    super("classModifiers");
  }

  public void testAddStatic() throws Exception {
    doTest();
  }

  public void testRemoveStatic() throws Exception {
    doTest();
  }

  public void testDecAccess() throws Exception {
    doTest();
  }

  public void testSetAbstract() throws Exception {
    doTest();
  }

  public void testDropAbstract() throws Exception {
    doTest();
  }

  public void testSetFinal() throws Exception {
    doTest();
  }

  public void testSetFinal1() throws Exception {
    doTest();
  }
}
