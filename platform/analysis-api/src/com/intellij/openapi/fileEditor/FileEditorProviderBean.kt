// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.openapi.extensions.RequiredElement
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class FileEditorProviderBean {
  @Attribute("fileType")
  @JvmField
  var fileType: String? = null

  @Attribute("implementation")
  @RequiredElement
  var implementation: String? = null
}