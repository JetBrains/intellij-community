// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui.util

interface AsyncDistributionsInfo : DistributionsInfo {
  fun isReady(): Boolean
  fun prepare()
}