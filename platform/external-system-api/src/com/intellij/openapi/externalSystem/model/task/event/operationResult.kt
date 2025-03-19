// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.task.event

import java.io.Serializable

open class OperationResult(val startTime: Long, val endTime: Long) : Serializable

class FailureResult(startTime: Long, endTime: Long, val failures: List<Failure>) : OperationResult(startTime = startTime, endTime = endTime)

class SkippedResult(startTime: Long, endTime: Long) : OperationResult(startTime = startTime, endTime = endTime)

class SuccessResult(startTime: Long, endTime: Long, val isUpToDate: Boolean) : OperationResult(startTime = startTime, endTime = endTime)