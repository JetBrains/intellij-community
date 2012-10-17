package org.jetbrains.jps.model.serialization.runConfigurations;

import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.runConfiguration.JpsRunConfigurationType;
import org.jetbrains.jps.model.serialization.JpsElementPropertiesSerializer;

/**
 * @author nik
 */
public abstract class JpsRunConfigurationPropertiesSerializer<P extends JpsElement> extends JpsElementPropertiesSerializer<P, JpsRunConfigurationType<P>> {
  protected JpsRunConfigurationPropertiesSerializer(JpsRunConfigurationType<P> type, String typeId) {
    super(type, typeId);
  }

  public abstract P loadProperties(@Nullable Element runConfigurationTag);

  public abstract void saveProperties(P properties, Element runConfigurationTag);
}
