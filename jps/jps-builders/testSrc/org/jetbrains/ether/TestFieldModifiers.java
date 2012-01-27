package org.jetbrains.ether;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 23.09.11
 * Time: 15:53
 * To change this template use File | Settings | File Templates.
 */
public class TestFieldModifiers extends IncrementalTestCase {
  public TestFieldModifiers() throws Exception {
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
