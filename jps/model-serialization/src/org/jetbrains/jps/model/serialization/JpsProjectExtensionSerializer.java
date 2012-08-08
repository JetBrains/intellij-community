package org.jetbrains.jps.model.serialization;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsProject;

/**
 * @author nik
 */
public abstract class JpsProjectExtensionSerializer {
  private final String myConfigFileName;
  private String myComponentName;

  /**
   * @param configFileName name of file in .idea directory where the extension settings are stored
   * @param componentName name of the component
   */
  public JpsProjectExtensionSerializer(@Nullable String configFileName, @NotNull String componentName) {
    myConfigFileName = configFileName;
    myComponentName = componentName;
  }

  @Nullable
  public String getConfigFileName() {
    return myConfigFileName;
  }

  @NotNull
  public String getComponentName() {
    return myComponentName;
  }

  public abstract void loadExtension(@NotNull JpsProject project, @NotNull Element componentTag);

  public abstract void saveExtension(@NotNull JpsProject project, @NotNull Element componentTag);
}
