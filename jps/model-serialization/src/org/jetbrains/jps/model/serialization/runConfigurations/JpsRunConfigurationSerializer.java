// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.serialization.runConfigurations;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JpsRunConfigurationSerializer {
  private static final Logger LOG = Logger.getInstance(JpsRunConfigurationSerializer.class);

  public static void loadRunConfigurations(@NotNull JpsProject project, @Nullable Element runManagerTag) {
    List<Element> elements = JDOMUtil.getChildren(runManagerTag, "configuration");
    if (elements.isEmpty()) {
      return;
    }

    Map<String, JpsRunConfigurationPropertiesSerializer<?>> serializers = new HashMap<>();
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsRunConfigurationPropertiesSerializer<?> serializer : extension.getRunConfigurationPropertiesSerializers()) {
        serializers.put(serializer.getTypeId(), serializer);
      }
    }

    for (Element configurationTag : elements) {
      if (Boolean.parseBoolean(configurationTag.getAttributeValue("default"))) {
        continue;
      }

      String typeId = configurationTag.getAttributeValue("type");
      JpsRunConfigurationPropertiesSerializer<?> serializer = serializers.get(typeId);
      String name = configurationTag.getAttributeValue("name");
      if (serializer != null) {
        loadRunConfiguration(name, configurationTag, serializer, project);
      }
      else if (name == null) {
        LOG.info("Run configuration '" + JDOMUtil.write(configurationTag) + "' wasn't loaded because 'name' attribute is missing");
      }
      else if (typeId != null) {
        project.addRunConfiguration(name, new JpsUnknownRunConfigurationType(typeId), JpsElementFactory.getInstance().createDummyElement());
      }
      else {
        LOG.info("Run configuration '" + name + "' wasn't loaded because 'type' attribute is missing");
      }
    }
  }

  private static <P extends JpsElement> void loadRunConfiguration(final String name, Element configurationTag,
                                                                  JpsRunConfigurationPropertiesSerializer<P> serializer,
                                                                  JpsProject project) {
    P properties = serializer.loadProperties(configurationTag);
    project.addRunConfiguration(name, serializer.getType(), properties);

  }
}
