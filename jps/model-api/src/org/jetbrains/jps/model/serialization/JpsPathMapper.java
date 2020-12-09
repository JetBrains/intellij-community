// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.serialization;

import org.jetbrains.annotations.Nullable;

public interface JpsPathMapper {
  @Nullable String mapUrl(@Nullable String url);

  JpsPathMapper IDENTITY = new JpsPathMapper() {
    @Override
    public @Nullable String mapUrl(@Nullable String url) {
      return url;
    }
  };
}
