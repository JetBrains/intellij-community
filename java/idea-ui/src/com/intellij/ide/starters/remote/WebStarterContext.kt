// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starters.remote

import com.intellij.ide.starters.shared.CommonStarterContext
import com.intellij.ide.starters.shared.DEFAULT_PACKAGE_NAME
import com.intellij.ide.starters.shared.StarterAppPackaging
import com.intellij.ide.starters.shared.StarterLanguageLevel

class WebStarterContext : CommonStarterContext() {
  lateinit var serverUrl: String
  lateinit var serverOptions: WebStarterServerOptions

  var frameworkVersion: WebStarterFrameworkVersion? = null
  var packageName: String = DEFAULT_PACKAGE_NAME
  var languageLevel: StarterLanguageLevel? = null
  var packaging: StarterAppPackaging? = null

  val dependencies: MutableSet<WebStarterDependency> = HashSet()

  var result: DownloadResult? = null
}