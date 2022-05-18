// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.CompilationContext
import java.nio.file.Path
import java.util.*
import kotlin.io.path.inputStream

class DependenciesProperties(private val context: CompilationContext) {
  private val directory = context.paths.communityHomeDir.resolve("build/dependencies")
  private val propertiesFile = directory.resolve("gradle.properties")

  val file: Path = propertiesFile

  private val props: Properties by lazy {
    propertiesFile.inputStream().use {
      val properties = Properties()
      properties.load(it)
      properties
    }
  }

  fun property(name: String): String =
    props.getProperty(name) ?: error("`$name` is not defined in `$propertiesFile`")

  fun propertyOrNull(name: String): String? {
    val value = props.getProperty(name)
    if (value == null) {
      context.messages.warning("`$name` is not defined in `$propertiesFile`")
    }
    return value
  }
}
