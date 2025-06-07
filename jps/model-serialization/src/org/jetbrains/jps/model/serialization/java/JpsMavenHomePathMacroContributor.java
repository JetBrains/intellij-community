// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.JpsPathMacroContributor;

import java.util.Map;

public final class JpsMavenHomePathMacroContributor implements JpsPathMacroContributor {
  @Override
  public @NotNull Map<@NotNull String, @NotNull String> getPathMacros() {
    String path = System.getProperty("ide.compiler.maven.path.to.home");
    if (path == null) {
      return Map.of();
    }
    return Map.of("MAVEN_REPOSITORY", path);
  }
}
