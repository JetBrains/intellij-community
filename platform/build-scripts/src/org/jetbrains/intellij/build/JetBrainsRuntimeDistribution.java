// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public enum JetBrainsRuntimeDistribution {
  /**
   * JBR with JCEF and DCEVM
   */
  JCEF("jcef"),

  /**
   * JBR without JCEF and DCEVM
   */
  LIGHTWEIGHT("");

  public static final List<JetBrainsRuntimeDistribution> ALL = List.of(values());

  /**
   * Distinguishes artifacts of different JBR distributions
   */
  public final @NotNull String classifier;

  JetBrainsRuntimeDistribution(@NotNull String classifier) {
    this.classifier = classifier;
  }

  public @NotNull String getArtifactPrefix() {
    if (classifier.isEmpty()) {
      return "jbr-";
    }
    else {
      return "jbr_" + classifier + "-";
    }
  }
}
