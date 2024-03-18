// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization.java;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.runConfiguration.JpsApplicationRunConfigurationProperties;
import org.jetbrains.jps.model.java.runConfiguration.JpsApplicationRunConfigurationState;
import org.jetbrains.jps.model.java.runConfiguration.JpsApplicationRunConfigurationType;
import org.jetbrains.jps.model.serialization.runConfigurations.JpsRunConfigurationPropertiesSerializer;

/**
 * Currently java run configurations aren't used in external compiler so this serializer is registered for tests only for performance reasons
 */
public final class JpsApplicationRunConfigurationSerializer extends JpsRunConfigurationPropertiesSerializer<JpsApplicationRunConfigurationProperties> {
  public JpsApplicationRunConfigurationSerializer() {
    super(JpsApplicationRunConfigurationType.INSTANCE, "Application");
  }

  @Override
  public JpsApplicationRunConfigurationProperties loadProperties(@Nullable Element runConfigurationTag) {
    JpsApplicationRunConfigurationState properties = runConfigurationTag != null ?
                                                     XmlSerializer.deserialize(runConfigurationTag, JpsApplicationRunConfigurationState.class) : new JpsApplicationRunConfigurationState();
    return JpsJavaExtensionService.getInstance().createRunConfigurationProperties(properties);
  }
}
