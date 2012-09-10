package org.jetbrains.ether;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 09.08.11
 * Time: 12:57
 * To change this template use File | Settings | File Templates.
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
