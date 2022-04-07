// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull

@CompileStatic
enum JetBrainsRuntimeDistribution {
  /**
   * JBR with JCEF and DCEVM
   */
  JCEF('jcef'),

  /**
   * JBR without JCEF and DCEVM
   */
  LIGHTWEIGHT(''),

  static final List<JetBrainsRuntimeDistribution> ALL = List.of(values())

  /**
   * Distinguishes artifacts of different JBR distributions
   */
  @NotNull
  final String classifier

  JetBrainsRuntimeDistribution(@NotNull String classifier) {
    this.classifier = classifier
  }

  @NotNull
  String getArtifactPrefix() {
    if (classifier.isEmpty()) {
      return "jbr-"
    }
    else {
      return "jbr_" + classifier + "-"
    }
  }
}
