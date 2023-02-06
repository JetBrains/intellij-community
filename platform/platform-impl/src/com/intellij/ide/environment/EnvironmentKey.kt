// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.environment

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
   * By convention, all IDs look like "foo.bar.baz"
   */
  val id: @NonNls String

  /**
   * The purpose of this key and the format of its values.
   */
  val description: Supplier<@Nls String>

  /**
   * The default value for a key.
   * If [defaultValue] for a key is empty, then the key has **no** default value,
   * and [EnvironmentParametersService.requestEnvironmentValue] enters in its error handling state.
   */
  val defaultValue: @NonNls String

  companion object {

    @JvmStatic
    @JvmOverloads
    fun createKey(id: @NonNls String, description: Supplier<@Nls String>, defaultValue: @NonNls String = ""): EnvironmentKey {
      return EnvironmentKeyImpl(id, description, defaultValue)
    }

    private class EnvironmentKeyImpl(override val id: @NonNls String,
                                     override val description: Supplier<@Nls String>,
                                     override val defaultValue: @NonNls String) : EnvironmentKey {
      override fun equals(other: Any?): Boolean = other is EnvironmentKeyImpl && other.id == this.id

      override fun hashCode(): Int = id.hashCode()
    }
  }
}


