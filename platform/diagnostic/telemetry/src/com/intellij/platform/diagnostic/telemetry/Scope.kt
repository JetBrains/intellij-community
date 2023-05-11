// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

open class Scope(val name: String, val parent: Scope? = null) {
  override fun toString(): String {
    if (parent == null)
      return name
    return "${parent.name}.$name"
  }
}