// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization.module;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

public final class JpsModuleSourceRootDummyPropertiesSerializer extends JpsModuleSourceRootPropertiesSerializer<JpsDummyElement> {
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
