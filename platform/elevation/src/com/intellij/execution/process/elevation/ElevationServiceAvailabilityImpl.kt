// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process.elevation

import com.intellij.execution.process.ElevationServiceAvailability

class ElevationServiceAvailabilityImpl : ElevationServiceAvailability {
  override fun isAvailable() = true
}