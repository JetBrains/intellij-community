package org.jetbrains.jps.model.serialization;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.java.JpsApplicationRunConfigurationSerializer;
import org.jetbrains.jps.model.serialization.runConfigurations.JpsRunConfigurationPropertiesSerializer;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class JpsTestSerializerExtension extends JpsModelSerializerExtension {
  @NotNull
  @Override
  public List<? extends JpsRunConfigurationPropertiesSerializer<?>> getRunConfigurationPropertiesSerializers() {
    return Collections.singletonList(new JpsApplicationRunConfigurationSerializer());
  }
}
