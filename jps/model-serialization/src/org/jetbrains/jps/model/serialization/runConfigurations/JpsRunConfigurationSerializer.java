/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.model.serialization.runConfigurations;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.containers.hash.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;

import java.util.Map;

/**
 * @author nik
 */
public class JpsRunConfigurationSerializer {
  private static final Logger LOG = Logger.getInstance(JpsRunConfigurationSerializer.class);

  public static void loadRunConfigurations(@NotNull JpsProject project, @Nullable Element runManagerTag) {
    Map<String, JpsRunConfigurationPropertiesSerializer<?>> serializers = new HashMap<String, JpsRunConfigurationPropertiesSerializer<?>>();
    for (JpsModelSerializerExtension extension : JpsModelSerializerExtension.getExtensions()) {
      for (JpsRunConfigurationPropertiesSerializer<?> serializer : extension.getRunConfigurationPropertiesSerializers()) {
        serializers.put(serializer.getTypeId(), serializer);
      }
    }

    for (Element configurationTag : JDOMUtil.getChildren(runManagerTag, "configuration")) {
      if (Boolean.parseBoolean(configurationTag.getAttributeValue("default"))) {
        continue;
      }

      String typeId = configurationTag.getAttributeValue("type");
      JpsRunConfigurationPropertiesSerializer<?> serializer = serializers.get(typeId);
      String name = configurationTag.getAttributeValue("name");
      if (serializer != null) {
        loadRunConfiguration(name, configurationTag, serializer, project);
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
