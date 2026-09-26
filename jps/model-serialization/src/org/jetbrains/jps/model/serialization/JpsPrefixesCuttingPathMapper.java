// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@ApiStatus.Internal
public class JpsPrefixesCuttingPathMapper implements JpsPathMapper {
  private final Set<String> myPrefixes;

  public JpsPrefixesCuttingPathMapper(Set<String> prefixes) { myPrefixes = prefixes; }

  @Override
  public @Nullable String mapUrl(@Nullable String url) {
    if (url == null) return null;
    for (String prefix : myPrefixes) {
      url = url.replace(prefix, "");
    }
    return url;
  }
}
