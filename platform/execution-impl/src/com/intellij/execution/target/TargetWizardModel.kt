// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.openapi.project.Project

interface TargetWizardModel {
  val project: Project

  val subject: TargetEnvironmentConfiguration

  val languageConfigForIntrospection: LanguageRuntimeConfiguration?

  fun save() {}

  fun commit() {}
}