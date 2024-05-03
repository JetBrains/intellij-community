// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

public interface JpsPathMapper {
  @Contract("null->null; !null->!null")
  @Nullable String mapUrl(@Nullable String url);

  JpsPathMapper IDENTITY = new JpsPathMapper() {
    @Override
    public @Nullable String mapUrl(@Nullable String url) {
      return url;
    }
  };
}
