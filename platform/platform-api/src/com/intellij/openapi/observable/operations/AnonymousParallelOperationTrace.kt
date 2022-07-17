// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.operations

import com.intellij.openapi.observable.operations.CompoundParallelOperationTrace.Companion.task
import org.jetbrains.annotations.NonNls

class AnonymousParallelOperationTrace private constructor(
  private val delegate: CompoundParallelOperationTrace<Nothing?>
) : ObservableOperationTrace by delegate {
  constructor(@NonNls debugName: String? = null) : this(CompoundParallelOperationTrace<Nothing?>(debugName))

  fun startTask() = delegate.startTask(null)
  fun finishTask() = delegate.finishTask(null)

  companion object {
    fun <R> AnonymousParallelOperationTrace.task(action: () -> R): R = delegate.task(null, action)
  }
}