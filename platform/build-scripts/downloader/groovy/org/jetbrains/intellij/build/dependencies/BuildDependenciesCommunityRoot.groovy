// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import groovy.transform.CompileStatic
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NotNull

import java.nio.file.Files
import java.nio.file.Path

/**
 * Special wrapper not to mix community root with other parameters
 */
@ApiStatus.Internal
@CompileStatic
class BuildDependenciesCommunityRoot {
  @NotNull
  final Path communityRoot

  BuildDependenciesCommunityRoot(Path communityRoot) {
    if (communityRoot == null) {
      throw new IllegalStateException("passed community root is null")
    }

    def probeFile = communityRoot.resolve("intellij.idea.community.main.iml")
    if (!Files.exists(probeFile)) {
      throw new IllegalStateException("community root was not found at $communityRoot")
    }

    this.communityRoot = communityRoot
  }

  @Override
  String toString() {
    return "BuildDependenciesCommunityRoot{" +
           "communityRoot=" + communityRoot +
           '}'
  }
}
