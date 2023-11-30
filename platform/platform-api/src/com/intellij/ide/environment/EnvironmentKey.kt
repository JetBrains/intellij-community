// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.environment

import com.intellij.ide.environment.EnvironmentKey.Companion.create
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

/**
 * Represents a key for a value defined in user environment.
 * The preferable way to work with this class is to store its instances in a static field after calling [create].
 *
 * To get a value for a key, use [EnvironmentService].
 * **Note**: every key must be registered in some [EnvironmentKeyProvider.knownKeys].
 */
sealed interface EnvironmentKey {

  /**
   * Representation of a key in the user environment.
   * By convention, all IDs look like "foo.bar.baz"
   */
  val id: @NonNls String

  companion object {

    @JvmStatic
    fun create(id: @NonNls String): EnvironmentKey {
      require(regex.matches(id))
      return EnvironmentKeyImpl(id)
    }

    private val regex: Regex = Regex("^[a-z0-9]+(\\.[a-z0-9]+)*$")

    private open class EnvironmentKeyImpl(
      override val id: @NonNls String) : EnvironmentKey {
      override fun equals(other: Any?): Boolean = other is EnvironmentKeyImpl && other.id == this.id
      override fun toString(): String = id

      override fun hashCode(): Int = id.hashCode()
    }
  }
}

val EnvironmentKey.description: @Nls String
  get() = EnvironmentKeyProvider.EP_NAME.extensionList.firstNotNullOfOrNull { provider ->
    provider.knownKeys[this]?.get()
  } ?: error("Key ${this.id} must be registered in some ${EnvironmentKeyProvider}")
