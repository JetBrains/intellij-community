// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.EelMachineConnectionProvider.Bridge
import com.intellij.platform.eel.EelMachineConnectionRegistry.MachineLeaseSlot.State.Connected
import com.intellij.platform.eel.EelMachineConnectionRegistry.MachineLeaseSlot.State.Unconnected
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.LocalEelMachine
import com.intellij.platform.eel.provider.localEel
import kotlinx.coroutines.Job
import org.jetbrains.annotations.ApiStatus
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * Internal SPI for opening reference-counted [EelMachine] connection leases.
 *
 * A lease is block-scoped: [EelMachineConnectionRegistry.withConnection] acquires it before the block runs and releases it
 * when the block completes, whether normally, by an exception, or by cancellation. The only non-block lease is the
 * deploy-free project lease, see [EelMachineConnectionRegistry.acquireProjectLease].
 *
 * All [EelMachine], [EelDescriptor], and `Eel*Api` instances for the same environment share consistent backend and OS characteristics
 * (for example, an IJent machine corresponds to an IJent descriptor and an `IjentApi` whose [EelApi.fs] is an `IjentFileSystemApi`).
 * Because [EelApi] already identifies its backend and exposes [EelApi.machine] and [EelApi.descriptor], provider methods only accept
 * [EelApi].
 */
@ApiStatus.Internal
interface EelMachineConnectionProvider {
  fun isSessionStillAlive(eelApi: EelApi): Boolean = true

  /**
   * SPI for providing [EelMachineConnectionProvider] instances. Loaded via [ServiceLoader] from `eel-impl`.
   */
  @ApiStatus.Internal
  fun interface Bridge {
    fun getAll(): List<EelMachineConnectionProvider>
  }

  companion object {
    val registry: EelMachineConnectionRegistry by lazy { EelMachineConnectionRegistry() }
  }
}

@ApiStatus.Internal
@Suppress("WHEN_SUBJECT_CAN_BE_NULL_IN_JAVA")
class EelMachineConnectionRegistry {
  @ApiStatus.Internal
  interface Listener {
    fun onConnectionOpened(eelApi: EelApi) {}

    fun isConnectionStillAlive(eelApi: EelApi): Boolean = true

    fun onConnectionClosed(eelApi: EelApi) {}
  }

  private class MachineLeaseSlot {
    sealed interface State {
      val refCount: Int
      fun withRefCount(refCount: Int): State

      class Unconnected(override val refCount: Int) : State {
        override fun withRefCount(refCount: Int): Unconnected = Unconnected(refCount)
      }

      class Connected(
        override val refCount: Int,
        val eelApi: EelApi,
      ) : State {
        override fun withRefCount(refCount: Int): Connected = Connected(refCount, eelApi)
      }
    }

    val state = AtomicReference<State>(Unconnected(refCount = 0))
  }

  private val slots = ConcurrentHashMap<EelMachine, MachineLeaseSlot>()

  private val listeners = CopyOnWriteArrayList<Listener>()

  private val providerBridge: Bridge? = ServiceLoader.load(Bridge::class.java, Bridge::class.java.classLoader).firstOrNull()

  @ApiStatus.Internal
  fun addListener(listener: Listener) {
    listeners.add(listener)
    for (slot in slots.values) {
      val state = slot.state.get()
      if (state is Connected) {
        listener.onConnectionOpened(state.eelApi)
      }
    }
  }

  private fun isSessionAlive(eelApi: EelApi): Boolean {
    return providerBridge?.getAll().orEmpty().all { it.isSessionStillAlive(eelApi) } &&
           listeners.all { it.isConnectionStillAlive(eelApi) }
  }

  private fun notifyConnectionOpened(eelApi: EelApi) {
    for (listener in listeners) {
      listener.onConnectionOpened(eelApi)
    }
  }

  private fun notifyConnectionClosed(eelApi: EelApi) {
    for (listener in listeners) {
      listener.onConnectionClosed(eelApi)
    }
  }

  /**
   * Runs [block] with a connected [EelApi] of [machine], holding a lease on the connection for the whole duration of [block].
   *
   * The lease is released in `finally`, so it also ends when [block] is cancelled. Concurrent and nested calls for the same
   * machine share one [EelApi] and one [FileSystem]; they are closed when the last lease ends.
   */
  @ApiStatus.Internal
  @Suppress("WHEN_SUBJECT_CAN_BE_NULL_IN_JAVA")
  @Throws(EelUnavailableException::class)
  suspend fun <T> withConnection(machine: EelMachine, descriptor: EelDescriptor, block: suspend (EelApi) -> T): T {
    if (machine === LocalEelMachine) {
      check(descriptor === LocalEelDescriptor) { "Wrong descriptor: $descriptor for machine: $machine" }
      return block(localEel)
    }

    val slot = acquireSlot(machine)
    try {
      val api = getAlreadyConnectedEelApi(slot) ?: connect(slot, machine, descriptor)
      return block(api)
    }
    finally {
      releaseSlot(slot)
    }
  }

