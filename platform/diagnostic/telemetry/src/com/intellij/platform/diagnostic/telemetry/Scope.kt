// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

open class Scope @JvmOverloads constructor(val name: String, val parent: Scope? = null, val verbose: Boolean = false) {
  override fun toString(): String = if (parent == null) name else "${parent.name}.$name"
}