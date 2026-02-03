// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.vmOptions

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

internal const val BUNDLE: String = "messages.VMOptionsBundle"
object VMOptionsBundle {
    private val INSTANCE = DynamicBundle(VMOptionsBundle::class.java, BUNDLE)

    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any?): @Nls String {
      return INSTANCE.getMessage(key = key, params = params)
    }

    @JvmStatic
    fun messagePointer(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any?): Supplier<@Nls String> {
      return INSTANCE.getLazyMessage(key = key, params = params)
    }
}