// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionPlaces

class PopupInMainMenuActionGroup : NonTrivialActionGroup() {
  override fun isPopup(place: String): Boolean {
    return place == ActionPlaces.MAIN_MENU
  }
}
