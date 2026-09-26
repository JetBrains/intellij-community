// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.portsWatcher

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
enum class PortListeningOptions {
  INCLUDE_SELF,
  INCLUDE_CHILDREN,
  INCLUDE_SELF_AND_CHILDREN;

  fun includesSelf(): Boolean = this == INCLUDE_SELF || this == INCLUDE_SELF_AND_CHILDREN
  fun includesChildren(): Boolean = this == INCLUDE_CHILDREN || this == INCLUDE_SELF_AND_CHILDREN
}