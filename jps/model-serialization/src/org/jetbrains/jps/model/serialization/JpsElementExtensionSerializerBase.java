// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization;

import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;

public abstract class JpsElementExtensionSerializerBase<E extends JpsElement> {
  private final String myConfigFileName;
  private final String myComponentName;

  protected JpsElementExtensionSerializerBase(@Nullable String configFileName, @NotNull String componentName) {
    myComponentName = componentName;
    myConfigFileName = configFileName;
  }

  @ApiStatus.Internal
  public @Nullable String getConfigFileName() {
    return myConfigFileName;
  }

  @ApiStatus.Internal
  public @NotNull String getComponentName() {
    return myComponentName;
  }

  public abstract void loadExtension(@NotNull E e, @NotNull Element componentTag);

  // called when no corresponding component tag was found in xml configs
  public void loadExtensionWithDefaultSettings(@NotNull E e) {
  }

  /**
   * @deprecated the build process doesn't save project configuration so there is no need to implement this method, it isn't called by the platform
   */
  @Deprecated(forRemoval = true)
  public void saveExtension(@NotNull E e, @NotNull Element componentTag) {
  }
}
