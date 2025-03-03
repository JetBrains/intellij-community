// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.parser.elements

class DependsElement(
  @JvmField val pluginId: String,
  @JvmField val isOptional: Boolean,
  @JvmField val configFile: String?,
)