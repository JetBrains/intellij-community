package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementCreator;
import org.jetbrains.jps.model.impl.JpsElementKindBase;
import org.jetbrains.jps.model.java.JpsJavaProjectExtension;

/**
 * @author nik
 */
public class JavaProjectExtensionKind extends JpsElementKindBase<JpsJavaProjectExtension> implements JpsElementCreator<JpsJavaProjectExtension> {
  public static final JavaProjectExtensionKind INSTANCE = new JavaProjectExtensionKind();

  public JavaProjectExtensionKind() {
    super("java project extension");
  }

  @NotNull
  @Override
  public JpsJavaProjectExtension create() {
    return new JpsJavaProjectExtensionImpl();
  }
}
