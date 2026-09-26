// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative

import com.intellij.openapi.extensions.RequiredElement
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute

class InlayActionHandlerBean: BaseKeyedLazyInstance<InlayActionHandler>() {
  @Attribute
  @RequiredElement
  var implementationClass: String = ""

  @Attribute
  @RequiredElement
  var handlerId: String = ""
  override fun getImplementationClassName(): String {
    return implementationClass
  }
}