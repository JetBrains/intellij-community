// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.java

import com.intellij.java.workspace.entities.JavaModuleSettingsEntity
import com.intellij.pom.java.LanguageLevel

public var JavaModuleSettingsEntity.Builder.languageLevel: LanguageLevel?
  get() = idToLanguageLevel(languageLevelId)
  set(value) {
    languageLevelId = value?.name
  }

public val JavaModuleSettingsEntity.languageLevel: LanguageLevel?
  get() = idToLanguageLevel(languageLevelId)

private fun idToLanguageLevel(id: String?): LanguageLevel? {
  return try {
    LanguageLevel.valueOf(id ?: return null)
  }
  catch (e: IllegalArgumentException) {
    null
  }
}