// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.internal

import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.instanceContainer.InstanceContainer
import com.intellij.platform.instanceContainer.InstanceNotRegisteredException
import com.intellij.util.ArrayUtil
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle

class InstanceContainerImpl(
  private val scopeHolder: ScopeHolder,
  private val containerName: String,
  private val dynamicInstanceSupport: DynamicInstanceSupport?,
  ordered: Boolean,
) : InstanceContainer, InstanceContainerInternal, MutableInstanceContainer {

  override fun toString(): String {
    val state = _state
    return if (state is InstanceContainerState) {
      "Container $containerName { registered: ${state.holders.size} }"
    }
    else {
      "Container $containerName (disposed)"
    }
  }

  /**
   * InstanceContainerState | Throwable
   */
  private var _state: Any = InstanceContainerState(
    holders = if (ordered) persistentMapOf() else persistentHashMapOf(),
  )

  private fun state(): InstanceContainerState {
    return state(stateHandle.getVolatile(this))
  }

  private inline fun updateState(update: (InstanceContainerState) -> InstanceContainerState) {
    var state = state()
    while (true) {
      val updatedState = update(state)
      val witness = stateHandle.compareAndExchange(this, state, updatedState)
      if (witness === state) {
        return
      }
      state = state(witness)
    }
  }

  private fun state(state: Any): InstanceContainerState {
    if (state is InstanceContainerState) {
      return state
    }
    else {
      throw ContainerDisposedException(containerName, state as DisposalTrace)
    }
  }

  override suspend fun <T : Any> instance(keyClass: Class<T>): T {
    val holder = getInstanceHolder(keyClass, registerDynamic = true)
                 ?: throw InstanceNotRegisteredException(keyClass.name)
    @Suppress("UNCHECKED_CAST")
    return holder.getInstance(keyClass) as T
  }

  override suspend fun <T : Any> requestedInstance(keyClass: Class<T>): T? {
    val holder = getInstanceHolder(keyClass)
                 ?: throw InstanceNotRegisteredException(keyClass.name)
    @Suppress("UNCHECKED_CAST")
    return holder.getInstanceIfRequested() as T?
  }

  override fun instanceHoldersAndKeys(): Map<String, InstanceHolder> {
    return state().holders
  }

  override fun instanceHolders(): Collection<InstanceHolder> {
    return instanceHoldersAndKeys().values
  }

  override fun getInstanceHolder(keyClassName: String): InstanceHolder? {
    return state().getByName(keyClassName)
  }

  override fun getInstanceHolder(keyClass: Class<*>): InstanceHolder? {
    return state().getByClass(keyClass)
  }

  override fun getInstanceHolder(keyClass: Class<*>, registerDynamic: Boolean): InstanceHolder? {
    if (!registerDynamic || dynamicInstanceSupport == null) {
      return getInstanceHolder(keyClass)
    }
    lateinit var holder: InstanceHolder
    updateState { state: InstanceContainerState ->
      state.getByClass(keyClass)?.let {
        return it
      }
      val dynamicInstanceInitializer = dynamicInstanceSupport.dynamicInstanceInitializer(instanceClass = keyClass) ?: return null
      val parentScope = scopeHolder.intersectScope(dynamicInstanceInitializer.registrationScope)
      val initializer = dynamicInstanceInitializer.initializer
      holder = DynamicInstanceHolder(parentScope, initializer)
      state.replaceByClass(keyClass, holder)
    }
    // the following can only execute in case `holder` was initialized and committed into `state`
    dynamicInstanceSupport.dynamicInstanceRegistered(holder)
    return holder
  }

  override fun startRegistration(registrationScope: CoroutineScope?): InstanceRegistrar {
    val scopeName = registrationScope?.let {
      requireNotNull(it.coroutineContext[CoroutineName]) {
        "Registration scope must contain CoroutineName in its context"
      }.name
    }
    val debugString = if (scopeName == null) containerName else "($containerName x $scopeName)"
    LOG.trace { "$debugString : registration start" }
    return InstanceRegistrarImpl(debugString, state().holders) { actions ->
      register(debugString, registrationScope, actions)
    }
  }

  private fun register(
    debugString: String,
    registrationScope: CoroutineScope?,
    actions: Map<String, RegistrationAction>,
  ): UnregisterHandle? {
    if (actions.isEmpty()) {
      LOG.trace { "$debugString : registration empty" }
      return null
    }
    LOG.trace { "$debugString : registration" }
    val parentScope = scopeHolder.intersectScope(registrationScope)
    return register(parentScope = parentScope, actions).also {
      LOG.trace { "$debugString : registration completed" }
    }
  }

  inner class UnregisterHandleImpl(
    restorationMap: Map<String, InstanceHolder?>,
    returnMap: Map<String, InstanceHolder>,
    keysToRemove: Set<String>,
    hasPreviousHolders: Boolean,
  ) : UnregisterHandle {
    // if restorationMap has null values only, it has no sense to store it because the keys can be obtained from keysToRemove
    val keysToUnregister: Array<String>? = if (hasPreviousHolders) ArrayUtil.toStringArray(restorationMap.keys) else null
    // store map as a pair of arrays (keysToUnregister/holdersToUnregister) with keys and values to conserve memory
    val holdersToUnregister: Array<InstanceHolder?>? = if (hasPreviousHolders) restorationMap.values.toTypedArray() else null
    // typically, returnMap keys are the same as restorationMap, no need to store them
    val keysToReturn: Array<String> = ArrayUtil.toStringArray(returnMap.keys).let { if (keysToUnregister != null && it.contentEquals(keysToUnregister)) keysToUnregister else it }
    val holdersToReturn: Array<InstanceHolder> = returnMap.values.toTypedArray()
    // no need to store keysToRemove if we store restorationMap, the keys can be restored from there
    val keysToRemove: Array<String>? = if (hasPreviousHolders) null else ArrayUtil.toStringArray(keysToRemove)
    override fun unregister(): Map<String, InstanceHolder> {
      unregister(keysToUnregister ?: keysToReturn, holdersToUnregister ?: arrayOfNulls<InstanceHolder?>(keysToReturn.size), keysToRemove)
      return keysToReturn.zip(holdersToReturn).toMap()
    }
  }

  private fun register(parentScope: CoroutineScope, actions: Map<String, RegistrationAction>): UnregisterHandle {
    val preparedHolders = prepareHolders(parentScope, actions)
    val (holders, _, keysToRemove) = preparedHolders
    lateinit var handle: UnregisterHandle
    updateState { state ->
      // key -> holder to add/replace; key -> null to remove
      val restorationMap = LinkedHashMap<String, InstanceHolder?>()
      val builder = state.holders.builder()
      var hasPreviousHolders = false
      for (key in keysToRemove) {
        val previous = builder.remove(key)
        checkExistingRegistration(state.holders, preparedHolders, keyClassName = key, existing = previous, new = null)
        restorationMap[key] = previous
        hasPreviousHolders = hasPreviousHolders || (previous != null)
      }
      for ((key, value) in holders) {
        val previous = builder.put(key, value)
        checkExistingRegistration(state.holders, preparedHolders, keyClassName = key, existing = previous, new = value)
        restorationMap[key] = previous
        hasPreviousHolders = hasPreviousHolders || (previous != null)
      }
      handle = UnregisterHandleImpl(restorationMap, holders, keysToRemove, hasPreviousHolders)
      InstanceContainerState(builder.build())
    }
    return handle
  }

  private fun unregister(keys: Array<String>, instanceHolders: Array<InstanceHolder?>, keysToRemove: Array<String>?) {
    // TODO consider asserting that registered instances were not replaced in the middle to avoid situations like this:
    //  ```
    //  val restorationOne = register(one)
    //  val restorationTwo = register(two)
    //  restorationOne.close()
    //  restorationTwo.close()
    //  ```
    //  restorationTwo should be finished before restorationOne,
    //  or, in other words, two should be fully nested into one
    updateState { state: InstanceContainerState ->
      val builder = state.holders.builder()
      for(i in 0 until keys.size) {
        val key = keys[i]
        val value = instanceHolders[i]
        if (value == null) {
          builder.remove(key)
        }
        else {
          builder[key] = value
        }
      }
      keysToRemove?.forEach { builder.remove(it) }
      InstanceContainerState(builder.build())
    }
  }

  override fun <T : Any> registerInstance(keyClass: Class<T>, instance: T) {
    val keyClassName = keyClass.name
    val holder = InitializedInstanceHolder(instance)
    updateState { state: InstanceContainerState ->
      val existingHolder = state.getByName(keyClassName)
      if (existingHolder != null) {
        throw InstanceAlreadyRegisteredException(
          keyClassName = keyClassName,
          existingInstanceClassName = existingHolder.instanceClassName(),
          newInstanceClassName = holder.instanceClassName(),
        )
      }
      state.replaceByClass(keyClass, holder)
    }
  }

  override fun <T : Any> replaceInstance(keyClass: Class<T>, instance: T): UnregisterHandle {
    val keyClassName = keyClass.name
    val holder = InitializedInstanceHolder(instance)
    lateinit var handle: UnregisterHandle
    updateState { state: InstanceContainerState ->
      val existingHolder = state.getByName(keyClassName)
      handle = UnregisterHandle {
        undoReplaceInstance(keyClassName = keyClassName, instance = instance, previousHolder = existingHolder)
        return@UnregisterHandle mapOf(keyClassName to holder)
      }
      state.replaceByClass(keyClass, holder)
    }
    return handle
  }

  private fun undoReplaceInstance(keyClassName: String, instance: Any, previousHolder: InstanceHolder?) {
    updateState { state: InstanceContainerState ->
      check(state.getByName(keyClassName).let {
        it is InitializedInstanceHolder && it.instance === instance
      })
      state.replaceByName(keyClassName, previousHolder)
    }
  }

  override fun <T : Any> replaceInstanceForever(keyClass: Class<T>, instance: T): InstanceHolder? {
    val keyClassName = keyClass.name
    val holder = InitializedInstanceHolder(instance)
    var existingHolder: InstanceHolder? = null
    updateState { state ->
      existingHolder = state.getByName(keyClassName)
      state.replaceByClass(keyClass, holder)
    }
    return existingHolder
  }

  override fun unregister(keyClassName: String, unregisterDynamic: Boolean): InstanceHolder? {
    lateinit var existingHolder: InstanceHolder
    updateState { state ->
      existingHolder = state.getByName(keyClassName)?.takeUnless {
        it is DynamicInstanceHolder && !unregisterDynamic
      } ?: return null
      state.replaceByName(keyClassName, null)
    }
    return existingHolder
  }

  fun cleanCache() {
    updateState { state: InstanceContainerState ->
      InstanceContainerState(state.holders)
    }
  }

  override fun dispose() {
    stateHandle.setVolatile(this, DisposalTrace())
  }

  private companion object {
    @JvmField
    val stateHandle: VarHandle = MethodHandles
      .privateLookupIn(InstanceContainerImpl::class.java, MethodHandles.lookup())
      .findVarHandle(InstanceContainerImpl::class.java, "_state", Any::class.java)
  }
}
