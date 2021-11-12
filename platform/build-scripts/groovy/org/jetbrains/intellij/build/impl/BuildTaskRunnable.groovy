// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildContext

import java.util.function.Consumer

@CompileStatic
final class BuildTaskRunnable {
  final @NotNull String stepId
  final @NotNull String stepMessage
  final Consumer<BuildContext> task

  BuildTaskRunnable(@NotNull String stepId, @NotNull String stepMessage, @NotNull Consumer<BuildContext> task) {
    this.stepId = stepId
    this.stepMessage = stepMessage
    this.task = task
  }

  static BuildTaskRunnable task(@NotNull String name, @NotNull Consumer<BuildContext> task) {
    return new BuildTaskRunnable(name, name, task)
  }
}
