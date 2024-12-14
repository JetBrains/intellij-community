// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider

import com.intellij.platform.eel.EelResult

data class ResultOkImpl<P>(override val value: P) : EelResult.Ok<P>
data class ResultErrImpl<E>(override val error: E) : EelResult.Error<E>