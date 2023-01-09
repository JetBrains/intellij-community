// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.util

import java.lang.IllegalArgumentException


inline fun <reified T : Enum<T>> String.toEnumOrNull(): T? {
  return try {
    java.lang.Enum.valueOf(T::class.java, this)
  }
  catch (ex: IllegalArgumentException) {
    null
  }
}