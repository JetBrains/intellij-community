// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts

interface TargetEvent {
  val name: String
  val type: String
  val isTest: Boolean
  val isFileBased: Boolean
  val time: Long
  val thread: Long
}

data class StartTarget(
  override val name: String,
  override val type: String,
  override val isTest: Boolean,
  override val isFileBased: Boolean,
  override val thread: Long,
  override val time: Long,
) : TargetEvent

data class FinishTarget(
  override val name: String,
  override val type: String,
  override val isTest: Boolean,
  override val isFileBased: Boolean,
  override val thread: Long,
  override val time: Long,
) : TargetEvent

data class CpuMemoryStatistics(
  val heapUsed: Long,
  val heapMax: Long,
  val nonHeapUsed: Long,
  val nonHeapMax: Long,
  val cpu: Long,
  val time: Long,
)