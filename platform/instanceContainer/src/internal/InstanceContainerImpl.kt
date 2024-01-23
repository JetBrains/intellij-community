// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.internal

import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.instanceContainer.InstanceContainer
import com.intellij.platform.instanceContainer.InstanceNotRegisteredException
import kotlinx.collections.immutable.PersistentMap
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
    return if (state is PersistentMap<*, *>) {
      "Container $containerName { registered: ${state.size} }"
    }
    else {
      "Container $containerName (disposed)"
    }
  }

  /**
   * InstanceHolders | Throwable
   */
  private var _state: Any = if (ordered) persistentMapOf<String, InstanceHolder>() else persistentHashMapOf()

  private fun state(): InstanceHolders {
    return state(stateHandle.getVolatile(this))
  }

  private inline fun updateState(update: (state: InstanceHolders) -> InstanceHolders) {
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

  private fun state(state: Any): InstanceHolders {
    if (state is PersistentMap<*, *>) {
      @Suppress("UNCHECKED_CAST")
      return state as InstanceHolders
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
    return state()
  }

  override fun instanceHolders(): Collection<InstanceHolder> {
    return state().values
  }

  override fun getInstanceHolder(keyClassName: String): InstanceHolder? {
    return state()[keyClassName]
  }

  override fun getInstanceHolder(keyClass: Class<*>, registerDynamic: Boolean): InstanceHolder? {
    lateinit var holder: InstanceHolder
    updateState { state: InstanceHolders ->
      state[keyClass.name]?.let {
        return it
      }
      if (!registerDynamic || dynamicInstanceSupport == null) {
        return null
      }
      val dynamicInstanceInitializer = dynamicInstanceSupport.dynamicInstanceInitializer(instanceClass = keyClass)
      if (dynamicInstanceInitializer == null) {
        return null
      }
      val parentScope = scopeHolder.intersectScope(dynamicInstanceInitializer.registrationScope)
      val initializer = dynamicInstanceInitializer.initializer
      holder = DynamicInstanceHolder(parentScope, initializer)
      state.put(keyClass.name, holder)
    }
    // the following can only execute in case `holder` was initialized and committed into `state`
    dynamicInstanceSupport!!.dynamicInstanceRegistered(holder)
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
    val existingKeys = state().keys
    return InstanceRegistrarImpl(debugString, existingKeys) { actions ->
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

  private fun register(parentScope: CoroutineScope, actions: Map<String, RegistrationAction>): UnregisterHandle {
    val preparedHolders = prepareHolders(parentScope, actions)
    val (holders, _, keysToRemove) = preparedHolders
    lateinit var handle: UnregisterHandle
    updateState { state: InstanceHolders ->
      // key -> holder to add/replace; key -> null to remove
      val restorationMap = LinkedHashMap<String, InstanceHolder?>()
      val builder = state.builder()
      for (key in keysToRemove) {
        val previous = builder.remove(key)
        checkExistingRegistration(state, preparedHolders, keyClassName = key, existing = previous, new = null)
        restorationMap[key] = previous
      }
      for ((key, value) in holders) {
        val previous = builder.put(key, value)
        checkExistingRegistration(state, preparedHolders, keyClassName = key, existing = previous, new = value)
        restorationMap[key] = previous
      }
      handle = UnregisterHandle {
        unregister(restorationMap)
        return@UnregisterHandle holders
      }
      builder.build()
    }
    return handle
  }

  private fun unregister(restoration: Map<String, InstanceHolder?>) {
    // TODO consider asserting that registered instances were not replaced in the middle to avoid situations like this:
    //  ```
    //  val restorationOne = register(one)
    //  val restorationTwo = register(two)
    //  restorationOne.close()
    //  restorationTwo.close()
    //  ```
    //  restorationTwo should be finished before restorationOne,
    //  or, in other words, two should be fully nested into one
    updateState { state: InstanceHolders ->
      val builder = state.builder()
      for ((key, value) in restoration) {
        if (value == null) {
          builder.remove(key)
        }
        else {
          builder[key] = value
        }
      }
      builder.build()
    }
  }

  override fun <T : Any> registerInstance(keyClass: Class<T>, instance: T) {
    val keyClassName = keyClass.name
    val holder = InitializedInstanceHolder(instance)
    updateState { state: InstanceHolders ->
      val existingHolder = state[keyClassName]
      if (existingHolder != null) {
        throw InstanceAlreadyRegisteredException(keyClassName)
      }
      state.put(keyClassName, holder)
    }
  }

  override fun <T : Any> replaceInstance(keyClass: Class<T>, instance: T): UnregisterHandle {
    val keyClassName = keyClass.name
    val holder = InitializedInstanceHolder(instance)
    lateinit var handle: UnregisterHandle
    updateState { state: InstanceHolders ->
      val existingHolder = state[keyClassName]
      handle = UnregisterHandle {
        undoReplaceInstance(keyClassName, instance, previousHolder = existingHolder)
        return@UnregisterHandle mapOf(keyClassName to holder)
      }
      state.put(keyClassName, holder)
    }
    return handle
  }

  private fun undoReplaceInstance(keyClassName: String, instance: Any, previousHolder: InstanceHolder?) {
    updateState { state: InstanceHolders ->
      check(state[keyClassName].let {
        it is InitializedInstanceHolder && it.instance === instance
      })
      if (previousHolder == null) {
        state.remove(keyClassName)
      }
      else {
        state.put(keyClassName, previousHolder)
      }
    }
  }

  override fun <T : Any> replaceInstanceForever(keyClass: Class<T>, instance: T): InstanceHolder? {
    val keyClassName = keyClass.name
    val holder = InitializedInstanceHolder(instance)
    var existingHolder: InstanceHolder? = null
    updateState { state: InstanceHolders ->
      existingHolder = state[keyClassName]
      state.put(keyClassName, holder)
    }
    return existingHolder
  }

  override fun unregister(keyClassName: String, unregisterDynamic: Boolean): InstanceHolder? {
    lateinit var existingHolder: InstanceHolder
    updateState { state: InstanceHolders ->
      existingHolder = state[keyClassName]?.takeUnless {
        it is DynamicInstanceHolder && !unregisterDynamic
      } ?: return null
      state.remove(keyClassName)
    }
    return existingHolder
  }

  override fun dispose() {
    stateHandle.setVolatile(this, DisposalTrace())
  }

  private companion object {

    val stateHandle: VarHandle = MethodHandles
      .privateLookupIn(InstanceContainerImpl::class.java, MethodHandles.lookup())
      .findVarHandle(InstanceContainerImpl::class.java, "_state", Any::class.java)
  }
}
