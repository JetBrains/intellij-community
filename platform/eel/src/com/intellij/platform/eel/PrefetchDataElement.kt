// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.fs.EelFileInfo
import com.intellij.platform.eel.fs.EelFileSystemApi.SymlinkPolicy
import com.intellij.platform.eel.fs.EelPosixFileInfo
import com.intellij.platform.eel.fs.PrefetchedStat
import com.intellij.platform.eel.path.EelPath
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.currentCoroutineContext
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * A cached entry carries the link target of a symlink, but the cache does not follow the link on the server.
 * A lookup follows a symlink inside the cache, component by component, the way the kernel does.
 * A lookup that needs an entry the cache does not hold is a miss.
 *
 * The cache trusts the components above the shallowest cached directory as plain directories,
 * the same way it trusts the parent of a cached entry.
 *
 * The cache holds two kinds of entries. A directory listing is complete: a name that is not in it does not exist.
 * A stat cached on its own answers only for its path and says nothing about a sibling.
 */
@ApiStatus.Internal
abstract class PrefetchDataElement :
  AbstractCoroutineContextElement(Key),
  ThreadContextElement<PrefetchDataElement?> {

  /** The cached entries of a directory, or null when the directory is not cached. */
  protected abstract fun cachedChildren(path: EelPath): Map<String, EelFileInfo>?

  /** The stat cached for [path] on its own, or null when it is not cached. */
  protected abstract fun cachedStat(path: EelPath): PrefetchedStat?

  /** The child names do not depend on a symlink policy. */
  fun getChildNames(path: EelPath): Set<String>? {
    val directory = resolve(path, followLast = true) ?: return null
    return cachedChildren(directory.path)?.keys
  }

  fun getChildren(path: EelPath, symlinkPolicy: SymlinkPolicy): Map<String, EelFileInfo>? {
    val directory = resolve(path, followLast = true) ?: return null
    val children = cachedChildren(directory.path) ?: return null
    if (symlinkPolicy != SymlinkPolicy.RESOLVE_AND_FOLLOW || children.values.none { it.type is EelPosixFileInfo.Type.Symlink }) return children
    return children.mapValues { (name, info) ->
      if (info.type !is EelPosixFileInfo.Type.Symlink) info
      else resolve(directory.path.getChild(name), followLast = true)?.info ?: return null
    }
  }

  sealed class StatLookup {
    class Hit(val info: EelFileInfo) : StatLookup()
    object Absent : StatLookup()  // parent cached, child missing = known DoesNotExist
    object Miss : StatLookup()    // an entry on the way is not cached = need gRPC
  }

  fun lookupStat(path: EelPath, symlinkPolicy: SymlinkPolicy): StatLookup {
    // A stat cached on its own answers for its literal path, the way the server statted it.
    // It is final: a listing below either agrees with it or does not hold the path at all.
    when (val stat = cachedStat(path)) {
      is PrefetchedStat.Present -> return when (symlinkPolicy) {
        SymlinkPolicy.DO_NOT_RESOLVE, SymlinkPolicy.JUST_RESOLVE -> StatLookup.Hit(stat.info)
        SymlinkPolicy.RESOLVE_AND_FOLLOW -> stat.target?.let { StatLookup.Hit(it) } ?: StatLookup.Miss
      }
      PrefetchedStat.Absent -> return StatLookup.Absent
      null -> Unit
    }
    val resolved = resolve(path, followLast = symlinkPolicy == SymlinkPolicy.RESOLVE_AND_FOLLOW)
    if (resolved != null) return resolved.info?.let { StatLookup.Hit(it) } ?: StatLookup.Miss
    // A missing child of a cached directory is a known absence only when no symlink took part in the lookup.
    val parent = path.parent ?: return StatLookup.Miss
    val parentResolved = resolve(parent, followLast = true) ?: return StatLookup.Miss
    val siblings = cachedChildren(parentResolved.path) ?: return StatLookup.Miss
    return if (parentResolved.hops == 0 && path.fileName !in siblings) StatLookup.Absent else StatLookup.Miss
  }

  /** The real path of an existing [path], or null when the cache cannot prove it. */
  fun canonicalize(path: EelPath): EelPath? {
    val resolved = resolve(path, followLast = true) ?: return null
    return if (resolved.info != null) resolved.path else null
  }

  /** [info] is null for the shallowest cached directory, because its own entry is not cached. */
  private class Resolved(val path: EelPath, val info: EelFileInfo?, val hops: Int)

  /**
   * Walks [path] inside the cache. A symlink in the middle is always followed, the last one only when [followLast] is set.
   * `..` applies to the directory reached so far, as in the kernel, not to the text of the path.
   */
  private fun resolve(path: EelPath, followLast: Boolean): Resolved? {
    var current = shallowestCachedDirectory(path) ?: return null
    var info: EelFileInfo? = null
    var hops = 0
    val pending = ArrayDeque(path.parts.drop(current.nameCount))
    while (pending.isNotEmpty()) {
      val name = pending.removeFirst()
      when (name) {
        "", "." -> continue
        ".." -> {
          current = current.parent ?: return null
          info = null
          continue
        }
      }
      val entry = cachedChildren(current)?.get(name) ?: return null
      val type = entry.type
      if (type is EelPosixFileInfo.Type.Symlink && (followLast || pending.isNotEmpty())) {
        if (++hops > MAX_SYMLINK_HOPS) return null
        when (type) {
          is EelPosixFileInfo.Type.Symlink.Resolved.Absolute -> {
            val target = type.result
            current = shallowestCachedDirectory(target) ?: return null
            pending.addAll(0, target.parts.drop(current.nameCount))
          }
          is EelPosixFileInfo.Type.Symlink.Resolved.Relative -> pending.addAll(0, type.result.split('/'))
          is EelPosixFileInfo.Type.Symlink.Unresolved -> return null
        }
        info = null
        continue
      }
      current = current.getChild(name)
      info = entry
    }
    return Resolved(current, info, hops)
  }

  private fun shallowestCachedDirectory(path: EelPath): EelPath? {
    var result: EelPath? = null
    var current: EelPath? = path
    while (current != null) {
      if (cachedChildren(current) != null) result = current
      current = current.parent
    }
    return result
  }

  abstract val size: Int

  final override fun updateThreadContext(context: CoroutineContext): PrefetchDataElement? {
    val previous = threadLocal.get()
    threadLocal.set(this)
    return previous
  }

  final override fun restoreThreadContext(context: CoroutineContext, oldState: PrefetchDataElement?) {
    threadLocal.set(oldState)
  }

  companion object Key : CoroutineContext.Key<PrefetchDataElement> {
    /** The Linux limit for symlinks in one path resolution. */
    private const val MAX_SYMLINK_HOPS = 40

    val threadLocal: ThreadLocal<PrefetchDataElement?> = ThreadLocal<PrefetchDataElement?>()

    suspend fun current(): PrefetchDataElement? {
      return currentCoroutineContext()[PrefetchDataElement] ?: threadLocal.get()
    }
  }
}
