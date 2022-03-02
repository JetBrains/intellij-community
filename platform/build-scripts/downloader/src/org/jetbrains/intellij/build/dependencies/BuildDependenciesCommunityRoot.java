// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Special wrapper not to mix community root with other parameters
 */
@ApiStatus.Internal
public class BuildDependenciesCommunityRoot {
  @NotNull
  private final Path communityRoot;

  public @NotNull Path getCommunityRoot() {
    return communityRoot;
  }

  public BuildDependenciesCommunityRoot(@NotNull Path communityRoot) {
    Path probeFile = communityRoot.resolve("intellij.idea.community.main.iml");
    if (!Files.exists(probeFile)) {
      throw new IllegalStateException("community root was not found at " + communityRoot);
    }

    this.communityRoot = communityRoot;
  }

  @Override
  public String toString() {
    return "BuildDependenciesCommunityRoot{" +
           "communityRoot=" + communityRoot +
           '}';
  }
}
