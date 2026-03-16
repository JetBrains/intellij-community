// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.openapi.components.serviceOrNull
import com.intellij.psi.codeStyle.MinusculeMatcher

interface ChooseByNameMatcherFactory {
  companion object {
    fun tryGetInstance(): ChooseByNameMatcherFactory? = serviceOrNull<ChooseByNameMatcherFactory>()
  }

  fun createMatcher(pattern: String, preferStartMatches: Boolean): MinusculeMatcher?
}