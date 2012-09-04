package org.jetbrains.jps.model.serialization;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsProject;

/**
 * @author nik
 */
public abstract class JpsProjectExtensionSerializer extends JpsElementExtensionSerializerBase<JpsProject> {

  /**
   * @param configFileName name of file in .idea directory where the extension settings are stored
   * @param componentName name of the component
   */
  public JpsProjectExtensionSerializer(@Nullable String configFileName, @NotNull String componentName) {
    super(configFileName, componentName);
  }
}
