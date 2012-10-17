package org.jetbrains.jps.model.serialization.runConfigurations;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.containers.hash.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;

import java.util.Map;

/**
 * @author nik
 */
public class JpsRunConfigurationSerializer {
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
      if (serializer != null) {
        loadRunConfiguration(configurationTag, serializer, project);
      }
    }
  }

  private static <P extends JpsElement> void loadRunConfiguration(Element configurationTag,
                                                                  JpsRunConfigurationPropertiesSerializer<P> serializer,
                                                                  JpsProject project) {
    P properties = serializer.loadProperties(configurationTag);
    project.addRunConfiguration(configurationTag.getAttributeValue("name"), serializer.getType(), properties);

  }
}
