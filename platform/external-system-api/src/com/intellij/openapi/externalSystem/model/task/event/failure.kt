// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.task.event

import org.jetbrains.annotations.Nls
import java.io.Serializable

open class Failure(
  val message: @Nls String?,
  val description: @Nls String?,
  val causes: List<Failure>,
) : Serializable

open class TestFailure(
  val exceptionName: String?,
  message: @Nls String?,
  val stackTrace: @Nls String?,
  description: @Nls String?,
  causes: List<Failure>,
  val isTestError: Boolean,
) : Failure(message = message, description = description, causes = causes)

class TestAssertionFailure @JvmOverloads constructor(
  exceptionName: String?,
  message: @Nls String?,
  stackTrace: @Nls String?,
  description: @Nls String?,
  causes: List<Failure>,
  val expectedText: String,
  val actualText: String,
  val expectedFile: String? = null,
  val actualFile: String? = null,
) : TestFailure(exceptionName = exceptionName,
                message = message,
                stackTrace = stackTrace,
                description = description,
                causes = causes,
                isTestError = false)
