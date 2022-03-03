// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies.telemetry

import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull

@CompileStatic
interface BuildDependenciesTraceEventAttributes {
  void setAttribute(@NotNull String name, @NotNull String value)
}
