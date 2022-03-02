package com.intellij.codeInsight.codeVision

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

class CodeVisionBundle : DynamicBundle(BUNDLE) {
  companion object {
    @NonNls
    private const val BUNDLE = "messages.CodeVisionBundle"
    private val INSTANCE: CodeVisionBundle = CodeVisionBundle()

    @Nls
    fun message(
      @PropertyKey(resourceBundle = BUNDLE) key: String,
      vararg params: Any
    ): String {
      return INSTANCE.messageOrNull(key, *params) ?: ""
    }

    fun messagePointer(
      @PropertyKey(resourceBundle = BUNDLE) key: String,
      vararg params: Any
    ): Supplier<String> {
      return INSTANCE.getLazyMessage(key, *params)
    }
  }
}