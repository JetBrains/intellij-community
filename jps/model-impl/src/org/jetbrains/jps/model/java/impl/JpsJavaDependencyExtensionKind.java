package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.impl.JpsElementKindBase;
import org.jetbrains.jps.model.java.JpsJavaDependencyExtension;
import org.jetbrains.jps.model.java.JpsJavaDependencyScope;
import org.jetbrains.jps.model.module.JpsDependencyElement;

/**
 * @author nik
 */
public class JpsJavaDependencyExtensionKind extends JpsElementKindBase<JpsJavaDependencyExtensionImpl> implements JpsElementCreator<JpsJavaDependencyExtensionImpl> {
  public static final JpsJavaDependencyExtensionKind INSTANCE = new JpsJavaDependencyExtensionKind();

  public JpsJavaDependencyExtensionKind() {
    super("java dependency extension");
  }

  @NotNull
  @Override
  public JpsJavaDependencyExtensionImpl create() {
    return new JpsJavaDependencyExtensionImpl(false, JpsJavaDependencyScope.COMPILE);
  }

  public static JpsJavaDependencyExtension getExtension(@NotNull JpsDependencyElement element) {
    JpsJavaDependencyExtensionImpl extension = element.getContainer().getChild(INSTANCE);
    if (extension == null) {
      extension = element.getContainer().setChild(INSTANCE);
    }
    return extension;
  }
}
