package com.intellij.platform.diagnostic.plugin.freeze

import com.intellij.DynamicBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

internal object PluginFreezeBundle {

  private const val BUNDLE: String = "messages.PluginFreezeBundle"
  private val INSTANCE: DynamicBundle = DynamicBundle(PluginFreezeBundle::class.java, BUNDLE)

  @JvmStatic
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): @Nls String =
    INSTANCE.getMessage(key, *params)
}