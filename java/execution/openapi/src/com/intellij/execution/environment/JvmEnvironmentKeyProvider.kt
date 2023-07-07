// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.environment

import com.intellij.ide.environment.EnvironmentKey
import com.intellij.ide.environment.EnvironmentKeyProvider
import com.intellij.java.JavaBundle
import com.intellij.openapi.project.Project

class JvmEnvironmentKeyProvider : EnvironmentKeyProvider {

  object Keys {
    val JDK_KEY = EnvironmentKey.create("project.jdk")
    val JDK_NAME = EnvironmentKey.create("project.jdk.name")
  }

  override val knownKeys: Map<EnvironmentKey, String> =
    mapOf(Keys.JDK_KEY to JavaBundle.message("environment.key.description.project.jdk"),
          Keys.JDK_NAME to JavaBundle.message("environment.key.description.project.jdk.name"),
    )

  override suspend fun getRequiredKeys(project: Project): List<EnvironmentKey> = listOf()
}