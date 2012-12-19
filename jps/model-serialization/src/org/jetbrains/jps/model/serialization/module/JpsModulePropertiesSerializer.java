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
