// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.split

import com.intellij.json.JsonLanguage
import com.intellij.json.formatter.JsonCodeStyleSettings
import com.intellij.lang.Language
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomDataSynchronizer

internal class JsonCodeStyleSettingsCustomDataSynchronizer : CodeStyleSettingsCustomDataSynchronizer<JsonCodeStyleSettings>() {
  override val language: Language
    get() = JsonLanguage.INSTANCE

  override val customCodeStyleSettingsClass: Class<JsonCodeStyleSettings>
    get() = JsonCodeStyleSettings::class.java
}