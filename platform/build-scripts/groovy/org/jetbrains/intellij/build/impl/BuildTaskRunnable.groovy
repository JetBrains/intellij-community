// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildContext

import java.util.function.Function

@CompileStatic
final class BuildTaskRunnable<T> {
  final String name
  final Function<BuildContext, T> task

  private BuildTaskRunnable(String name, Function<BuildContext, T> task) {
    this.name = name
    this.task = task
  }

  static <T> BuildTaskRunnable<T> create(@NotNull String name, @NotNull Function<BuildContext, T> task) {
    return new BuildTaskRunnable<T>(name, task)
  }
}
