// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.impl

import com.intellij.util.messages.Topic

interface DocumentationPopupListener {

  fun popupShown()

  fun contentsScrolled()

  companion object {
    @Topic.ProjectLevel
    @JvmField
    val TOPIC = Topic("Documentation popup events", DocumentationPopupListener::class.java)
  }

}