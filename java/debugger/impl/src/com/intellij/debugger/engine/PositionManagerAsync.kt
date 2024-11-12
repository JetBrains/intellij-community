// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.SourcePosition
import com.sun.jdi.Location
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

@ApiStatus.Experimental
interface PositionManagerAsync {
  fun getSourcePositionAsync(location: Location?): CompletableFuture<SourcePosition?>
}
