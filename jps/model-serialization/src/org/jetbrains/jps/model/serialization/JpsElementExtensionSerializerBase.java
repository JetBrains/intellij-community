package org.jetbrains.jps.model.serialization;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;

/**
 * @author nik
 */
public abstract class JpsElementExtensionSerializerBase<E extends JpsElement> {
  protected final String myConfigFileName;
  protected String myComponentName;

  protected JpsElementExtensionSerializerBase(@NotNull String componentName, @Nullable String configFileName) {
    myComponentName = componentName;
    myConfigFileName = configFileName;
  }

  @Nullable
  public String getConfigFileName() {
    return myConfigFileName;
  }

  @NotNull
  public String getComponentName() {
    return myComponentName;
  }

  public abstract void loadExtension(@NotNull E element, @NotNull Element componentTag);

  public abstract void saveExtension(@NotNull E element, @NotNull Element componentTag);
}
