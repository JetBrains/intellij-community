// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore

import com.intellij.DynamicBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

@NonNls
private const val BUNDLE = "messages.CredentialStoreBundle"

@ApiStatus.Internal
object CredentialStoreBundle {
  private val bundle = DynamicBundle(CredentialStoreBundle::class.java, BUNDLE)

  val passwordSafeConfigurable: @Nls String
    get() = message("password.safe.configurable")

  fun message(
    @PropertyKey(resourceBundle = BUNDLE) key: String,
    vararg params: Any,
  ): @Nls String {
    return bundle.getMessage(key, *params)
  }

  fun messagePointer(
    @PropertyKey(resourceBundle = BUNDLE) key: String,
    vararg params: Any,
  ): Supplier<@Nls String> {
    return bundle.getLazyMessage(key, *params)
  }
}
