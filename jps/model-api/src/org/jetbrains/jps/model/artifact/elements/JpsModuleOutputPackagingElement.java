package org.jetbrains.jps.model.artifact.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleReference;

/**
 * @author nik
 */
public interface JpsModuleOutputPackagingElement extends JpsPackagingElement {
  @NotNull
  JpsModuleReference getModuleReference();

  @Nullable
  String getOutputUrl();
}
