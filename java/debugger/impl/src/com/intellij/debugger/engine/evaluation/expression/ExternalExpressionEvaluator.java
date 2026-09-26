// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation.expression;

import org.jetbrains.annotations.ApiStatus;

/**
 * Marks evaluators that are not intended to work with Java expressions.
 */
@ApiStatus.Internal
public interface ExternalExpressionEvaluator {
}
