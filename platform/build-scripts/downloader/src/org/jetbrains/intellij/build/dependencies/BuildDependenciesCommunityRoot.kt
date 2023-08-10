// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path

/**
 * Special wrapper not to mix community root with other parameters
 */
@ApiStatus.Internal
class BuildDependenciesCommunityRoot(communityRoot: Path) {
  @JvmField
  val communityRoot: Path

  init {
    val probeFile = communityRoot.resolve("intellij.idea.community.main.iml")
    check(!Files.notExists(probeFile)) { "community root was not found at $communityRoot" }
    this.communityRoot = communityRoot
  }

  override fun toString(): String {
    return "BuildDependenciesCommunityRoot{" +
           "communityRoot=" + communityRoot +
           '}'
  }
}
