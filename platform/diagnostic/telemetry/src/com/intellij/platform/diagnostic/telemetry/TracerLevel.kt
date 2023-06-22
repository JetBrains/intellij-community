// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

/**
 * Defines level of details for the tracing information.
 * In most cases, we don't need very-deep information about all possible subsystems.
 */
enum class TracerLevel {
  DEFAULT,
  DETAILED,
}