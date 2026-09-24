// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.fs.EelFileInfo
import com.intellij.platform.eel.fs.EelFileSystemApi.SymlinkPolicy
import com.intellij.platform.eel.fs.EelPosixFileInfo
import com.intellij.platform.eel.path.EelPath
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.currentCoroutineContext
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * A cached entry carries the link target of a symlink, but the cache does not follow the link.
 * A lookup with [SymlinkPolicy.RESOLVE_AND_FOLLOW] that touches a symlink is a miss.
 */
@ApiStatus.Internal
abstract class PrefetchDataElement :
  AbstractCoroutineContextElement(Key),
  ThreadContextElement<PrefetchDataElement?> {

  /** The cached entries of a directory, or null when the directory is not cached. */
  protected abstract fun cachedChildren(path: EelPath): Map<String, EelFileInfo>?

  /** The child names do not depend on a symlink policy. */
  fun getChildNames(path: EelPath): Set<String>? = cachedChildren(path)?.keys

  fun getChildren(path: EelPath, symlinkPolicy: SymlinkPolicy): Map<String, EelFileInfo>? {
    val children = cachedChildren(path) ?: return null
    if (symlinkPolicy == SymlinkPolicy.RESOLVE_AND_FOLLOW && children.values.any { it.type is EelPosixFileInfo.Type.Symlink }) return null
    return children
  }

  sealed class StatLookup {
    class Hit(val info: EelFileInfo) : StatLookup()
    object Absent : StatLookup()  // parent cached, child missing = known DoesNotExist
    object Miss : StatLookup()    // parent not cached, or a symlink to follow = need gRPC
  }

  fun lookupStat(path: EelPath, symlinkPolicy: SymlinkPolicy): StatLookup {
    val parent = path.parent ?: return StatLookup.Miss
    val children = cachedChildren(parent) ?: return StatLookup.Miss
    val info = children[path.fileName] ?: return StatLookup.Absent
    if (symlinkPolicy == SymlinkPolicy.RESOLVE_AND_FOLLOW && info.type is EelPosixFileInfo.Type.Symlink) return StatLookup.Miss
    return StatLookup.Hit(info)
  }

  abstract val size: Int

  companion object Key : CoroutineContext.Key<PrefetchDataElement> {
    val threadLocal: ThreadLocal<PrefetchDataElement?> = ThreadLocal<PrefetchDataElement?>()

    suspend fun current(): PrefetchDataElement? {
      return currentCoroutineContext()[PrefetchDataElement] ?: threadLocal.get()
    }
  }
}
