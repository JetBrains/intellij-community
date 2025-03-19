// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*

@ApiStatus.Internal
class DependenciesProperties(communityRoot: BuildDependenciesCommunityRoot, vararg customPropertyFiles: Path?) {
  private val dependencies: MutableMap<String, String?> = TreeMap()

  init {
    val communityPropertiesFile = communityRoot.communityRoot.resolve("build/dependencies/dependencies.properties")
    val runtimePropertiesFile = communityRoot.communityRoot.resolve("build/dependencies/runtime.properties")
    val ultimatePropertiesFile = communityRoot.communityRoot.parent.resolve("build/dependencies.properties")
    sequenceOf(*customPropertyFiles, communityPropertiesFile, ultimatePropertiesFile, runtimePropertiesFile)
      .filterNotNull()
      .distinct()
      .filter { file -> Files.exists(file) }
      .forEach { file ->
        BuildDependenciesUtil.loadPropertiesFile(file).forEach { (key, value) ->
          check(!dependencies.containsKey(key)) { "Key '${key}' from ${file} is already defined" }
          dependencies[key] = value
        }
      }
    check(!dependencies.isEmpty()) { "No dependencies are defined" }
  }

  override fun toString(): String {
    return dependencies.entries.joinToString("\n") {
      "${it.key}=${it.value}"
    }
  }

  fun property(name: String): String {
    return requireNotNull(dependencies[name]) {
      "'$name' is unknown key: $this"
    }
  }

  operator fun get(name: String): String {
    return property(name)
  }

  @Throws(IOException::class)
  fun copy(copy: Path?) {
    Files.newBufferedWriter(copy, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { file -> file.write(toString()) }
    check(dependencies.containsKey("jdkBuild")) { "'jdkBuild' key is required for backward compatibility with gradle-intellij-plugin" }
  }
}
