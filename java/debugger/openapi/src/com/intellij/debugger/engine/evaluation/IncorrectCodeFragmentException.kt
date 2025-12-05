// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation

import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
class IncorrectCodeFragmentException(message: String) : EvaluateException(message)
