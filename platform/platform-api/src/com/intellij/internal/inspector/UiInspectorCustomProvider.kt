// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector

import java.awt.Rectangle

interface UiInspectorCustomComponentProvider {
  companion object {
    const val KEY: String = "UiInspectorCustomComponentProvider"
  }

  fun getChildren(): List<UiInspectorCustomComponentChildProvider>
}

interface UiInspectorCustomComponentChildProvider : UiInspectorContextProvider {
  fun getTreeName(): String

  fun getChildren(): List<UiInspectorCustomComponentChildProvider>

  fun getObjectForProperties(): Any?

  fun getPropertiesMethodList(): List<String>

  fun getHighlightingBounds(): Rectangle?
}