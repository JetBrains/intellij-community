package org.jetbrains.jps.model;

import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.jps.model.impl.JpsModelImpl;

/**
 * @author nik
 */
public abstract class JpsModelTestCase extends UsefulTestCase {
  protected JpsModel myModel;
  protected TestJpsEventDispatcher myDispatcher;
  protected JpsProject myProject;

  public void setUp() throws Exception {
    super.setUp();
    myDispatcher = new TestJpsEventDispatcher();
    myModel = new JpsModelImpl(myDispatcher);
    myProject = myModel.getProject();
  }
}
