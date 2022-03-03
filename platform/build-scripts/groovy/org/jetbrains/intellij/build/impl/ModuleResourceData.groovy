// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import groovy.transform.Immutable

/**
 * Describes additional resources which should be included into a plugin or the platform distribution. This isn't related to files from
 * 'resources roots' of a module, such files are automatically included into the plugin JAR files.
 */
@CompileStatic
@Immutable
final class ModuleResourceData {
  /** Name of the module resources will be taken from */
  String moduleName

  /** Path to resource file or directory relative to the module content root */
  String resourcePath

  /** Target path relative to the plugin root directory */
  String relativeOutputPath

  /** If {@code true} resource will be packed into zip archive */
  boolean packToZip = false
}
