// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
val GlobalInspectionContextKey = Key<GlobalInspectionContext>("GlobalInspectionContext")

@ApiStatus.Internal
fun LocalInspectionToolSession.globalInspectionContext(): GlobalInspectionContext? {
  return getUserData(GlobalInspectionContextKey)
}