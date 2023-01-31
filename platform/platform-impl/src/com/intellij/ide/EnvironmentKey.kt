// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.util.function.Supplier

/**
 * Represents a key for a value defined in user environment.
 * The preferable way to work with this class is to store its instances in a static field after calling [createKey].
 *
 * To get a value for a key, use [EnvironmentParametersService].
 * **Note**: every key must be registered in some [EnvironmentKeyRegistry].
 */
sealed interface EnvironmentKey {

  /**
   * Representation of a key in the user environment.
   * By convention, all ids look like "foo.bar.baz"
   */
  fun getId(): @NonNls String

  /**
   * The purpose of this key and the format of its values.
   */
  fun getDescription(): Supplier<@Nls String>


  companion object {
    @JvmStatic
    fun createKey(id: @NonNls String, description: Supplier<@Nls String>): EnvironmentKey {
      return EnvironmentKeyImpl(id, description)
    }

    private data class EnvironmentKeyImpl(private val id: @NonNls String, private val description: Supplier<@Nls String>) : EnvironmentKey {
      override fun getId(): String = id
      override fun getDescription(): Supplier<String> = description

      override fun equals(other: Any?): Boolean = other is EnvironmentKeyImpl && other.id == this.id

      override fun hashCode(): Int = id.hashCode()
    }
  }
}