  private suspend fun connect(slot: MachineLeaseSlot, machine: EelMachine, descriptor: EelDescriptor): EelApi {
    // TODO This code looks complicated. It seems, that there's a lot of confusion between EelDescriptor and EelMachine in our code.
    val eelApi = machine.toEelApi(descriptor)
    while (true) {
      when (val currentState = slot.state.get()) {
        is Unconnected -> {
          val connectedState = Connected(
            refCount = currentState.refCount,
            eelApi = eelApi,
          )
          if (slot.state.compareAndSet(currentState, connectedState)) {
            notifyConnectionOpened(eelApi)
            return eelApi
          }
        }
        is Connected -> {
          if (isSessionAlive(currentState.eelApi)) {
            return currentState.eelApi
          }
          val connectedState = Connected(
            refCount = currentState.refCount,
            eelApi = eelApi,
          )
          if (slot.state.compareAndSet(currentState, connectedState)) {
            if (currentState.eelApi !== eelApi) {
              notifyConnectionClosed(currentState.eelApi)
            }
            notifyConnectionOpened(eelApi)
            return eelApi
          }
        }
      }
    }
  }

  private val Connected.isAlive: Boolean get() = isSessionAlive(eelApi)

  private tailrec fun getAlreadyConnectedEelApi(slot: MachineLeaseSlot): EelApi? {
    return when (val currentState = slot.state.get()) {
      is Connected if currentState.isAlive -> {
        currentState.eelApi
      }

      is Connected if slot.state.compareAndSet(currentState, Unconnected(currentState.refCount)) -> {
        notifyConnectionClosed(currentState.eelApi)
        null
      }

      is Connected -> {
        getAlreadyConnectedEelApi(slot)
      }

      is Unconnected -> {
        null
      }
    }
  }

  /**
   * Holds a lease on [machine] until [job] completes, without connecting to the machine.
   *
   * Unlike [withConnection], this does not deploy anything: it only keeps an already opened connection alive
   * while the project that owns [job] is open.
   */
  @ApiStatus.Internal
  fun acquireProjectLease(machine: EelMachine, job: Job) {
    if (machine === LocalEelMachine || !job.isActive) {
      return
    }
    val slot = acquireSlot(machine)
    job.invokeOnCompletion { releaseSlot(slot) }
  }

  @ApiStatus.Internal
  fun getLeaseCount(machine: EelMachine): Int {
    if (machine === LocalEelMachine) {
      return 0
    }
    val slot = slots[machine] ?: return 0
    return slot.state.get().refCount
  }

  private fun acquireSlot(machine: EelMachine): MachineLeaseSlot {
    val slot = slots.computeIfAbsent(machine) { MachineLeaseSlot() }
    while (true) {
      val currentState = slot.state.get()
      if (slot.state.compareAndSet(currentState, currentState.withRefCount(currentState.refCount + 1))) {
        return slot
      }
    }
  }

  private fun releaseSlot(slot: MachineLeaseSlot) {
    val releasedConnection: Connected?
    while (true) {
      val currentState = slot.state.get()
      if (currentState.refCount <= 0) {
        releasedConnection = null
        break
      }
      val nextRefCount = currentState.refCount - 1
      val nextState = if (nextRefCount == 0) {
        Unconnected(refCount = 0)
      }
      else {
        currentState.withRefCount(nextRefCount)
      }
      if (slot.state.compareAndSet(currentState, nextState)) {
        releasedConnection = if (nextRefCount == 0) {
          when (currentState) {
            is Unconnected -> null
            is Connected -> currentState
          }
        }
        else {
          null
        }
        break
      }
    }

    if (releasedConnection != null) {
      val apiToClose = releasedConnection.eelApi
      notifyConnectionClosed(apiToClose)
      if (apiToClose is AutoCloseable && slot.state.get().refCount == 0) {
        closeQuietly(apiToClose)
      }
    }
  }

  private fun closeQuietly(closeable: AutoCloseable) {
    runCatching {
      closeable.close()
    }
  }
}
