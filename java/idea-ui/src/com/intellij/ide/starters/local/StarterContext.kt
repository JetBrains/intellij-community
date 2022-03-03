// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starters.local

import com.intellij.ide.starters.shared.*
import com.intellij.openapi.util.UserDataHolderBase

class StarterContext : UserDataHolderBase() {
  var isCreatingNewProject: Boolean = false
  var gitIntegration: Boolean = false

  lateinit var starterPack: StarterPack
  var starter: Starter? = null
  var starterDependencyConfig: DependencyConfig? = null
  val startersDependencyUpdates: MutableMap<String, DependencyConfig> = mutableMapOf()

  var group: String = DEFAULT_MODULE_GROUP
  var artifact: String = DEFAULT_MODULE_ARTIFACT
  var version: String = DEFAULT_MODULE_VERSION

  lateinit var language: StarterLanguage

  var projectType: StarterProjectType? = null
  var testFramework: StarterTestRunner? = null
  var applicationType: StarterAppType? = null
  var includeExamples: Boolean = true

  val libraryIds: MutableSet<String> = HashSet()
}