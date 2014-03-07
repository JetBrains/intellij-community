package com.intellij.execution.configurations;

import org.jetbrains.annotations.NotNull;

public interface CompatibilityAwareRunProfile {
  /**
   * Checks whether the run configuration is compatible with the configuration passed as a parameter
   * and may still run if the configuration passed as a parameter starts as well.
   *
   * @param configuration the run configuration to check a compatibility to run with the current configuration.
   * @return true if the configuration can still run along side with the configuration passed as parameter, false otherwise.
   */
  boolean isCompatibleWith(@NotNull RunConfiguration configuration);
}
