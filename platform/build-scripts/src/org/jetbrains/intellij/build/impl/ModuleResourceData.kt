// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

/**
 * Describes additional resources which should be included in a plugin or the platform distribution.
 * This isn't related to files from `resource roots` of a module, such files are automatically included in the plugin JAR files.
 */
internal data class ModuleResourceData(
  /** The Name of the module resources will be taken from */
  @JvmField
  val moduleName: String,

  /** Path to resource file or directory relative to the module content root */
  @JvmField
  val resourcePath: String,

  /** Target path relative to the plugin root directory */
  @JvmField
  val relativeOutputPath: String,

  /** If `true` resource is packed into the zip archive */
  @JvmField
  val packToZip: Boolean = false,
)
