// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SystemLogger {
  fun info(data: () -> String)

  fun debug(data: () -> String)
}

@ApiStatus.Internal
interface SystemLoggerBuilder {
  fun build(clazz: Class<*>): SystemLogger
}
