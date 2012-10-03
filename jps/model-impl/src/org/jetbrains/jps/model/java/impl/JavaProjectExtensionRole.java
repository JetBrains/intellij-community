package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementCreator;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.java.JpsJavaProjectExtension;

/**
 * @author nik
 */
public class JavaProjectExtensionRole extends JpsElementChildRoleBase<JpsJavaProjectExtension> implements JpsElementCreator<JpsJavaProjectExtension> {
  public static final JavaProjectExtensionRole INSTANCE = new JavaProjectExtensionRole();

  public JavaProjectExtensionRole() {
    super("java project extension");
  }

  @NotNull
  @Override
  public JpsJavaProjectExtension create() {
    return new JpsJavaProjectExtensionImpl();
  }
}
