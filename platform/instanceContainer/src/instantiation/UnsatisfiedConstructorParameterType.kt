// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.instantiation

import java.lang.reflect.Constructor

internal data class UnsatisfiedConstructorParameterType<T>(
  val constructor: Constructor<T>,
  val parameterType: Class<*>,
)
