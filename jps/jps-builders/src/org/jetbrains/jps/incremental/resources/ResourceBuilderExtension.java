package org.jetbrains.jps.incremental.resources;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModule;

public abstract class ResourceBuilderExtension {
  public boolean skipStandardResourceCompiler(final @NotNull JpsModule module) {
    return false;
  }
}
