// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.split

import com.intellij.json.JsonLanguage
import com.intellij.json.formatter.JsonCodeStyleSettings
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomDataSynchronizer

//todo split on two separate classes for the backend and the frontend
@InternalIgnoreDependencyViolation
class JsonCodeStyleSettingsCustomDataSynchronizer : CodeStyleSettingsCustomDataSynchronizer<JsonCodeStyleSettings>() {
  override val language
    get() = JsonLanguage.INSTANCE

  override val customCodeStyleSettingsClass
    get() = JsonCodeStyleSettings::class.java
}