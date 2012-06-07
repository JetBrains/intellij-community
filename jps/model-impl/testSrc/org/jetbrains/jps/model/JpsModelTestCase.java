package org.jetbrains.jps.model;

import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.jps.model.impl.JpsModelImpl;

/**
 * @author nik
 */
public abstract class JpsModelTestCase extends UsefulTestCase {
  protected JpsModelImpl myModel;
  protected TestJpsEventDispatcher myDispatcher;

  public void setUp() throws Exception {
    super.setUp();
    myDispatcher = new TestJpsEventDispatcher();
    myModel = new JpsModelImpl(myDispatcher);
  }
}
