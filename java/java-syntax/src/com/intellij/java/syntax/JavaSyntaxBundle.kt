// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax

import com.intellij.platform.syntax.i18n.ResourceBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@ApiStatus.Internal
object JavaSyntaxBundle {
  const val BUNDLE: @NonNls String = "messages.JavaSyntaxBundle"

  val resourceBundle: ResourceBundle = ResourceBundle("com.intellij.java.syntax.JavaSyntaxBundle", BUNDLE, this)

  @JvmStatic
  fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): @Nls String {
    return resourceBundle.message(key, *params)
  }

  @JvmStatic
  fun messagePointer(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): () -> @Nls String {
    return resourceBundle.messagePointer(key, *params)
  }
}
