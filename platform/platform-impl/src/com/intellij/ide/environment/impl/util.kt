// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.environment.impl

import com.intellij.ide.environment.DefaultedEnvironmentKey
import com.intellij.ide.environment.EnvironmentKey

class EnvironmentConfiguration(private val map: Map<EnvironmentKey, String>) {
  companion object {
    val EMPTY : EnvironmentConfiguration = EnvironmentConfiguration(emptyMap())
  }
  fun get(key: EnvironmentKey) : String? = map[key]
  fun get(key: DefaultedEnvironmentKey) : String = get(key as EnvironmentKey) ?: key.defaultValue
}

