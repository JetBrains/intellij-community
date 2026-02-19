// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi

import com.intellij.openapi.util.Key
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object LanguageLevelKey {
  @JvmField
  val FILE_LANGUAGE_LEVEL_KEY: Key<LanguageLevel> = Key.create("FORCE_LANGUAGE_LEVEL")
}