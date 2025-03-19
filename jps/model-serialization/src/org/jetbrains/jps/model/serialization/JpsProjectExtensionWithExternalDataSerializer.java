// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization;

import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public abstract class JpsProjectExtensionWithExternalDataSerializer extends JpsProjectExtensionSerializer {
  private final String myExternalComponentName;
  private final String myExternalConfigFilePath;

  protected JpsProjectExtensionWithExternalDataSerializer(@NotNull String configFileName,
                                                          @NotNull String componentName,
                                                          @NotNull String externalConfigFilePath, 
                                                          @NotNull String externalComponentName) {
    super(configFileName, componentName);
    myExternalConfigFilePath = externalConfigFilePath;
    myExternalComponentName = externalComponentName;
  }

  public abstract void mergeExternalData(@NotNull Element internalComponent, @NotNull Element externalComponent);

  public String getExternalConfigFilePath() {
    return myExternalConfigFilePath;
  }

  public final @NotNull String getExternalComponentName() {
    return myExternalComponentName;
  }
}
