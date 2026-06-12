// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface FixWithConsent {
  fun giveConsent(): Unit
  fun hasConsent(): Boolean
}