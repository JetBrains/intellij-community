package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementCreator;
import org.jetbrains.jps.model.impl.JpsElementKindBase;
import org.jetbrains.jps.model.java.JpsJavaModuleExtension;

/**
 * @author nik
 */
public class JavaModuleExtensionKind extends JpsElementKindBase<JpsJavaModuleExtension> implements JpsElementCreator<JpsJavaModuleExtension> {
  public static final JavaModuleExtensionKind INSTANCE = new JavaModuleExtensionKind();

  private JavaModuleExtensionKind() {
    super("java module extension");
  }

  @NotNull
  @Override
  public JpsJavaModuleExtensionImpl create() {
    return new JpsJavaModuleExtensionImpl();
  }
}
