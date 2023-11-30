// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.instantiation

import java.lang.reflect.Constructor

internal sealed interface DependencyResolutionResult<T> {

  data class Resolved<T>(
    val constructor: Constructor<T>,
    val arguments: List<Argument>,
  ) : DependencyResolutionResult<T>

  data class Ambiguous<T>(
    val first: Resolved<T>,
    val second: Resolved<T>,
  ) : DependencyResolutionResult<T>

  data class Failed<T>(
    val unsatisfiableConstructors: List<UnsatisfiedConstructorParameterType<T>>,
  ) : DependencyResolutionResult<T>
}
