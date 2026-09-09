// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ohos

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path

/**
 * The OHOS app sandbox lets a process traverse `/` but not list it.
 * So the file chooser tree starts from these paths, not from `/`.
 *
 * The launcher gives the paths in the [PROPERTY] system property, as absolute paths separated by semicolons.
 * If the property is not set or is empty, [isEnabled] is `false`.
 */
@ApiStatus.Internal
object OhosSandboxRoots {
  const val PROPERTY: String = "idea.ohos.sandbox.roots"

  private val pathStrings: List<String> =
    System.getProperty(PROPERTY)?.split(';')?.filter { it.isNotBlank() }.orEmpty()

  val paths : List<Path>
    get() = pathStrings.mapNotNull { runCatching { Path.of(it) }.getOrNull() }.filter { Files.isDirectory(it) }

  val isEnabled: Boolean
    get() = pathStrings.isNotEmpty()
}
