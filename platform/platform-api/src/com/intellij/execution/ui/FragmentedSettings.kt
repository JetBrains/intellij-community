// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui

import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag

interface FragmentedSettings {
  @Tag("option")
  class Option() {
    constructor(name: String, visible: Boolean): this() {
      this.name = name
      this.visible = visible
    }

    @get:Attribute("name")
    var name: String = ""

    @get:Attribute("visible")
    var visible: Boolean = true
  }

  var selectedOptions: MutableList<Option>
}