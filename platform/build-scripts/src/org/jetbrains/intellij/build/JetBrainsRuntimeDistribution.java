// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull
  public final String classifier;

  JetBrainsRuntimeDistribution(@NotNull String classifier) {
    this.classifier = classifier;
  }

  @NotNull
  public String getArtifactPrefix() {
    if (classifier.isEmpty()) {
      return "jbr-";
    }
    else {
      return "jbr_" + classifier + "-";
    }
  }
}
