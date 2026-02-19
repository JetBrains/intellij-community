// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.model

import org.jetbrains.intellij.build.productLayout.model.error.ValidationError
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Collects validation errors during model building stage.
 *
 * **Why ErrorSink exists separately from [org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext.emitError]:**
 *
 * [org.jetbrains.intellij.build.productLayout.pipeline.ModelBuildingStage] runs BEFORE the pipeline nodes execute.
 * It creates [org.jetbrains.intellij.build.productLayout.pipeline.GenerationModel] which is then passed to
 * [org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext]. Since ComputeContext doesn't exist yet
 * during model building, xi:include errors discovered during plugin content extraction cannot use `ctx.emitError()`.
 *
 * Converting ModelBuildingStage to a PipelineNode would require a two-phase context (bootstrap context without model,
 * then full context with model), adding more complexity than it removes.
 *
 * @see org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext.emitError
 */
internal class ErrorSink {
  private val errors = CopyOnWriteArrayList<ValidationError>()

  fun emit(error: ValidationError) {
    errors.add(error)
  }

  fun getErrors(): List<ValidationError> = errors
}
