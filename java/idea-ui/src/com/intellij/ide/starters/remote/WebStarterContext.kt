// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starters.remote

import com.intellij.ide.starters.shared.*
import com.intellij.openapi.util.UserDataHolderBase

class WebStarterContext : UserDataHolderBase() {
  var name: String = DEFAULT_MODULE_NAME
  var group: String = DEFAULT_MODULE_GROUP
  var artifact: String = DEFAULT_MODULE_ARTIFACT
  var version: String = DEFAULT_MODULE_VERSION

  var isCreatingNewProject: Boolean = false
  var gitIntegration: Boolean = false

  lateinit var serverUrl: String
  lateinit var serverOptions: WebStarterServerOptions

  lateinit var language: StarterLanguage

  var frameworkVersion: WebStarterFrameworkVersion? = null
  var projectType: StarterProjectType? = null
  var packageName: String = DEFAULT_PACKAGE_NAME
  var languageLevel: StarterLanguageLevel? = null
  var packaging: StarterAppPackaging? = null
  var applicationType: StarterAppType? = null
  var testFramework: StarterTestRunner? = null
  var includeExamples: Boolean = true

  val dependencies: MutableSet<WebStarterDependency> = HashSet()

  var result: DownloadResult? = null
}