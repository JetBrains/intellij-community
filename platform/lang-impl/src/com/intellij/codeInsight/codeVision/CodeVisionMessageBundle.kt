package com.intellij.codeInsight.codeVision

import com.intellij.DynamicBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

@ApiStatus.Internal
object CodeVisionMessageBundle {
  private const val BUNDLE = "messages.CodeVisionBundle"
  private val instance = DynamicBundle(CodeVisionMessageBundle::class.java, BUNDLE)

  @Nls
  fun message(
    @PropertyKey(resourceBundle = BUNDLE) key: String,
    vararg params: Any,
  ): String {
    return instance.messageOrNull(key, *params) ?: ""
  }

  fun messagePointer(
    @PropertyKey(resourceBundle = BUNDLE) key: String,
    vararg params: Any,
  ): Supplier<String> {
    return instance.getLazyMessage(key, *params)
  }
}