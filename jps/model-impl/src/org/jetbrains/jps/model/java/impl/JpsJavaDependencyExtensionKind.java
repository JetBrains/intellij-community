package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementCreator;
import org.jetbrains.jps.model.impl.JpsElementKindBase;
import org.jetbrains.jps.model.java.JpsJavaDependencyExtension;
import org.jetbrains.jps.model.java.JpsJavaDependencyScope;

/**
 * @author nik
 */
public class JpsJavaDependencyExtensionKind extends JpsElementKindBase<JpsJavaDependencyExtension> implements JpsElementCreator<JpsJavaDependencyExtension> {
  public static final JpsJavaDependencyExtensionKind INSTANCE = new JpsJavaDependencyExtensionKind();

  private JpsJavaDependencyExtensionKind() {
    super("java dependency extension");
  }

  @NotNull
  @Override
  public JpsJavaDependencyExtensionImpl create() {
    return new JpsJavaDependencyExtensionImpl(false, JpsJavaDependencyScope.COMPILE);
  }
}
