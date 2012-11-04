package org.jetbrains.ether;

/**
 * @author: db
 * Date: 23.09.11
 */
public class FieldModifierTest extends IncrementalTestCase {
  public FieldModifierTest() throws Exception {
    super("fieldModifiers");
  }

  public void testSetFinal() throws Exception {
    doTest();
  }

  public void testSetPrivate() throws Exception {
    doTest();
  }

  public void testSetProtected() throws Exception {
    doTest();
  }

  public void testSetStatic() throws Exception {
    doTest();
  }

  public void testUnsetStatic() throws Exception {
    doTest();
  }

  public void testUnsetStaticFinal() throws Exception {
    doTest();
  }
}
