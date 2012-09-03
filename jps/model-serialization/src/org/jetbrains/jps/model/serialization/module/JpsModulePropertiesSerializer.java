package org.jetbrains.jps.model.serialization.module;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.module.JpsModuleType;
import org.jetbrains.jps.model.serialization.JpsElementPropertiesSerializer;

/**
 * @author nik
 */
public abstract class JpsModulePropertiesSerializer<P extends JpsElement> extends JpsElementPropertiesSerializer<P, JpsModuleType<P>> {
  private final String myComponentName;

  protected JpsModulePropertiesSerializer(JpsModuleType<P> type, String typeId, @Nullable String componentName) {
    super(type, typeId);
    myComponentName = componentName;
  }

  @Nullable
  public String getComponentName() {
    return myComponentName;
  }

  public abstract P loadProperties(@Nullable Element componentElement);

  public abstract void saveProperties(@NotNull P properties, @NotNull Element componentElement);
}
