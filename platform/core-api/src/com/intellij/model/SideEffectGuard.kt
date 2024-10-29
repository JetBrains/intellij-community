// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model

import com.intellij.concurrency.IntelliJContextElement
import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.installThreadContext
import com.intellij.openapi.diagnostic.ControlFlowException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.coroutines.CoroutineContext


interface SideEffectGuard {
  companion object {
    private val NO_EFFECTS: EnumSet<EffectType> = EnumSet.noneOf(EffectType::class.java)
    
    @JvmStatic
    fun checkSideEffectAllowed(effectType: EffectType) {
      if (!isAllowed(effectType)) {
        throw SideEffectNotAllowedException(effectType)
      }
    }
    
    @JvmStatic
    fun <T> computeWithoutSideEffects(action: () -> T): T {
      return computeWithAllowedSideEffectsBlocking(NO_EFFECTS, action)
    }
    
//    suspend fun <T> computeWithoutSideEffects(action: suspend CoroutineScope.() -> T): T {
//      return computeWithAllowedSideEffects(NO_EFFECTS, action)
//    }

    suspend fun <T> computeWithAllowedSideEffects(effects: EnumSet<EffectType>, action: suspend CoroutineScope.() -> T): T {
      return withContext(AllowedSideEffectsElement(effects), action)
    }

    @JvmStatic
    fun <T> computeWithAllowedSideEffectsBlocking(effects: EnumSet<EffectType>, action: () -> T): T {
      val context = currentThreadContext()
      return installThreadContext(context + AllowedSideEffectsElement(effects), replace = true).use {
        action()
      }
    }

    private fun isAllowed(effectType: EffectType): Boolean {
      return currentThreadContext()[AllowedSideEffectsElement]?.sideEffects?.contains(effectType) ?: true
    }
  }

  class SideEffectNotAllowedException(effectType: EffectType) : IllegalStateException("Side effect not allowed: " + effectType.name),
                                                                ControlFlowException

  enum class EffectType {
    /**
     * Change project model
     */
    PROJECT_MODEL,

    /**
     * Change settings
     */
    SETTINGS,

    /**
     * Execute external process
     */
    EXEC,

    /**
     * Spawn an action in UI thread
     */
    INVOKE_LATER,
  }
}

internal class AllowedSideEffectsElement(val sideEffects: EnumSet<SideEffectGuard.EffectType>) : CoroutineContext.Element, IntelliJContextElement {
  companion object : CoroutineContext.Key<AllowedSideEffectsElement>

  override fun produceChildElement(parentContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this

  override val key: CoroutineContext.Key<*> = AllowedSideEffectsElement
}


