/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.jps.model.serialization.module;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;
import org.jetbrains.jps.model.serialization.JpsElementPropertiesSerializer;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;

/**
 * Override this class and return the implementation from {@link JpsModelSerializerExtension#getModuleSourceRootPropertiesSerializers()}
 * to support loading and saving custom source root types. The JAR file which contains the implementation must be added directly to 'lib'
 * directory of the plugin distribution to ensure that it's available inside the IDE process. Also if the plugin doesn't participate in the
 * build process (and therefore the JAR name isn't specified in 'compileServer.plugin' extension) a marker extension 'jps.plugin' must be added to plugin.xml:
 * <pre>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;
 * &nbsp;&nbsp;&lt;jps.plugin /&gt;
 * &lt;/extensions&gt;
 * </pre>
 */
public abstract class JpsModuleSourceRootPropertiesSerializer<P extends JpsElement> extends JpsElementPropertiesSerializer<P, JpsModuleSourceRootType<P>> {
  public JpsModuleSourceRootPropertiesSerializer(JpsModuleSourceRootType<P> type, String typeId) {
    super(type, typeId);
  }

  public abstract P loadProperties(@NotNull Element sourceRootTag);

  public abstract void saveProperties(@NotNull P properties, @NotNull Element sourceRootTag);
}
