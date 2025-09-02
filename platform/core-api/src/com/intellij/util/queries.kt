// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

fun <T, R : Any> Query<T>.mappingNotNull(f: (T) -> R?): Query<R> {
  return transforming { t: T ->
    val r: R? = f(t)
    if (r == null) {
      emptyList()
    }
    else {
      listOf(r)
    }
  }
}

fun <T> Query<T>.filteringNotNull(): Query<T & Any> {
  @Suppress("UNCHECKED_CAST")
  return filtering { it != null } as Query<T & Any>
}
