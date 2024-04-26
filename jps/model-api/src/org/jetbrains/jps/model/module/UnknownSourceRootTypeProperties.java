// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.ex.JpsElementBase;

public class UnknownSourceRootTypeProperties<Data> extends JpsElementBase<UnknownSourceRootTypeProperties<Data>> {
  @Nullable
  private final Data myPropertiesData;

  public UnknownSourceRootTypeProperties(@Nullable Data propertiesData) {
    myPropertiesData = propertiesData;
  }

  @Nullable
  public Data getPropertiesData() {
    return myPropertiesData;
  }

  @NotNull
  @Override
  public UnknownSourceRootTypeProperties<Data> createCopy() {
    return new UnknownSourceRootTypeProperties<>(myPropertiesData);
  }
}
