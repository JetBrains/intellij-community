// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic

import com.intellij.diagnostic.ThreadDump
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus

/**
 * Implement this interface in a plugin to receive reports about unhandled exceptions and UI freezes
 * that occurred in the plugin's code during a user session.
 *
 * Register the implementation in `plugin.xml`:
 * ```xml
 * <extensions defaultExtensionNs="com.intellij">
 *   <errorReportSink implementation="my.plugin.MyErrorReportSink"/>
 * </extensions>
 * ```
 *
 * [submit] is called asynchronously (on a background thread) when an unhandled exception or a UI
 * freeze is attributed to this plugin. Use it to collect metrics or forward reports to a custom
 * backend.
 *
 * **Limitations:**
 * - Bundled IDE plugins are not notified about their errors.
 * - At most 10 000 exception reports per plugin per IDE session are forwarded.
 * - The IDE does not deduplicate or debounce reports; the same exception may be reported many
 *   times if it is thrown repeatedly. Implementations are responsible for deduplication and
 *   rate-limiting on their side.
 *
 * @see UnhandledExceptionReport
 * @see UnhandledFreezeReport
 */
@ApiStatus.Experimental
interface ErrorReportSink {
  suspend fun submit(report: UnhandledErrorReport)
}

@ApiStatus.Experimental
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