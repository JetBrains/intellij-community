// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import org.jetbrains.annotations.ApiStatus.Internal

/**
 * @param verbose provides a way to disable by default some tracers.
 * Such tracers will be created only if additional system property "verbose" is set to true.
 */
@Internal
data class Scope @JvmOverloads constructor(val name: String, val parent: Scope? = null, val verbose: Boolean = false) {
  override fun toString(): String = if (parent == null) name else "$parent.$name"
}