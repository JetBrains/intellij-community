// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.json

import org.jetbrains.annotations.ApiStatus
import java.io.Reader

@ApiStatus.Experimental
interface JsonDataDeserializer {
  /**
   * Parses a value of the given type from the given reader.
   *
   * The reader is not closed by this function. It should be managed by the caller.
   */
  fun <T> fromJson(bodyReader: Reader, clazz: Class<T>): T?
  /**
   * Parses a value of type T = L<A, B> from the given reader.
   * Type T is given by [clazz], whereas A and B are given through [classArgs].
   *
   * The reader is not closed by this function. It should be managed by the caller.
   */
  fun <T> fromJson(bodyReader: Reader, clazz: Class<T>, vararg classArgs: Class<*>): T?
}