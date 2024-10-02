// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SEHeaderActionListener {
    fun performed(event: SearchEverywhereActionEvent)

    class SearchEverywhereActionEvent(val actionID: String)

    companion object {
      @Topic.AppLevel
      val SE_HEADER_ACTION_TOPIC = Topic(SEHeaderActionListener::class.java)
    }
}