// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.pluginModel

/**
 * List of compile-only dependencies in intellij-community project that should be ignored by [PluginDependenciesValidator], see
 * [PluginDependenciesValidationOptions.compileOnlyDependencies].
 */
val compileOnlyDependenciesInCommunity: List<Pair<String, String>> = listOf(
  "*" to "intellij.platform.multiplatformSupport", // references are replaced by expects-compiler-plugin
  "*" to "fleet.util.multiplatform", // references are replaced by expects-compiler-plugin
  "*" to "intellij.platform.compose.compilerPlugin", // dependency is needed for compose compiler plugin
  "intellij.java.rt" to "intellij.libraries.junit4", // module is used in external processes where the library from user's project is added to the classpath
)