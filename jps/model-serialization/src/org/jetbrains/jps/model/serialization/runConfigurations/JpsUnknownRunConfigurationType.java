// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization.runConfigurations;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.ex.JpsElementTypeWithDummyProperties;
import org.jetbrains.jps.model.runConfiguration.JpsRunConfigurationType;

@ApiStatus.Internal
public final class JpsUnknownRunConfigurationType extends JpsElementTypeWithDummyProperties implements JpsRunConfigurationType<JpsDummyElement> {
  private final String myTypeId;

  public JpsUnknownRunConfigurationType(@NotNull String typeId) {
    myTypeId = typeId;
  }

  public @NotNull String getTypeId() {
    return myTypeId;
  }
}
