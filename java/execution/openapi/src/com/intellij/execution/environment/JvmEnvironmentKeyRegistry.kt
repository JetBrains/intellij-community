// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.environment

import com.intellij.ide.environment.EnvironmentKey
import com.intellij.ide.environment.EnvironmentKeyRegistry
import com.intellij.java.JavaBundle
import com.intellij.openapi.project.Project

class JvmEnvironmentKeyRegistry : EnvironmentKeyRegistry {

  companion object {
    val JDK_KEY = EnvironmentKey.createKey("project.jdk", JavaBundle.messagePointer("environment.key.description.project.jdk"))
  }
  override fun getAllKeys(): List<EnvironmentKey> = listOf(JDK_KEY)

  override suspend fun getRequiredKeys(project: Project): List<EnvironmentKey> = listOf()
}