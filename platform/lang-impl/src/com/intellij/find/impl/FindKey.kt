// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.openapi.util.registry.Registry

object FindKey {
  val isEnabled: Boolean = Registry.`is`("find.in.files.split")
}