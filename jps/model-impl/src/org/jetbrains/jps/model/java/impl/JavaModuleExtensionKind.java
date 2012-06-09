package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.impl.JpsElementKindBase;
import org.jetbrains.jps.model.java.JavaModuleExtension;
import org.jetbrains.jps.model.module.JpsModule;

/**
 * @author nik
 */
public class JavaModuleExtensionKind extends JpsElementKindBase<JavaModuleExtensionImpl> implements JpsElementCreator<JavaModuleExtensionImpl> {
  private static final JavaModuleExtensionKind INSTANCE = new JavaModuleExtensionKind();

  public JavaModuleExtensionKind() {
    super("java module extension");
  }

  @NotNull
  @Override
  public JavaModuleExtensionImpl create() {
    return new JavaModuleExtensionImpl();
  }

  @NotNull
  public static JavaModuleExtension getExtension(@NotNull JpsModule module) {
    JavaModuleExtension child = module.getContainer().getChild(INSTANCE);
    if (child == null) {
      child = module.getContainer().setChild(INSTANCE);
    }
    return child;
  }
}
