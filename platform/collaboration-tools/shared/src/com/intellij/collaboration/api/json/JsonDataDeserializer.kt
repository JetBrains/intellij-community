// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.json

import org.jetbrains.annotations.ApiStatus
import java.io.InputStream
import java.io.Reader
import java.nio.charset.Charset

@ApiStatus.Experimental
interface JsonDataDeserializer {
  /**
   * Parses a value of the given type from the given [bodyReader].
   *
   * The reader is not closed by this function. It should be managed by the caller.
   */
  fun <T> fromJson(bodyReader: Reader, clazz: Class<T>): T?

  /**
   * Parses a value of type T = L<A, B> from the given [bodyReader].
   * Type T is given by [clazz], whereas A and B are given through [classArgs].
   *
   * The reader is not closed by this function. It should be managed by the caller.
   */
  fun <T> fromJson(bodyReader: Reader, clazz: Class<T>, vararg classArgs: Class<*>): T?

  /**
   * Read a value of the given type from the given [stream].
   *
   * The stream should not be closed by this function. It should be managed by the caller.
   */
  fun <T : Any> readJson(stream: InputStream, charset: Charset, clazz: Class<T>): T?

  /**
   * Read a value of type T = L<A, B> from the given [stream].
   * Type T is given by [clazz], whereas A and B are given through [classArgs].
   *
   * The stream should not be closed by this function. It should be managed by the caller.
   */
  fun <T : Any> readJson(stream: InputStream, charset: Charset, clazz: Class<T>, vararg classArgs: Class<*>): T?
}