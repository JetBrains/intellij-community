// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui

import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag

interface FragmentedSettings {
  @Tag("option")
  class Option() : BaseState() {
    constructor(name: String, visible: Boolean): this() {
      this.name = name
      this.visible = visible
    }

    @get:Attribute("name")
    var name by string()

    @get:Attribute("visible")
    var visible by property(true)
  }

  var selectedOptions: MutableList<Option>
}