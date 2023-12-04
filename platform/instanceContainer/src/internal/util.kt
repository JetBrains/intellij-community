// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.internal

import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.instanceContainer.InstanceNotRegisteredException
import kotlinx.collections.immutable.PersistentMap
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.cancellation.CancellationException

internal val LOG: Logger = Logger.getInstance("#com.intellij.platform.instanceContainer")

internal typealias InstanceHolders = PersistentMap<String, InstanceHolder>

/**
 * @return `true` if [this] holder was statically registered,
 * i.e. registered via [MutableInstanceContainer] functions,
 * or `false` if it was registered via [DynamicInstanceSupport]
 */
fun InstanceHolder.isStatic(): Boolean {
  // this !is DynamicInstanceHolder
  return this is StaticInstanceHolder || this is InitializedInstanceHolder
}

/**
 * @return all instances which completed initialization
 * @see InstanceHolder.tryGetInstance
 */
fun InstanceContainerInternal.initializedInstances(): Sequence<Any> {
  val holders = instanceHolders()
  return sequence {
    for (holder in holders) {
      try {
        val instance = holder.tryGetInstance() ?: continue
        yield(instance)
      }
      catch (t: Throwable) {
        LOG.warn(t)
      }
    }
  }
}

suspend fun InstanceContainerInternal.preloadAllInstances() {
  val holders = instanceHolders()
  for (holder in holders) {
    try {
      holder.getInstanceInCallerContext(keyClass = null)
    }
    catch (ce: CancellationException) {
      throw ce
    }
    catch (t: Throwable) {
      LOG.error("Cannot create ${holder.instanceClassName()}", t)
    }
  }
}

internal fun prepareHolders(parentScope: CoroutineScope, actions: Map<String, RegistrationAction>): PreparedHolders {
  val holders = LinkedHashMap<String, InstanceHolder>()
  val keysToAdd = HashSet<String>()
  val keysToRemove = HashSet<String>()
  for ((keyClassName, action) in actions) {
    when (action) {
      is RegistrationAction.Register -> {
        keysToAdd.add(keyClassName)
        holders[keyClassName] = StaticInstanceHolder(parentScope, action.initializer)
      }
      is RegistrationAction.Override -> {
        holders[keyClassName] = StaticInstanceHolder(parentScope, action.initializer)
      }
      RegistrationAction.Remove -> {
        keysToRemove.add(keyClassName)
      }
    }
  }
  return PreparedHolders(holders, keysToAdd, keysToRemove)
}

internal data class PreparedHolders(
  /**
   * Map of key -> holder to add or replace.
   */
  val holders: Map<String, InstanceHolder>,
  /**
   * Set of keys which should not yet exist; strictly subset of keys in [holders].
   */
  val keysToAdd: Set<String>,
  /**
   * Set of keys which will not exist after registration.
   */
  val keysToRemove: Set<String>,
)

internal fun checkExistingRegistration(
  state: InstanceHolders,
  preparedHolders: PreparedHolders,
  keyClassName: String,
  existing: InstanceHolder?,
  new: InstanceHolder?,
) {
  val override = keyClassName !in preparedHolders.keysToAdd
  val t = if (override && existing == null) {
    InstanceNotRegisteredException("$keyClassName -> ${new?.instanceClassName() ?: "<removed>"}")
  }
  else if (!override && existing != null) {
    InstanceAlreadyRegisteredException("$keyClassName : ${existing.instanceClassName()} -> ${new?.instanceClassName() ?: "<removed>"}")
  }
  else {
    null
  }
  if (t != null) {
    LOG.debug(
      """  
         --- State dump --- 
        $state
         --- To apply ---
        ${preparedHolders.holders}
         --- To register ---
        ${preparedHolders.keysToAdd}
         --- To remove --- 
        ${preparedHolders.keysToRemove}
         --- End state dump ---
      """.trimIndent(),
      t,
    )
    throw t
  }
}
