// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs

import kotlin.reflect.KType

interface VirtualFileExtras<TData : Any> {
  val id: String get() = javaClass.name

  val dataType: KType
}