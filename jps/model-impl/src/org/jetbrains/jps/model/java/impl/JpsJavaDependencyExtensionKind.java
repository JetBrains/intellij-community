package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.java.JpsJavaDependencyExtension;
import org.jetbrains.jps.model.java.JpsJavaDependencyScope;
import org.jetbrains.jps.model.module.JpsDependencyElement;

/**
 * @author nik
 */
public class JpsJavaDependencyExtensionKind extends JpsElementKind<JpsJavaDependencyExtensionImpl>
                                            implements JpsElementFactory<JpsJavaDependencyExtensionImpl> {
  public static final JpsJavaDependencyExtensionKind INSTANCE = new JpsJavaDependencyExtensionKind();

  @NotNull
  @Override
  public JpsJavaDependencyExtensionImpl create(@NotNull JpsModel model,
                                               @NotNull JpsEventDispatcher eventDispatcher,
                                               JpsParentElement parent) {
    return new JpsJavaDependencyExtensionImpl(eventDispatcher, parent, false, JpsJavaDependencyScope.COMPILE);
  }

  public static JpsJavaDependencyExtension getExtension(@NotNull JpsDependencyElement element) {
    JpsJavaDependencyExtensionImpl extension = element.getContainer().getChild(INSTANCE);
    if (extension == null) {
      extension = element.getContainer().setChild(INSTANCE);
    }
    return extension;
  }
}
