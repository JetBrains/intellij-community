// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.worker.core

import androidx.collection.ObjectList
import org.jetbrains.bazel.jvm.kotlin.JvmBuilderFlags
import org.jetbrains.bazel.jvm.util.ArgMap
import org.jetbrains.jps.model.ex.JpsElementBase
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import java.nio.file.Path

class BazelConfigurationHolder(
  @JvmField val classPath: Array<Path>,
  @JvmField val args: ArgMap<JvmBuilderFlags>,
  @JvmField val kotlinArgs: K2JVMCompilerArguments,
  @JvmField val classPathRootDir: Path,
  @JvmField val sources: List<Path>,
  @JvmField val trackableDependencyFiles: ObjectList<Path>,
  @JvmField val javaExports: List<String>,
) : JpsElementBase<BazelConfigurationHolder>() {
  companion object {
    @JvmField val KIND: JpsElementChildRoleBase<BazelConfigurationHolder> = JpsElementChildRoleBase.create("kotlin facet extension")
  }
}
