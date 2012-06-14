package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementCreator;
import org.jetbrains.jps.model.impl.JpsElementKindBase;

/**
 * @author nik
 */
public class JavaModuleExtensionKind extends JpsElementKindBase<JpsJavaModuleExtensionImpl>
  implements JpsElementCreator<JpsJavaModuleExtensionImpl> {
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
