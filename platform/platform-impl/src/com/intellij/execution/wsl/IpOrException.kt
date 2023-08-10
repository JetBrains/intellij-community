// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.execution.ExecutionException

internal class IpOrException private constructor(private val result: Result<String>) {
  constructor(ip: String) : this(Result.success(ip))
  constructor(error: ExecutionException) : this(Result.failure(error))

  @Throws(ExecutionException::class)
  fun getIp(): String = result.getOrThrow()
}