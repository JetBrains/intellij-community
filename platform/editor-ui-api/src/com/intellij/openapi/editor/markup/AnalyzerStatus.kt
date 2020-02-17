// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.markup

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.xml.util.XmlStringUtil
import javax.swing.Icon

class AnalyzerStatus(val icon: Icon?, statusText: String, val actionMenu: () -> AnAction) {
  val statusText = XmlStringUtil.wrapInHtml(statusText)
  var showNavigation = false

  fun withNavigation() : AnalyzerStatus {
    showNavigation = true
    return this
  }
}