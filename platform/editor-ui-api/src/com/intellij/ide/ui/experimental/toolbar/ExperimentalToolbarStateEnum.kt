// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.experimental.toolbar

/**
 * New toolbar state plus support legacy toolbar
 */
enum class ExperimentalToolbarStateEnum(val oldToolbarVisible: Boolean, val newToolbarVisible: Boolean, val navBarVisible: Boolean) {
  NEW_TOOLBAR_WITH_NAVBAR(false, true, true),
  NEW_TOOLBAR_WITHOUT_NAVBAR(false, true, false),
  OLD_NAVBAR(false, false, true), //navbar with old toolbar integrated
  OLD_TOOLBAR_WITH_NAVBAR_SEPARATE(true, false, true),
  OLD_TOOLBAR_WITHOUT_NAVBAR(true, false, false),
  NO_TOOLBAR_NO_NAVBAR(false, false, false)
  //only navbar option without any toolbar - was not possible
}