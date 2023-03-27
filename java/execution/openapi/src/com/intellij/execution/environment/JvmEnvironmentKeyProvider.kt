// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.environment

import com.intellij.ide.environment.EnvironmentKey
import com.intellij.ide.environment.EnvironmentKeyProvider
import com.intellij.java.JavaBundle
import com.intellij.openapi.project.Project

class JvmEnvironmentKeyProvider : EnvironmentKeyProvider {

  companion object {
    val JDK_KEY = EnvironmentKey.create("project.jdk", JavaBundle.messagePointer("environment.key.description.project.jdk"))
    val JDK_NAME = EnvironmentKey.createWithDefaultValue("project.jdk.name", JavaBundle.messagePointer("environment.key.description.project.jdk.name"), "warmup_jdk")
  }
  override fun getAllKeys(): List<EnvironmentKey> = listOf(JDK_KEY, JDK_NAME)

  override suspend fun getRequiredKeys(project: Project): List<EnvironmentKey> = listOf()
}