package org.jetbrains.jps.model.serialization;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsGlobal;

/**
 * @author nik
 */
public abstract class JpsGlobalExtensionSerializer extends JpsElementExtensionSerializerBase<JpsGlobal> {
  protected JpsGlobalExtensionSerializer(@Nullable String configFileName, @NotNull String componentName) {
    super(configFileName, componentName);
  }
}
