// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.proxy

import com.intellij.execution.wsl.WSLDistribution

internal data class WslProxyActionModel(val wslDistributions: List<WSLDistribution>, var port: Int = 5201, var selectedDistribution:WSLDistribution? = wslDistributions.first()) //perf port