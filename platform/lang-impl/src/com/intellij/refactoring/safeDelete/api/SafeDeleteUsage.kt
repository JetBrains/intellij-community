// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete.api

import com.intellij.model.Pointer
import org.jetbrains.annotations.Nls

interface SafeDeleteUsage {
  fun createPointer(): Pointer<out SafeDeleteUsage>
  var conflictMessage : @Nls String?

  var isSafeToDelete : Boolean
  val fileUpdater: FileUpdater
}
