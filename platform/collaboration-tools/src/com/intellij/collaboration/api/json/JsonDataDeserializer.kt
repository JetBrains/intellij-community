// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.json

import org.jetbrains.annotations.ApiStatus
import java.io.Reader

@ApiStatus.Experimental
interface JsonDataDeserializer {
  fun <T> fromJson(bodyReader: Reader, clazz: Class<T>): T
  fun <T> fromJson(bodyReader: Reader, clazz: Class<T>, vararg classArgs: Class<*>): T
}