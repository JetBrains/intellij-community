// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef

import com.intellij.openapi.extensions.ExtensionPointName

interface JBCefAppRequiredArgumentsProvider {
  companion object {
    var EP = ExtensionPointName<JBCefAppRequiredArgumentsProvider>("com.intellij.jcef.appRequiredArgumentsProvider")

    @JvmStatic
    fun getProviders(): List<JBCefAppRequiredArgumentsProvider> = EP.extensionList
  }

  val options: List<String>
}