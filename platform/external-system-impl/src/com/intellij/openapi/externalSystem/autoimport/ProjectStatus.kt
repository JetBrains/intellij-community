// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport


import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemModificationType.*
import com.intellij.openapi.externalSystem.autoimport.ProjectStatus.ProjectEvent.*
import com.intellij.openapi.externalSystem.autoimport.ProjectStatus.ProjectState.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

class ProjectStatus(private val debugName: String? = null) {
  private var state = AtomicReference(Synchronized(-1) as ProjectState)

  fun isDirty() = state.get() is Dirty

  fun isUpToDate() = when (state.get()) {
    is Modified, is Dirty, is Broken -> false
    is Synchronized, is Reverted -> true
  }

  fun getModificationType() = when (val state = state.get()) {
    is Dirty -> state.type
    is Modified -> state.type
    else -> UNKNOWN
  }

  fun markBroken(stamp: Long): ProjectState {
    return update(Break(stamp))
  }

  fun markDirty(stamp: Long, type: ExternalSystemModificationType = INTERNAL): ProjectState {
    return update(Invalidate(stamp, type))
  }

  fun markModified(stamp: Long, type: ExternalSystemModificationType = INTERNAL): ProjectState {
    return update(Modify(stamp, type))
  }

  fun markReverted(stamp: Long): ProjectState {
    return update(Revert(stamp))
  }

  fun markSynchronized(stamp: Long): ProjectState {
    return update(Synchronize(stamp))
  }

  fun update(event: ProjectEvent): ProjectState {
    val oldState = AtomicReference<ProjectState>()
    val newState = state.updateAndGet { currentState ->
      oldState.set(currentState)
      when (currentState) {
        is Synchronized -> when (event) {
          is Synchronize -> event.withFuture(currentState, ::Synchronized)
          is Invalidate -> event.ifFuture(currentState) { Dirty(it, event.type) }
          is Modify -> event.ifFuture(currentState) { Modified(it, event.type) }
          is Revert -> event.ifFuture(currentState, ::Reverted)
          is Break -> event.ifFuture(currentState, ::Broken)
        }
        is Dirty -> when (event) {
          is Synchronize -> event.ifFuture(currentState, ::Synchronized)
          is Invalidate -> event.withFuture(currentState) { Dirty(it, merge(currentState.type, event.type)) }
          is Modify -> event.withFuture(currentState) { Dirty(it, merge(currentState.type, event.type)) }
          is Revert -> event.withFuture(currentState) { Dirty(it, currentState.type) }
          is Break -> event.withFuture(currentState) { Dirty(it, currentState.type) }
        }
        is Modified -> when (event) {
          is Synchronize -> event.ifFuture(currentState, ::Synchronized)
          is Invalidate -> event.withFuture(currentState) { Dirty(it, merge(currentState.type, event.type)) }
          is Modify -> event.withFuture(currentState) { Modified(it, merge(currentState.type, event.type)) }
          is Revert -> event.ifFuture(currentState, ::Reverted)
          is Break -> event.withFuture(currentState) { Dirty(it, currentState.type) }
        }
        is Reverted -> when (event) {
          is Synchronize -> event.ifFuture(currentState, ::Synchronized)
          is Invalidate -> event.withFuture(currentState) { Dirty(it, event.type) }
          is Modify -> event.ifFuture(currentState) { Modified(it, event.type) }
          is Revert -> event.withFuture(currentState, ::Reverted)
          is Break -> event.ifFuture(currentState, ::Broken)
        }
        is Broken -> when (event) {
          is Synchronize -> event.ifFuture(currentState, ::Synchronized)
          is Invalidate -> event.withFuture(currentState) { Dirty(it, event.type) }
          is Modify -> event.withFuture(currentState) { Dirty(it, event.type) }
          is Revert -> event.withFuture(currentState, ::Broken)
          is Break -> event.withFuture(currentState, ::Broken)
        }
      }
    }
    debug(newState, oldState.get(), event)
    return newState
  }

  private fun debug(newState: ProjectState, oldState: ProjectState, event: ProjectEvent) {
    if (LOG.isDebugEnabled) {
      val debugPrefix = if (debugName == null) "" else "$debugName: "
      LOG.debug("${debugPrefix}$oldState -> $newState by $event")
    }
  }

  private fun ProjectEvent.withFuture(state: ProjectState, action: (Long) -> ProjectState): ProjectState {
    return action(max(stamp, state.stamp))
  }

  private fun ProjectEvent.ifFuture(state: ProjectState, action: (Long) -> ProjectState): ProjectState {
    return if (stamp > state.stamp) action(stamp) else state
  }

  companion object {
    private val LOG = Logger.getInstance("#com.intellij.openapi.externalSystem.autoimport")

    fun merge(
      type1: ExternalSystemModificationType,
      type2: ExternalSystemModificationType
    ): ExternalSystemModificationType {
      return when (type1) {
        INTERNAL -> INTERNAL
        EXTERNAL -> when (type2) {
          INTERNAL -> INTERNAL
          EXTERNAL -> EXTERNAL
          HIDDEN -> EXTERNAL
          UNKNOWN -> EXTERNAL
        }
        HIDDEN -> when (type2) {
          INTERNAL -> INTERNAL
          EXTERNAL -> EXTERNAL
          HIDDEN -> HIDDEN
          UNKNOWN -> HIDDEN
        }
        UNKNOWN -> type2
      }
    }
  }

  sealed class ProjectEvent {
    abstract val stamp: Long

    data class Synchronize(override val stamp: Long) : ProjectEvent()
    data class Invalidate(override val stamp: Long, val type: ExternalSystemModificationType) : ProjectEvent()
    data class Modify(override val stamp: Long, val type: ExternalSystemModificationType) : ProjectEvent()
    data class Revert(override val stamp: Long) : ProjectEvent()
    data class Break(override val stamp: Long) : ProjectEvent()

    companion object {
      fun externalModify(stamp: Long) = Modify(stamp, EXTERNAL)
      fun externalInvalidate(stamp: Long) = Invalidate(stamp, EXTERNAL)
    }
  }

  sealed class ProjectState {
    abstract val stamp: Long

    data class Synchronized(override val stamp: Long) : ProjectState()
    data class Dirty(override val stamp: Long, val type: ExternalSystemModificationType) : ProjectState()
    data class Modified(override val stamp: Long, val type: ExternalSystemModificationType) : ProjectState()
    data class Reverted(override val stamp: Long) : ProjectState()
    data class Broken(override val stamp: Long) : ProjectState()
  }
}
