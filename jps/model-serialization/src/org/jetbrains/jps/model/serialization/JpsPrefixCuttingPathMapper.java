// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class JpsPrefixCuttingPathMapper implements JpsPathMapper {
  private final String myPrefix;

  public JpsPrefixCuttingPathMapper(String prefix) { myPrefix = prefix; }

  @Override
  public @Nullable String mapUrl(@Nullable String url) {
    if (url == null) return null;
    return url.replace(myPrefix, "");
  }
}
