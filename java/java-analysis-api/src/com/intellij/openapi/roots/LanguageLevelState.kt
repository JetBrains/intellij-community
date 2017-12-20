/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.roots

import com.intellij.openapi.components.BaseState
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.xmlb.annotations.Attribute

class LanguageLevelState : BaseState() {
  @get:Attribute("LANGUAGE_LEVEL")
  var languageLevel by property<LanguageLevel?>()
}