package com.intellij.platform.ijent.community.ui.actions

import com.intellij.DynamicBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

@ApiStatus.Internal
object IjentImplBundle {
  private const val BUNDLE: String = "messages.IjentImplBundle"
  private val INSTANCE = DynamicBundle(IjentImplBundle::class.java, BUNDLE)

  @Nls
  fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any?): String {
    return INSTANCE.getMessage(key, *params)
  }
}
