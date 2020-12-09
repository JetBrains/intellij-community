// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildContext

import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

@CompileStatic
abstract class BuildTaskRunnable<T> {
  final String stepId

  protected BuildTaskRunnable(String stepId) {
    this.stepId = stepId
  }

  static <T> BuildTaskRunnable<T> taskWithResult(@NotNull String name, @NotNull Function<BuildContext, T> task) {
    return new BuildTaskRunnable<T>(name) {
      @Override
      T execute(BuildContext buildContext) {
        return task.apply(buildContext)
      }
    }
  }

  static BuildTaskRunnable<Void> task(@NotNull String name, @NotNull Consumer<BuildContext> task) {
    return new BuildTaskRunnable<Void>(name) {
      @Override
      Void execute(BuildContext buildContext) {
        task.accept(buildContext)
        return null
      }
    }
  }

  static BuildTaskRunnable<Void> task(@NotNull String stepId, @NotNull String stepMessage, @NotNull Consumer<BuildContext> task) {
    return new BuildTaskRunnable<Void>(stepId) {
      @Override
      Void execute(BuildContext buildContext) {
        if (buildContext.options.buildStepsToSkip.contains(stepId)) {
          buildContext.messages.info("Skipping '$stepMessage'")
          return null
        }

        buildContext.messages.block(stepMessage, new Supplier<Void>() {
          @Override
          Void get() {
            task.accept(buildContext)
            return null
          }
        })
        return null
      }
    }
  }

  abstract T execute(BuildContext buildContext)
}
