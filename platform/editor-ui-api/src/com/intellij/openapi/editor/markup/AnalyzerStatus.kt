// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.markup

import javax.swing.Icon

class AnalyzerStatus(val icon: Icon?, val showNavigation: Boolean) {
  companion object {
    fun icon(analyzerStatus: AnalyzerStatus?): Icon? = analyzerStatus?.icon
    fun showNavigation(analyzerStatus: AnalyzerStatus?):Boolean = analyzerStatus?.showNavigation ?: false
    fun otherIcon(otherIcon: Icon?, analyzerStatus: AnalyzerStatus?):Boolean = icon(analyzerStatus) != otherIcon
  }
}