// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.IjentSession
import com.intellij.platform.ijent.tcp.IjentIsolatedTcpDeployingStrategy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Path

/**
 * Abstract base class for TCP-based EEL machines providing IJent session management.
 *
 * **Session lifecycle:**
 * - Sessions are created lazily on first [toEelApi] call
 * - Dead sessions (isRunning=false) are automatically recreated
 * - Failed deployments can be retried after a backoff period (3 seconds)
 *
 * **Thread safety:**
 * - Session state is guarded by [sessionMutex]
 * - Fast path in [toEelApi] uses volatile read for running sessions
 * - Concurrent [toEelApi] calls wait on mutex; first caller creates session
 *
 * **State machine:**
 * - NotStarted → Started (on first toEelApi call)
 * - Started → Started (session dies → recreated on next toEelApi)
 * - NotStarted → Failed (on creation error, excluding cancellation)
 * - Failed → Started (after backoff period)
 */
abstract class TcpEelMachine(override val internalName: String) : EelMachine {

  private val sessionMutex = Mutex()

  /**
   * Session state machine.
   * All state transitions happen under [sessionMutex] except for fast-path reads.
   */
  private sealed class SessionState {
    data object NotStarted : SessionState()
    data class Started(val session: IjentSession<IjentApi>) : SessionState()
    data class Failed(val error: Throwable, val nanoTime: Long) : SessionState()
  }

  @Volatile
  private var state: SessionState = SessionState.NotStarted

  /**
   * Returns true if the machine has an active, running session.
   */
  val isSessionRunning: Boolean
    get() = (state as? SessionState.Started)?.session?.isRunning == true

  protected abstract fun createStrategy(): IjentIsolatedTcpDeployingStrategy

  override suspend fun toEelApi(descriptor: EelDescriptor): EelApi {
    // Fast path: check if session is still running without acquiring mutex
    (state as? SessionState.Started)?.session?.takeIf { it.isRunning }?.let {
      return it.getIjentInstance(descriptor)
    }

    // Slow path: get or create session under mutex
    val session = sessionMutex.withLock {
      when (val currentState = state) {
        is SessionState.Started -> {
          if (currentState.session.isRunning) {
            currentState.session
          }
          else {
            // Session died, recreate it
            createSession()
          }
        }
        is SessionState.Failed -> {
          // Allow retry after backoff
          if (System.nanoTime() - currentState.nanoTime > RETRY_BACKOFF_NS) {
            createSession()
          }
          else {
            throw currentState.error
          }
        }
        SessionState.NotStarted -> {
          createSession()
        }
      }
    }
    return session.getIjentInstance(descriptor)
  }

  /**
   * Creates a new session. Must be called under [sessionMutex].
   * Concurrent callers wait on mutex and get the created session.
   */
  private suspend fun createSession(): IjentSession<IjentApi> {
    return try {
      val session: IjentSession<IjentApi> = createStrategy().createIjentSession()
      state = SessionState.Started(session)
      session
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      state = SessionState.Failed(e, System.nanoTime())
      throw e
    }
  }

  override fun ownsPath(path: Path): Boolean {
    val pathInternalName = TcpEelPathParser.extractInternalMachineId(path) ?: return false
    return pathInternalName == this.internalName
  }

  companion object {
    /**
     * Backoff time in nanoseconds before retrying after a failed deployment.
     */
    private const val RETRY_BACKOFF_NS = 3_000_000_000L // 3 seconds
  }
}
