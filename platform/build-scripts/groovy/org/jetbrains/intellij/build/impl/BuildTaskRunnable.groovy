// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildContext

import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

@CompileStatic
abstract class BuildTaskRunnable<T> {
  final @NotNull String stepId
  final @NotNull String stepMessage

  protected BuildTaskRunnable(@NotNull String stepId, @NotNull String stepMessage) {
    this.stepId = stepId
    this.stepMessage = stepMessage
  }

  static <T> BuildTaskRunnable<T> taskWithResult(@NotNull String name, @NotNull Function<BuildContext, T> task) {
    return new BuildTaskRunnable<T>(name, name) {
      @Override
      T execute(BuildContext context) {
        return task.apply(context)
      }
    }
  }

  static BuildTaskRunnable<Void> task(@NotNull String name, @NotNull Consumer<BuildContext> task) {
    return new BuildTaskRunnable<Void>(name, name) {
      @Override
      Void execute(BuildContext context) {
        task.accept(context)
        return null
      }
    }
  }

  static BuildTaskRunnable<Void> task(@NotNull String stepId, @NotNull String stepMessage, @NotNull Consumer<BuildContext> task) {
    return new BuildTaskRunnable<Void>(stepId, stepMessage) {
      @Override
      Void execute(BuildContext context) {
        context.messages.block(stepMessage, new Supplier<Void>() {
          @Override
          Void get() {
            task.accept(context)
            return null
          }
        })
        return null
      }
    }
  }

  abstract T execute(BuildContext context)
}
