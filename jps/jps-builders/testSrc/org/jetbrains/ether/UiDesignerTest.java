package org.jetbrains.ether;

import org.jetbrains.jps.builders.BuildResult;
import org.jetbrains.jps.incremental.java.JavaBuilder;

/**
 * @author nik
 */
public class UiDesignerTest extends IncrementalTestCase {
  public UiDesignerTest() throws Exception {
    super("uiDesigner");
  }

  @Override
  public BuildResult doTest() {
    final boolean wasEnabled = JavaBuilder.isFormsInstrumentationEnabled();
    try {
      JavaBuilder.setFormsInstrumentationEnabled(true);
      return super.doTest();
    }
    finally {
      JavaBuilder.setFormsInstrumentationEnabled(wasEnabled);
    }
  }

  public void testSimple() throws Exception {
    doTest().assertSuccessful();
  }
}
