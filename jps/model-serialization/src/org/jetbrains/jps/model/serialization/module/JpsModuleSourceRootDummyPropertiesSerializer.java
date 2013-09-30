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
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

/**
 * @author nik
 */
public class JpsModuleSourceRootDummyPropertiesSerializer extends JpsModuleSourceRootPropertiesSerializer<JpsDummyElement> {
  public JpsModuleSourceRootDummyPropertiesSerializer(JpsModuleSourceRootType<JpsDummyElement> type, String typeId) {
    super(type, typeId);
  }

  @Override
  public JpsDummyElement loadProperties(@NotNull Element sourceRootTag) {
    return JpsElementFactory.getInstance().createDummyElement();
  }

  @Override
  public void saveProperties(@NotNull JpsDummyElement properties, @NotNull Element sourceRootTag) {
  }
}
