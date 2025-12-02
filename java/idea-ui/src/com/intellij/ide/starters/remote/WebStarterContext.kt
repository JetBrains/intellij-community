// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starters.remote

import com.intellij.ide.starters.shared.*

class WebStarterContext : CommonStarterContext() {
  lateinit var serverUrl: String
  lateinit var serverOptions: WebStarterServerOptions

  var frameworkVersion: WebStarterFrameworkVersion? = null
  var packageName: String = DEFAULT_PACKAGE_NAME
  var languageLevel: StarterLanguageLevel? = null
  var packaging: StarterAppPackaging? = null
  var configFileFormat: StarterConfigFileFormat? = null

  val dependencies: MutableSet<WebStarterDependency> = HashSet()

  var result: DownloadResult? = null
}