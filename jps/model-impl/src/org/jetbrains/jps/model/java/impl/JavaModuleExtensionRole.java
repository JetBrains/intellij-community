package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementCreator;
import org.jetbrains.jps.model.impl.JpsElementChildRoleBase;
import org.jetbrains.jps.model.java.JpsJavaModuleExtension;

/**
 * @author nik
 */
public class JavaModuleExtensionRole extends JpsElementChildRoleBase<JpsJavaModuleExtension> implements JpsElementCreator<JpsJavaModuleExtension> {
  public static final JavaModuleExtensionRole INSTANCE = new JavaModuleExtensionRole();

  private JavaModuleExtensionRole() {
    super("java module extension");
  }

  @NotNull
  @Override
  public JpsJavaModuleExtensionImpl create() {
    return new JpsJavaModuleExtensionImpl();
  }
}
