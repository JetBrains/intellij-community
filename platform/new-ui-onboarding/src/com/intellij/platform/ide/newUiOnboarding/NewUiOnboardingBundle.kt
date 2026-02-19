// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding

import com.intellij.DynamicBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.NewUiOnboardingBundle"

@ApiStatus.Internal
object NewUiOnboardingBundle {
  private val instance = DynamicBundle(NewUiOnboardingBundle::class.java, BUNDLE)

  @Nls
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String = instance.getMessage(key, *params)
}