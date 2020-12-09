// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui

import com.intellij.openapi.application.ApplicationManager

interface ToolbarSettings {

  companion object {
    fun getInstance(): ToolbarSettings {
      return ApplicationManager.getApplication().getService(ToolbarSettings::class.java)
    }
  }
  fun isNavBarVisible(): Boolean

  fun setNavBarVisible(b: Boolean)

  fun isToolbarVisible(): Boolean

  fun setToolbarVisible(b: Boolean)

  fun getShowToolbarInNavigationBar(): Boolean

}