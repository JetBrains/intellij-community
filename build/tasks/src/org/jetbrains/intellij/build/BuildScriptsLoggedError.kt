// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

class BuildScriptsLoggedError(message: String, cause: Throwable? = null)
  : RuntimeException(message, cause)