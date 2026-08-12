// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class EssentialPluginMissingException internal constructor(val pluginIds: List<String>) :
  RuntimeException("Missing essential plugins: ${pluginIds.joinToString()}")
