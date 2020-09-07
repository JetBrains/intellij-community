// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

@NonNls
private const val BUNDLE = "messages.CredentialStoreBundle"

object CredentialStoreBundle : DynamicBundle(BUNDLE) {
  @Nls
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String,
              vararg params: Any): String {
    return getMessage(key, *params)
  }

  fun messagePointer(@PropertyKey(resourceBundle = BUNDLE) key: String,
                  vararg params: Any): Supplier<@Nls String> {
    return getLazyMessage(key, *params)
  }
}