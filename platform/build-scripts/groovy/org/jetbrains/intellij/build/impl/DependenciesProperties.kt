// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.CompilationContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import kotlin.io.path.appendText
import kotlin.io.path.inputStream

class DependenciesProperties(context: CompilationContext) {
  private val propertiesFile = context.paths.communityHomeDir.communityRoot.resolve("build/dependencies/gradle.properties")

  private val props: Properties by lazy {
    propertiesFile.inputStream().use {
      val properties = Properties()
      properties.load(it)
      properties
    }
  }

  fun property(name: String): String =
    props.getProperty(name) ?: error("`$name` is not defined in `$propertiesFile`")

  fun copy(copy: Path) {
    Files.copy(propertiesFile, copy, StandardCopyOption.REPLACE_EXISTING)
    // legacy key is required for backward compatibility with gradle-intellij-plugin
    val jdkBuild = "jdkBuild"
    if (props.getProperty(jdkBuild) == null) {
      copy.appendText("\n$jdkBuild=${property("runtimeBuild")}\n")
    }
  }
}
