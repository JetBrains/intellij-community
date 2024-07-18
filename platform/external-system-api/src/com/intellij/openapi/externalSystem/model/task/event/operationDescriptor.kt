// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.task.event

import org.jetbrains.annotations.Nls
import java.io.Serializable

open class OperationDescriptor(val displayName: @Nls String, val eventTime: Long) : Serializable {
  var hint: @Nls String? = null
}

class TaskOperationDescriptor(displayName: @Nls String, eventTime: Long, val taskName: String)
  : OperationDescriptor(displayName = displayName, eventTime = eventTime)

class TestOperationDescriptor(
  displayName: @Nls String,
  eventTime: Long,
  val suiteName: String?,
  val className: String?,
  val methodName: String?,
) : OperationDescriptor(displayName = displayName, eventTime = eventTime)