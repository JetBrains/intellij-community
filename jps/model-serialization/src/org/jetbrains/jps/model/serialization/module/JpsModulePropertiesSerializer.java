// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization.module;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.module.JpsModuleType;
import org.jetbrains.jps.model.serialization.JpsElementPropertiesSerializer;

public abstract class JpsModulePropertiesSerializer<P extends JpsElement> extends JpsElementPropertiesSerializer<P, JpsModuleType<P>> {
  private final String myComponentName;

  protected JpsModulePropertiesSerializer(JpsModuleType<P> type, String typeId, @Nullable String componentName) {
    super(type, typeId);
    myComponentName = componentName;
  }

  public @Nullable String getComponentName() {
    return myComponentName;
  }

  public abstract P loadProperties(@Nullable Element componentElement);

  /**
   * @deprecated the build process doesn't save project configuration so there is no need to implement this method, it isn't called by the platform
   */
  @Deprecated(forRemoval = true)
  public void saveProperties(@NotNull P properties, @NotNull Element componentElement) {
  }
}
