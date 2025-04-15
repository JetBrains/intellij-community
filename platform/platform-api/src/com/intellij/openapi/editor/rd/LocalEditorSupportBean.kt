// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.rd

import com.intellij.openapi.extensions.RequiredElement
import com.intellij.util.KeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus.Internal

@Internal // lift this once we are ready to split external plugins
class LocalEditorSupportBean internal constructor() : KeyedLazyInstance<Unit> {

  @Attribute("filetype")
  @RequiredElement
  var filetype: String? = null

  override fun getInstance() {}

  override fun getKey(): String {
    return filetype!!
  }
}
