// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.fs.EelSearchApi
import com.intellij.platform.eel.fs.EelSearchEvent
import com.intellij.platform.eel.fs.EelSearchOptions
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentLinkedQueue

internal class FakeEelDescriptor(override val name: String) : EelDescriptor {
  override val osFamily: EelOsFamily = EelOsFamily.Posix
}

internal class FakeEelSearchApi(private val events: Flow<EelSearchEvent>) : EelSearchApi {
  val requests = ConcurrentLinkedQueue<EelSearchOptions>()

  override suspend fun search(options: EelSearchOptions): Flow<EelSearchEvent> {
    requests.add(options)
    return events
  }
}
