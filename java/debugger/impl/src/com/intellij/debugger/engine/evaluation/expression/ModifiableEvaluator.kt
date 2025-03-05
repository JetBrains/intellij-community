// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation.expression

import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl

interface ModifiableEvaluator : Evaluator {
  @Throws(EvaluateException::class)
  override fun evaluate(context: EvaluationContextImpl): Any? = evaluateModifiable(context).value

  @Throws(EvaluateException::class)
  override fun evaluateModifiable(context: EvaluationContextImpl): ModifiableValue
}

/**
 * Represents a value that can be modified. This class encapsulates a value and an optional modifier
 * that allows modifications to the value.
 *
 * @property value The value being encapsulated, which can be of any type or null.
 * @property modifier The modifier associated with the value, which provides functionality for modifying the value.
 */
class ModifiableValue(val value: Any?, val modifier: Modifier?)
