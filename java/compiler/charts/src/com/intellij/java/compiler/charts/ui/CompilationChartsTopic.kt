// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts.ui

import com.intellij.java.compiler.charts.events.ModuleChartEvent
import com.intellij.java.compiler.charts.events.StatisticChartEvent
import com.intellij.java.compiler.charts.impl.CompilationChartsViewModel.Filter
import com.intellij.java.compiler.charts.impl.ModuleKey
import com.jetbrains.rd.util.reactive.IViewableList
import com.jetbrains.rd.util.reactive.IViewableMap
import kotlinx.collections.immutable.PersistentList

sealed class CompilationChartsTopic<T> {
  object MODULE : CompilationChartsTopic<IViewableMap.Event<ModuleKey, PersistentList<ModuleChartEvent>>>()
  object STATISTIC : CompilationChartsTopic<IViewableList.Event<StatisticChartEvent>>()
  object FILTER : CompilationChartsTopic<Filter>()
}