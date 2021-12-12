// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import java.nio.file.Path

/**
 * Special wrapper not to mix community root with other parameters
 */
class BuildDependenciesCommunityRoot {
  final Path communityRoot

  BuildDependenciesCommunityRoot(Path communityRoot) {
    this.communityRoot = communityRoot
  }

  @Override
  String toString() {
    return "BuildDependenciesCommunityRoot{" +
           "communityRoot=" + communityRoot +
           '}'
  }
}
