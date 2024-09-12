// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts

import com.intellij.compiler.server.BuildProcessParametersProvider
import com.intellij.java.compiler.charts.CompilationChartsProjectActivity.Companion.COMPILATION_CHARTS_KEY
import com.intellij.openapi.util.registry.Registry

class CompilationChartsBuildParametersProvider: BuildProcessParametersProvider() {
  override fun getVMArguments() = listOf("-D${COMPILATION_CHARTS_KEY}=${Registry.`is`(COMPILATION_CHARTS_KEY)}")
}