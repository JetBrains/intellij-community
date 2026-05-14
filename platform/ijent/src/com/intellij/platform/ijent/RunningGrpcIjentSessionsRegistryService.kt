// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.util.containers.CollectionFactory
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object RunningGrpcIjentSessionsRegistryService {
  val sessions: MutableMap<IjentSession, IjentScope> = CollectionFactory.createConcurrentWeakMap()
}
