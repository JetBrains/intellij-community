package org.jetbrains.ether;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 04.10.11
 * Time: 14:41
 * To change this template use File | Settings | File Templates.
 */
public class MarkDirtyTest extends IncrementalTestCase {
  public MarkDirtyTest() throws Exception {
    super("markDirty");
  }

  public void testRecompileDependent() throws Exception {
    doTest();
  }
}
