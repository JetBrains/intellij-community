// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.commandInterface

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

private const val BUNDLE: @NonNls String = "messages.CommandInterfaceBundle"

internal object CommandInterfaceBundle {
  private val instance = DynamicBundle(CommandInterfaceBundle::class.java, BUNDLE)

  @JvmStatic
  fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): @Nls String {
    return instance.getMessage(key, *params)
  }

  @JvmStatic
  fun messagePointer(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): Supplier<String> {
    return instance.getLazyMessage(key, *params)
  }
}
