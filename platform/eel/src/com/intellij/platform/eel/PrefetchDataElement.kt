// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.fs.EelFileInfo
import com.intellij.platform.eel.path.EelPath
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.currentCoroutineContext
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

@ApiStatus.Internal
abstract class PrefetchDataElement :
  AbstractCoroutineContextElement(Key),
  ThreadContextElement<PrefetchDataElement?> {

  abstract fun getChildren(path: EelPath): Map<String, EelFileInfo>?

  sealed class StatLookup {
    class Hit(val info: EelFileInfo) : StatLookup()
    object Absent : StatLookup()  // parent cached, child missing = known DoesNotExist
    object Miss : StatLookup()    // parent not cached = need gRPC
  }

  abstract fun lookupStat(path: EelPath): StatLookup

  abstract val size: Int

  companion object Key : CoroutineContext.Key<PrefetchDataElement> {
    val threadLocal: ThreadLocal<PrefetchDataElement?> = ThreadLocal<PrefetchDataElement?>()

    suspend fun current(): PrefetchDataElement? {
      return currentCoroutineContext()[PrefetchDataElement] ?: threadLocal.get()
    }
  }
}
