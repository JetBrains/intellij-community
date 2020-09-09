// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.experimental.toolbar

import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.OptionTag

class ExperimentalToolbarStateWrapper: BaseState() {

  @get:OptionTag("NEW_TOOLBAR_SETTINGS")
  var state by enum(ExperimentalToolbarStateEnum.NEW_TOOLBAR_WITHOUT_NAVBAR)
}