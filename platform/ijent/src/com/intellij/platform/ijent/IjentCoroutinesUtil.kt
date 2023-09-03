// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext

// TODO It is a copy-paste from Fleet, and it's better be generalized and put into some generic place.
fun CoroutineScope.coroutineNameAppended(name: String, separator: String = " > "): CoroutineContext =
  coroutineContext.coroutineNameAppended(name, separator)

fun CoroutineContext.coroutineNameAppended(name: String, separator: String = " > "): CoroutineContext {
  val parentName = this[CoroutineName]?.name
  return CoroutineName(if (parentName == null) name else parentName + separator + name)
}