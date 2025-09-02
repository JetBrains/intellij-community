// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomDataSynchronizer
import com.intellij.psi.codeStyle.JavaCodeStyleSettings

class JavaCodeStyleSettingsCustomDataSynchronizer  : CodeStyleSettingsCustomDataSynchronizer<JavaCodeStyleSettings>() {
  override val language: JavaLanguage get() = JavaLanguage.INSTANCE

  override val customCodeStyleSettingsClass: Class<JavaCodeStyleSettings> get() = JavaCodeStyleSettings::class.java
}