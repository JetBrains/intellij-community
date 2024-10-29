// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("EelPathResultImpl")
package com.intellij.platform.eel.path

import com.intellij.platform.eel.EelResult

internal data class OkResult<P : EelPath>(override val value: P) : EelResult.Ok<P, EelPathError>
internal data class ErrorResult<P : EelPath>(override val error: EelPathError) : EelResult.Error<P, EelPathError>
internal data class Err(override val raw: String, override val reason: String) : EelPathError