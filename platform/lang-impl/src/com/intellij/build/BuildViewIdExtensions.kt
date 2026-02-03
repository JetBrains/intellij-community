// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build

import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun BuildViewId.findValue(): BuildTreeViewModel? {
  return findValueById(this, type = AiAssistantChatValueIdType)
}

@ApiStatus.Internal
fun BuildTreeViewModel.storeGlobally(coroutineScope: CoroutineScope): BuildViewId {
  return storeValueGlobally(coroutineScope, this, type = AiAssistantChatValueIdType)
}

@ApiStatus.Internal
private object AiAssistantChatValueIdType : BackendValueIdType<BuildViewId, BuildTreeViewModel>(::BuildViewId)