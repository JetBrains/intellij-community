// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.pluginModel

/**
 * Options for [PluginDependenciesValidator].
 */
class PluginDependenciesValidationOptions(
  /**
   * List of a module name and a name of a missing dependency in this module which shouldn't be reported by the validator.
   * This can be used to temporarily suppress some errors while they are being fixed in plugins or while a false-positive is being fixed in the validator. 
   */
  val missingDependenciesToIgnore: List<Pair<String, String>>,

  /**
   * Prefixes of plugin loading error messages which shouldn't be reported by the validator.
   */
  val pluginErrorPrefixesToIgnore: List<String>,
  
  /**
   * Relative paths to XML files which are located in libraries and included in plugin descriptors via `<xi:include>`.
   */
  val pathsIncludedFromLibrariesViaXiInclude: Set<String>,
)
