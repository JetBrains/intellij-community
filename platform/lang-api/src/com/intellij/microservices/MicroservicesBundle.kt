package com.intellij.microservices

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

private const val PATH_TO_BUNDLE = "messages.MicroservicesBundle"

object MicroservicesBundle : DynamicBundle(PATH_TO_BUNDLE) {
  @Nls
  @JvmStatic
  fun message(@PropertyKey(resourceBundle = PATH_TO_BUNDLE) key: String, vararg params: Any): String {
    return getMessage(key, *params)
  }

  @JvmStatic
  fun messagePointer(@PropertyKey(resourceBundle = PATH_TO_BUNDLE) key: String, vararg params: Any): Supplier<@Nls String> {
    return getLazyMessage(key, *params)
  }
}