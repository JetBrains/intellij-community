// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.openapi.extensions.ExtensionPointName

interface BuildSystemType {
  val name: String

  companion object {
    var EP_BUILD_SYSTEM = ExtensionPointName<BuildSystemType>("com.intellij.newProjectWizard.buildSystem")
  }
}

object GradleGroovy : BuildSystemType {
  override val name = "Gradle Groovy"
}

object GradleKotlin : BuildSystemType {
  override val name = "Gradle Kotlin"
}

object Maven : BuildSystemType {
  override val name = "Maven"
}

object Intellij : BuildSystemType {
  override val name = "Intellij"
}