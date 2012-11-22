package org.jetbrains.ether;

import org.jetbrains.jps.builders.BuildResult;

/**
 * @author nik
 */
public class UiDesignerTest extends IncrementalTestCase {
  public UiDesignerTest() throws Exception {
    super("uiDesigner");
  }

  @Override
  public BuildResult doTest() {
    try {
      return super.doTest();
    }
    finally {
    }
  }

  public void testSimple() throws Exception {
    doTest().assertSuccessful();
  }
}
