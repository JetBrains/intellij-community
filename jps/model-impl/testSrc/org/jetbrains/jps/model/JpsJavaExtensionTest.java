package org.jetbrains.jps.model;

import org.jetbrains.jps.model.java.JpsJavaModuleType;
import org.jetbrains.jps.model.java.impl.JavaModuleExtensionKind;
import org.jetbrains.jps.model.module.JpsModule;

/**
 * @author nik
 */
public class JpsJavaExtensionTest extends JpsModelTestCase {
  public void test() {
    final JpsModule module = myModel.getProject().addModule(JpsJavaModuleType.INSTANCE, "m");
    JavaModuleExtensionKind.getExtension(module).setOutputUrl("file://path");
    assertEquals("file://path", JavaModuleExtensionKind.getExtension(module).getOutputUrl());
  }
}
