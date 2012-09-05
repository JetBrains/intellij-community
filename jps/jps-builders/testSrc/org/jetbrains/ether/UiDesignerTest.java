package org.jetbrains.ether;

/**
 * @author nik
 */
public class UiDesignerTest extends IncrementalTestCase {
  public UiDesignerTest() throws Exception {
    super("uiDesigner");
  }

  public void testSimple() throws Exception {
    doTest().assertSuccessful();
  }
}
