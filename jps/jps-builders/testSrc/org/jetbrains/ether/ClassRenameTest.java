package org.jetbrains.ether;

/**
 * @author: db
 * Date: 09.08.11
 */
public class ClassRenameTest extends IncrementalTestCase {
  public ClassRenameTest() throws Exception {
    super("changeName");
  }

  public void testChangeClassName() {
    doTest().assertSuccessful();
  }

  public void testChangeCaseOfName() {
    doTest().assertSuccessful();
  }
}
