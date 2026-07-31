// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Deprecated("The old Search Everywhere is being sunset in favor of the new (Split) Search Everywhere " +
            "(com.intellij.platform.searchEverywhere).")
interface SETabSwitcherListener {
  fun tabSwitched(event: SETabSwitchedEvent)

  class SETabSwitchedEvent(val newTab: SearchEverywhereHeader.SETab)

  companion object {
    @Topic.AppLevel
    val SE_TAB_TOPIC = Topic(SETabSwitcherListener::class.java)
  }
}