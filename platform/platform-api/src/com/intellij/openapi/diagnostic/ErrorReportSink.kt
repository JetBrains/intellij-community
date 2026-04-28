// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic

import com.intellij.diagnostic.ThreadDump
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ErrorReportSink {
  suspend fun submit(report: UnhandledErrorReport)
}

sealed interface UnhandledErrorReport

@ApiStatus.Experimental
class UnhandledExceptionReport(
  val exceptionClass: Class<*>,
  val stackTrace: List<StackTraceElement>,
) : UnhandledErrorReport {
  constructor(t: Throwable) : this(t.javaClass, t.stackTrace.toList())
}

@ApiStatus.Experimental
class UnhandledFreezeReport(
  val message: String?,
  val durationMs: Long,
  val attachments: Collection<Attachment>,
  val threadDumps: Collection<ThreadDump>,
) : UnhandledErrorReport

@ApiStatus.Experimental
class ErrorReportSinkBean : BaseKeyedLazyInstance<ErrorReportSink>() {
  @Attribute("implementation")
  @JvmField
  @RequiredElement
  var implementation: String = ""

  @ApiStatus.Internal
  companion object {
    @get:ApiStatus.Internal
    val EP_NAME: ExtensionPointName<ErrorReportSinkBean> = ExtensionPointName.create("com.intellij.errorReportSink")
  }

  override fun getImplementationClassName(): String = implementation
}