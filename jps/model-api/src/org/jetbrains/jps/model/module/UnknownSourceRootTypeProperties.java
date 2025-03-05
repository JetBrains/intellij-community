// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.ex.JpsElementBase;

public class UnknownSourceRootTypeProperties<Data> extends JpsElementBase<UnknownSourceRootTypeProperties<Data>> {
  private final @Nullable Data myPropertiesData;

  public UnknownSourceRootTypeProperties(@Nullable Data propertiesData) {
    myPropertiesData = propertiesData;
  }

  public @Nullable Data getPropertiesData() {
    return myPropertiesData;
  }

  @Override
  public @NotNull UnknownSourceRootTypeProperties<Data> createCopy() {
    return new UnknownSourceRootTypeProperties<>(myPropertiesData);
  }
}
