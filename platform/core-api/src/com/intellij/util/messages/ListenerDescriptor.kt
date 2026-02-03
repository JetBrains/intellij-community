// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages

import com.intellij.openapi.extensions.ExtensionDescriptor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ListenerDescriptor(
  @JvmField val os: ExtensionDescriptor.Os?,
  @JvmField val listenerClassName: String,
  @JvmField val topicClassName: String,
  @JvmField val activeInTestMode: Boolean,
  @JvmField val activeInHeadlessMode: Boolean
)
