// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.instantiation

internal sealed interface Argument {

  @JvmInline
  value class LazyArgument(val argumentSupplier: ArgumentSupplier) : Argument

  data object CoroutineScopeMarker : Argument
}
