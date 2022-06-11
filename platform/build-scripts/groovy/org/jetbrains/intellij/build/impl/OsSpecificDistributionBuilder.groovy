// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily

import java.nio.file.Path

@CompileStatic
abstract class OsSpecificDistributionBuilder {
  protected final BuildContext buildContext

  OsSpecificDistributionBuilder(BuildContext buildContext) {
    this.buildContext = buildContext
  }

  @NotNull
  abstract OsFamily getTargetOs();

  abstract void copyFilesForOsDistribution(@NotNull Path targetPath, JvmArchitecture arch = null)

  abstract void buildArtifacts(@NotNull Path osAndArchSpecificDistPath, @NotNull JvmArchitecture arch)

  List<String> generateExecutableFilesPatterns(boolean includeJre) {
    return Collections.emptyList()
  }
}