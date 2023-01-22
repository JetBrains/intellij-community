// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.stream.Collectors
import java.util.stream.Stream

@ApiStatus.Internal
class DependenciesProperties(communityRoot: BuildDependenciesCommunityRoot, vararg customPropertyFiles: Path?) {
  private val dependencies: MutableMap<String, String?> = TreeMap()

  init {
    val communityPropertiesFile = communityRoot.communityRoot
      .resolve("build")
      .resolve("dependencies")
      .resolve("dependencies.properties")
    val ultimatePropertiesFile = communityRoot.communityRoot
      .parent
      .resolve("build")
      .resolve("dependencies.properties")
    val propertyFiles = Stream.concat(
      Stream.of(*customPropertyFiles),
      Stream.of(communityPropertiesFile, ultimatePropertiesFile).filter { path: Path? -> Files.exists(path) }
    ).distinct().collect(Collectors.toList())
    for (propertyFile in propertyFiles) {
      Files.newInputStream(propertyFile).use { file ->
        val properties = Properties()
        properties.load(file)
        properties.forEach { key: Any, value: Any ->
          check(!dependencies.containsKey(key)) { "Key '$key' from $propertyFile is already defined" }
          dependencies[key.toString()] = value.toString()
        }
      }
    }
    check(!dependencies.isEmpty()) { "No dependencies are defined" }
  }

  override fun toString(): String =
    dependencies.entries.joinToString("\n") { "${it.key}=${it.value}"}

  fun property(name: String): String {
    return dependencies[name] ?: throw IllegalArgumentException("'$name' is unknown key: $this")
  }

  @Throws(IOException::class)
  fun copy(copy: Path?) {
    Files.newBufferedWriter(copy, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { file -> file.write(toString()) }
    check(dependencies.containsKey("jdkBuild")) { "'jdkBuild' key is required for backward compatibility with gradle-intellij-plugin" }
  }
}
