// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.locking.impl

import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.installThreadContext
import com.intellij.concurrency.withThreadLocal
import com.intellij.core.rwmutex.*
import com.intellij.openapi.application.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.locking.impl.listeners.ErrorHandler
import com.intellij.platform.locking.impl.listeners.LegacyProgressIndicatorProvider
import com.intellij.platform.locking.impl.listeners.LockAcquisitionListener
import com.intellij.util.ReflectionUtil
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import kotlinx.coroutines.*
import kotlinx.coroutines.internal.intellij.IntellijCoroutines
import org.jetbrains.annotations.ApiStatus
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.Result
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.getOrThrow
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * This enum is almost in one-to-one correspondence to [Permit], except that it handles downgrades of write and write-intent permits to Read permit.
 * I.e., for this case:
 * ```
 * writeAction {
 *   readAction {
 *     runBlockingCancellable {}
 *   }
 * }
 * ```
 * We need to parallelize the read permit, without an instance of read permit.
 */
private sealed interface ParallelizablePermit {
  @JvmInline
  value class Write(val writePermit: WritePermit) : ParallelizablePermit

  @JvmInline
  value class WriteIntent(val writeIntentPermit: WriteIntentPermit) : ParallelizablePermit

  @JvmInline
  value class Read(val readPermit: ReadPermit?) : ParallelizablePermit
}

private fun Permit.asParallelizablePermit(): ParallelizablePermit {
  return when (this) {
    is ReadPermit -> ParallelizablePermit.Read(this)
    is WriteIntentPermit -> ParallelizablePermit.WriteIntent(this)
    is WritePermit -> ParallelizablePermit.Write(this)
  }
}


@Suppress("ArrayInDataClass")
private data class ExposedWritePermitData(
  /**
   * The stack of write-intent permits that got upgraded to write permits
   */
  val writeIntentStack: Array<WriteIntentPermit>,
  /**
   * Obtained from [writeIntentStack]
   */
  val writePermitStack: Array<WritePermit>,
  /**
   * The highest-level write permit
   */
  val finalWritePermit: WritePermit,
  /**
   * The write-intent permit that was used to obtain [finalWritePermit]
   */
  val originalWriteIntentPermit: WriteIntentPermit,
  /**
   * The original permit that was existing on the current thread
   */
  val oldPermit: WriteIntentPermit?,
)

/**
 * This class implements a technique that we call "nested locks" (a.k.a. "n-locks", where "n" stands for "natural number").
 *
 * The purpose of n-locks is to support the scenario of "lock parallelization".
 * Consider the following code:
 * ```kotlin
 * withContext(Dispatchers.EDT) {
 *   writeIntentReadAction { // 1
 *     runWithModalProgressBlocking {
 *       launch(Dispatchers.Default) {
 *         backgroundWriteAction { // 2
 *         }
 *       }
 *     }
 *   }
 * }
 * ```
 * If we have one lock that acquires a write-intent permit (1), then, in order to perform the background WA (2), we would need to pass
 * the write-intent permit to the background thread. However, we must also maintain mutual exclusion for the following cases:
 * ```kotlin
 * runWithModalProgressBlocking {
 *   launch(Dispatchers.Default) {
 *      backgroundWriteAction {}
 *   }
 *   launch(Dispatchers.Default) {
 *     backgroundWriteAction {}
 *   }
 *   launch(Dispatchers.Default) {
 *     readAction {}
 *   }
 * }
 * ```
 *
 * This scenario can be handled by the introduction of another lock, that works within modal progress.
 * Abstracting a little, here we deal not with just a modal progress, but with an attempt to parallelize inside a taken lock.
 * This is what we mean by "lock parallelization".
 *
 * Another obstacle is that this situation can be scaled:
 * ```kotlin
 * runWithModalProgressBlocking {
 *   withContext(Dispatchers.EDT) {
 *     runWithModalProgressBlocking {
 *     }
 *   }
 * }
 * ```
 * It means that we need to maintain several locks, one for each parallelization attempt.
 *
 * Each new layer of locks is denoted as "level", where level `0` stands for the global shared lock, and bigger levels correspond to new parallelization layers.
 *
 * There is also an alternative way to parallelize locks: since we know that it is safe to execute read actions in parallel, we can simply
 * grant read access to all computations when lock parallelization is requested:
 * ```
 * readAction {
 *   runBlockingCancellable {
 *     launch(Default) {
 *       readAction {} // succeeds regardless of pending write actions
 *     }
 *   }
 * }
 * ```
 * This read-parallelization would break the contracts in `writeIntentReadAction`, but it is safe to apply for read actions.
 * It actually helps to resolve certain kind of deadlocks.
 */
@ApiStatus.Internal
class NestedLocksThreadingSupport : ThreadingSupport {
  companion object {
    private const val SPIN_TO_WAIT_FOR_LOCK: Int = 100
    private val logger = Logger.getInstance(NestedLocksThreadingSupport::class.java)
  }

  /**
   * Current permit that is given to a thread.
   * We need to have this in a thread local, because thread context often gets reset, but we still need to proceed with locking actions.
   * Example:
   * ```
   * WriteIntentReadAction.run {
   *   dispatchAllInvocationEvents { // <- this method starts processing event queue, hence we reset the context there
   *     WriteIntentReadAction.run { // since the context is reset, we need to obtain the higher-level permit;
   *                                 // otherwise there would be a deadlock on a new attempt to acquire a write-intent permit
   *     }
   *   }
   * }
   * ```
   */
  val thisLevelPermit: ThreadLocal<Permit> = ThreadLocal.withInitial { null }

  /**
   * This field is set after starting a write action.
   *
   * It is needed to support [NestedLocksThreadingSupport.executeSuspendingWriteAction].
   * During the execution of suspending WA, we release and re-acquire write permits.
   *
   * Invariants: these fields must be accessed only from EDT, so all usages are intended to be serialized.
   * We don't add excessive memory barriers here for the sake of performance.
   */
  private var publishedPermits: ExposedWritePermitData? = null


  /**
   * The global lock that is a default choice when lock parallelization is absent
   */
  private val zeroLevelComputationState = ComputationState(emptyArray(), RWMutexIdea(), false)

  /**
   * A stack of computation states for the thread that is allowed to hold the Write-Intent lock
   * This variable is `null` on all threads except EDT.
   * It is needed because the thread context is often getting reset on EDT, but we still need to get the last relevant computation state.
   */
  private val statesOfWIThread: ThreadLocal<MutableList<ComputationState>?> = ThreadLocal.withInitial { null }

  private val readActionListeners: CopyOnWriteArrayList<ReadActionListener> = CopyOnWriteArrayList()
  private val myWriteActionListeners: CopyOnWriteArrayList<WriteActionListener> = CopyOnWriteArrayList()
  private val myWriteIntentActionListeners: CopyOnWriteArrayList<WriteIntentReadActionListener> = CopyOnWriteArrayList()
  private var myLockAcquisitionListener: LockAcquisitionListener<*>? = null
  private var myWriteLockReacquisitionListener: WriteLockReacquisitionListener? = null
  private var myLegacyProgressIndicatorProvider: LegacyProgressIndicatorProvider? = null

  @Volatile
  private var errorHandler: ErrorHandler? = null

  private val myWriteActionsStack = Collections.synchronizedList(ArrayList<Class<*>>())
  private var myWriteStackBase = 0

  /**
   * We need to keep track of pending write actions on each level.
   * The reason is that low-level background write actions should not block higher-level read actions:
   * ```
   * backgroundWriteAction {} // pending
   * modalProgress {
   *   readAction {} // should proceed
   * }
   * ```
   *
   * The underlying array rarely changes in its size, but reasonably frequently changes its values.
   * That's why we don't use COW list here: value updates may be frequent.
   * Also, we'd like to have some sort of random access, that's why we don't use any lock-free list.
   *
   * When we enlarge this array, we copy all underlying [AtomicInteger] by reference, so that everyone who reads old values of elements
   * will have the same data as if we managed to extend the array before all reads.
   */
  @Suppress("RemoveExplicitTypeArguments")
  private val myWriteActionPending = AtomicReference<Array<AtomicInteger>>(Array(1) { AtomicInteger(0) })
  private val myNoWriteActionCounter = ThreadLocal<Int>.withInitial { 0 }

  private val myReadActionsInThread = ThreadLocal.withInitial { 0 }
  private val myLockingProhibited: ThreadLocal<Pair<Boolean, String>?> = ThreadLocal.withInitial { null }

  // todo: reimplement with listeners in IJPL-177760
  private val myTopmostReadAction = ThreadLocal.withInitial { false }

  private val ignorePreventiveActions: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

  @Volatile
  private var myWriteAcquired: Thread? = null

  private val myLockInterceptor: ThreadLocal<PermitWaitingInterceptor?> = ThreadLocal.withInitial { null }

  /**
   * Performance optimization.
   * The IDE often asserts the presence of write or write-intent lock.
   * We would like to avoid the traversal of coroutine contexts each time, hence we store a thread-local
   */
  private val myWriteIntentAcquired: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

  /**
   * Used to wakeup non-blocking read actions
   */
  private val pendingWriteActionFollowup: MutableList<Runnable> = ArrayList()

  private inline fun <T> List<T>.traverse(action: (T) -> Unit) {
    var index = 0
    while (index < size) {
      try {
        action(this[index++])
      }
      catch (_: CancellationException) {
        // ignored
      }
      catch (e: Throwable) {
        try {
          errorHandler?.handleError(e)
        }
        catch (e: Throwable) {
          // swallowing error :(
        }
      }
    }
  }

  private inline fun <T> List<T>.traverseBackwards(action: (T) -> Unit) {
    var index = lastIndex
    while (index >= 0) {
      try {
        action(this[index--])
      }
      catch (_: CancellationException) {
        // ignored
      }
      catch (e: Throwable) {
        try {
          errorHandler?.handleError(e)
        }
        catch (e: Throwable) {
          // swallowing error :(
        }
      }
    }
  }

  /**
   * Shallow clone of [CopyOnWriteArrayList] that wraps the underlying array.
   * I swear that I will not modify the array further
   */
  private fun <T> CopyOnWriteArrayList<T>.doClone(): List<T> {
    @Suppress("UNCHECKED_CAST")
    return clone() as List<T>
  }

  fun setErrorHandler(handler: ErrorHandler) {
    errorHandler = handler
  }

  fun removeErrorHandler() {
    errorHandler = null
  }

  /**
   * The locking state that is shared by all computations belonging to the same level.
   */
  // we need backreference to ThreadingSupport because there can exist multiple instances of ThreadingSupport, like in Analyzer
  private inner class ComputationState(
    /**
     * A set of write-intent permits of **previous** locks (from `0` to `n - 1` included)
     * We need them for obtaining write permit on [thisLevelLock] while cancelling lower-level actions.
     */
    private val lowerLevelPermits: Array<WriteIntentPermit>,

    /**
     * The lock of level `n`. This lock is intended to be shared between all computations belonging to the same level.
     */
    private val thisLevelLock: RWMutexIdea,

    /**
     * An artifact of parallelizing the read lock. The code parallelized from the read lock
     * has no way of upgrading further, so it is safe to share read access with whole remaining computation tree.
     */
    private val isParallelizedRead: Boolean,
  ) {

    fun level(): Int {
      return lowerLevelPermits.size
    }

    fun isParallelizedReadPermit(): Boolean {
      return isParallelizedRead
    }

    fun getThisThreadPermit(): ParallelizablePermit? {
      if (isParallelizedRead) {
        return ParallelizablePermit.Read(null)
      }
      else {
        return thisLevelPermit.get()?.asParallelizablePermit()
      }
    }

    /**
     * Fast check for read access; skips unnecessary boxing that is caused by inline classes
     */
    fun isReadAcquired(): Boolean {
      return isParallelizedRead || thisLevelPermit.get() != null
    }

    /**
     * Obtains a write permit if current thread holds a write-intent permit
     */
    fun upgradeWritePermit(permit: WriteIntentPermit): ExposedWritePermitData {
      val finalPermit = runSuspendMaybeConsuming(false) {
        permit.acquireWriteActionPermit()
      }

      // we need to acquire writes on the whole stack of lower-level write-intent permits,
      // since we want to cancel all lower-level running read actions
      val writePermits = Array(level()) {
        runSuspendMaybeConsuming(false) {
          lowerLevelPermits[it].acquireWriteActionPermit()
        }
      }
      return ExposedWritePermitData(lowerLevelPermits, writePermits, finalPermit, permit, permit)
    }

    suspend fun upgradeWritePermitSuspending(permit: WriteIntentPermit): ExposedWritePermitData {
      val finalPermit = permit.acquireWriteActionPermit()

      // we need to acquire writes on the whole stack of lower-level write-intent permits,
      // since we want to cancel all lower-level running read actions
      val writePermits = Array(level()) {
        lowerLevelPermits[it].acquireWriteActionPermit()
      }
      return ExposedWritePermitData(lowerLevelPermits, writePermits, finalPermit, permit, permit)
    }

    fun releaseWritePermit() {
      val currentPublishedPermits = checkNotNull(hack_getPublishedWriteData()) {
        "'releaseWrite' must be called only after 'acquireWritePermit' or 'upgradeWritePermit'"
      }
      thisLevelPermit.set(currentPublishedPermits.oldPermit)
      hack_setPublishedPermitData(null)
      val writePermits = currentPublishedPermits.writePermitStack
      var writePermitIndex = writePermits.lastIndex
      while (writePermitIndex >= 0) {
        writePermits[writePermitIndex--].release()
      }
      currentPublishedPermits.finalWritePermit.release()
      if (currentPublishedPermits.oldPermit == null) {
        // it means that we were asked to acquire write permit without existing write-intent
        currentPublishedPermits.originalWriteIntentPermit.release()
      }
    }

    /**
     * Obtains a write-intent permit if the current thread does not hold anything
     */
    fun acquireWriteIntentPermit(): WriteIntentPermit {
      val permit = runSuspendMaybeConsuming(false) {
        thisLevelLock.acquireWriteIntentActionPermit()
      }
      thisLevelPermit.set(permit)
      return permit
    }

    /**
     * Obtains a write-intent permit if the current thread does not hold anything
     */
    suspend fun acquireWriteIntentPermitSuspending(): WriteIntentPermit {
      val permit = thisLevelLock.acquireWriteIntentActionPermit()
      // we DO NOT use thread-locals here, the thread is not set in stone for suspending code
      return permit
    }

    /**
     * Releases a write-intent permit acquired in [acquireWriteIntentPermit].
     * Must be preceded by [acquireWriteIntentPermit] on this thread.
     */
    fun releaseWriteIntentPermit(writeIntentPermit: WriteIntentPermit) {
      thisLevelPermit.set(null)
      return writeIntentPermit.release()
    }

    /**
     * Obtains a write-intent permit if the current thread does not hold anything
     */
    fun acquireReadPermit(): ReadPermit {
      val permit = runSuspendMaybeConsuming(true) {
        thisLevelLock.acquireReadActionPermit(false)
      }
      thisLevelPermit.set(permit)
      return permit
    }

    /**
     * same as [acquireReadPermit], but returns `null` if acquisition failed
     */
    fun tryAcquireReadPermit(): ReadPermit? {
      val permit = runSuspendMaybeConsuming(false) {
        thisLevelLock.tryAcquireReadActionPermit()
      }
      if (permit != null) {
        thisLevelPermit.set(permit)
      }
      return permit
    }

    /**
     * Releases a read permit acquired in [acquireReadPermit] or [tryAcquireReadPermit].
     */
    fun releaseReadPermit(readPermit: ReadPermit) {
      check(thisLevelPermit.get() === readPermit) { "Attempt to release of a read permit that was not acquired in the current thread" }
      thisLevelPermit.set(null)
      return readPermit.release()
    }

    /**
     * Starts parallelization of the write-intent permit.
     * We introduce a new level of locking here
     */
    fun parallelizeWriteIntent(thisLevelPermit: WriteIntentPermit): Pair<ComputationState, AccessToken> {
      val existingPermit = this@NestedLocksThreadingSupport.thisLevelPermit.get()
      check(existingPermit == thisLevelPermit) { "Internal error: attempt to parallelize a foreign write-intent permit" }
      this@NestedLocksThreadingSupport.thisLevelPermit.set(null)
      return ComputationState(lowerLevelPermits + thisLevelPermit, RWMutexIdea(), false) to object : AccessToken() {
        override fun finish() {
          this@NestedLocksThreadingSupport.thisLevelPermit.set(existingPermit)
        }
      }
    }

    /**
     * Starts parallelization of the write-intent permit.
     * We simply grant the read access to all computations.
     */
    fun parallelizeRead(): Pair<ComputationState, () -> Unit> {
      return ComputationState(lowerLevelPermits, thisLevelLock, true) to {}
    }

    override fun toString(): String {
      return "ComputationState(level=${level()},thisLevelLock=$thisLevelLock,isParallelizedRead=${isParallelizedRead})"
    }
  }

  private class ComputationStateContextElement(val computationState: ComputationState) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>
      get() = ComputationStateContextElement

    companion object : CoroutineContext.Key<ComputationStateContextElement>

    override fun toString(): String {
      return "$computationState"
    }
  }

  override fun getPermitAsContextElement(baseContext: CoroutineContext, shared: Boolean): Pair<CoroutineContext, () -> Unit> {
    val currentComputationStateElement = baseContext[ComputationStateContextElement]
    // we suppose that the caller passes `baseContext` that is actually correct
    val currentComputationState = currentComputationStateElement?.computationState
                                  ?: statesOfWIThread.get()?.lastOrNull()
                                  ?: zeroLevelComputationState

    if (!shared) {
      return (currentComputationStateElement ?: ComputationStateContextElement(currentComputationState)) to { }
    }

    // now we need to parallelize the existing permit
    val currentPermit = currentComputationState.getThisThreadPermit()

    if (isInTopmostReadAction()) {
      // this case is identical to the branch of parallelization of read lock. Regardless of what permit is currently being held by this thread,
      // the caller requested parallelization while being in a read action, hence we need to parallelize a read.
      val (newComputationState, cleanup) = currentComputationState.parallelizeRead()
      return ComputationStateContextElement(newComputationState) to cleanup
    }
    when (currentPermit) {
      null -> {
        // this is equivalent to a pure `runBlockingCancellable` on a thread that does not hold locks
        // we can simply share the current lock

        // here we do elvis expression to save an allocation
        return (currentComputationStateElement ?: ComputationStateContextElement(currentComputationState)) to { }
      }
      is ParallelizablePermit.Read -> {
        // This is equivalent to `runBlockingCancellable` under `readAction`.
        // We must ensure that the computation in `runBlockingCancellable` has read access;
        // Otherwise there can be deadlocks where `runBlockingCancellable` is canceled by a pending write which cannot start
        // because some computation inside `runBlockingCancellable` waits for read access in a blocking way
        val (newComputationState, cleanup) = currentComputationState.parallelizeRead()
        return ComputationStateContextElement(newComputationState) to cleanup
      }
      is ParallelizablePermit.WriteIntent -> {
        // we are attempting to share a write-intent lock
        // it means that we are entering a new level of locking
        val (newComputationState, cleanup) = currentComputationState.parallelizeWriteIntent(currentPermit.writeIntentPermit)
        statesOfWIThread.set(statesOfWIThread.get() ?: mutableListOf())
        statesOfWIThread.get()?.add(newComputationState)

        // we need to extend the array atomically
        // currently, there will never be any contenders: write-intent is allowed only on EDT, so this loop completes in one iteration.
        // but here we also insert a memory barrier by working with an atomic reference, so every other thread now sees
        // the updated value of an array.
        do {
          val currentPendingWaArray = myWriteActionPending.get()
          val newArray = currentPendingWaArray + AtomicInteger(0)
        }
        while (!myWriteActionPending.compareAndSet(currentPendingWaArray, newArray))
        drainWriteActionFollowups()
        myWriteIntentAcquired.set(false)

        return ComputationStateContextElement(newComputationState) to {
          myWriteIntentAcquired.set(true)
          var isWriteActionPendingOnCurrentLevel: Boolean
          do {
            val currentPendingWaArray = myWriteActionPending.get()

            @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog") // the suggested method adds useless nullability
            val newArray = Arrays.copyOf(currentPendingWaArray, currentPendingWaArray.size - 1)

            isWriteActionPendingOnCurrentLevel = newArray.last().get() > 0
          }
          while (!myWriteActionPending.compareAndSet(currentPendingWaArray, newArray))

          /**
           * We need to cancel read actions because after the change of level we might get a pending WA which needs to run asap.
           * See the comment in [isWriteActionPending]
           */
          if (isWriteActionPendingOnCurrentLevel) {
            myWriteLockReacquisitionListener?.beforeWriteLockReacquired()
          }
          statesOfWIThread.get()?.removeLast()
          cleanup.finish()
        }
      }
      is ParallelizablePermit.Write -> {
        // The parallelization of a write action is a very tricky topic.
        // It is difficult to get right, and we are not sure about the valid semantics yet.
        // Unfortunately, our old tests rely on this concept.
        // We forbid write lock parallelization in production (see `runBlockingCancellable`), and instead allow it only in tests
        val currentPermits = checkNotNull(hack_getPublishedWriteData()) { "Parallelization of write permit must happen when write lock is acquired" }
        val currentWriteIntentPermit = currentPermits.originalWriteIntentPermit
        // the idea of write lock parallelization is that we release the **top-level** write permit,
        // capture read permit, and parallelize it.
        // so this is the parallelization of the second kind.
        currentPermit.writePermit.release()
        val newPermit = currentComputationState.acquireReadPermit()
        val (newState, cleanup) = currentComputationState.parallelizeRead()
        return ComputationStateContextElement(newState) to {
          cleanup()
          currentComputationState.releaseReadPermit(newPermit)
          // we need to reacquire the previously released write permit
          val newWritePermit = runSuspendMaybeConsuming(false) {
            currentWriteIntentPermit.acquireWriteActionPermit()
          }
          hack_setThisLevelPermit(newWritePermit)
          hack_setPublishedPermitData(currentPermits.copy(finalWritePermit = newWritePermit))
        }
      }
    }
  }

  override fun isParallelizedReadAction(context: CoroutineContext): Boolean {
    return context[ComputationStateContextElement]?.computationState?.isParallelizedReadPermit() == true
  }

  /**
   * Needs for the following situations:
   * ```
   * WriteAction.run {
   *   ReadAction.run {
   *     runBlockingCancellable {
   *     }
   *   }
   * }
   * ```
   * yes, we have it in production, and we decided to permit this kind of parallelization for now. At least it is clear that users want to parallelize read permit.
   */
  override fun isInTopmostReadAction(): Boolean {
    // once a read action was requested
    return myTopmostReadAction.get()
  }

  private fun getComputationState(): ComputationState {
    // here we retrieve [statesOfWiThread] first in opposition to `getPermitAsContextElement`,
    // because current thread context may be leaked from some outer computation, and we are interested in precisely latest
    // state of WI thread
    return statesOfWIThread.get()?.lastOrNull()
           ?: currentThreadContext()[ComputationStateContextElement]?.computationState
           ?: zeroLevelComputationState
  }

  override fun <T> runWriteIntentReadAction(computation: () -> T): T {
    handleLockAccess("write-intent lock")
    return runPreventiveWriteIntentReadAction(computation)
  }

  override fun <T> runPreventiveWriteIntentReadAction(computation: () -> T): T {
    return if (ignorePreventiveActions.get() == true) {
      computation()
    } else {
      doRunWriteIntentReadAction(computation)
    }
  }

  fun <T> doRunWriteIntentReadAction(computation: () -> T): T {
    val frozenListeners = myWriteIntentActionListeners.doClone()
    fireBeforeWriteIntentReadActionStart(frozenListeners, computation.javaClass)
    val currentReadState = myTopmostReadAction.get()
    myTopmostReadAction.set(false)
    val currentWriteIntentState = myWriteIntentAcquired.get()
    myWriteIntentAcquired.set(true)
    val computationState = getComputationState()
    val currentPermit = computationState.getThisThreadPermit()
    try {
      var permitToRelease: WriteIntentPermit? = null
      when (currentPermit) {
        null -> {
          permitToRelease = computationState.acquireWriteIntentPermit()
        }
        is ParallelizablePermit.Read -> {
          error("WriteIntentReadAction can not be called from ReadAction")
        }
        is ParallelizablePermit.WriteIntent, is ParallelizablePermit.Write -> {}
      }
      try {
        fireWriteIntentActionStarted(frozenListeners, computation.javaClass)
        return computation()
      }
      finally {
        fireWriteIntentActionFinished(frozenListeners, computation.javaClass)
        if (permitToRelease != null) {
          /**
           * The permit to release can be changed because of [releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack] inside
           */
          val newPermitToRelease = (computationState.getThisThreadPermit() as ParallelizablePermit.WriteIntent).writeIntentPermit
          computationState.releaseWriteIntentPermit(newPermitToRelease)
        }
      }
    }
    finally {
      myWriteIntentAcquired.set(currentWriteIntentState)
      myTopmostReadAction.set(currentReadState)
      afterWriteIntentReadActionFinished(frozenListeners, computation.javaClass)
    }
  }

  override fun isWriteIntentLocked(): Boolean {
    if (myWriteAcquired == Thread.currentThread()) {
      return true
    }
    return myWriteIntentAcquired.get()
  }

  override fun isReadAccessAllowed(): Boolean {
    return getComputationState().isReadAcquired()
  }

  override fun <T> runUnlockingIntendedWrite(action: () -> T): T {
    return action()
  }

  @ApiStatus.Internal
  override fun addReadActionListener(listener: ReadActionListener) {
    readActionListeners.add(listener)
  }

  @ApiStatus.Internal
  override fun removeReadActionListener(listener: ReadActionListener) {
    check(readActionListeners.remove(listener)) {
      "ReadActionListener $listener is not registered"
    }
  }

  private fun smartAcquireReadPermit(state: ComputationState): ReadPermit {
    var permit = state.tryAcquireReadPermit()
    if (permit != null) {
      return permit
    }

    readActionListeners.forEach {
      it.fastPathAcquisitionFailed()
    }

    // Check for cancellation
    val indicator = myLegacyProgressIndicatorProvider?.obtainProgressIndicator()
    // Nothing to check or cannot be canceled
    if (indicator == null || Cancellation.isInNonCancelableSection()) {
      return state.acquireReadPermit()
    }

    // Spin & sleep with checking for cancellation
    var iter = 0
    do {
      indicator.checkCanceled()
      if (iter++ < SPIN_TO_WAIT_FOR_LOCK) {
        Thread.yield()
        permit = state.tryAcquireReadPermit()
      }
      else {
        permit = state.acquireReadPermit() // getReadPermitTimed (1) // millis
      }
    }
    while (permit == null)
    return permit
  }

  private fun handleLockAccess(culprit: String) {
    val lockProhibition = myLockingProhibited.get()
    if (lockProhibition != null) {
      val exception = ThreadingSupport.LockAccessDisallowed("Attempt to take $culprit was prevented\n${lockProhibition.second}")
      if (lockProhibition.first) {
        logger.error(exception)
      }
      else {
        throw exception
      }
    }
  }

  override fun <T> runReadAction(clazz: Class<*>, action: () -> T): T {
    handleLockAccess("read lock")

    val frozenListeners = readActionListeners.doClone()
    fireBeforeReadActionStart(frozenListeners, clazz)

    val currentReadState = myTopmostReadAction.get()
    myTopmostReadAction.set(true)

    val computationState = getComputationState()
    val currentPermit = computationState.getThisThreadPermit()
    var readPermitToRelease: ReadPermit? = null

    when (currentPermit) {
      is ParallelizablePermit.Read, is ParallelizablePermit.Write, is ParallelizablePermit.WriteIntent -> {}
      null -> {
        readPermitToRelease = smartAcquireReadPermit(computationState)
      }
    }

    // For diagnostic purposes register that we in read action, even if we use stronger lock
    myReadActionsInThread.set(myReadActionsInThread.get() + 1)

    try {
      fireReadActionStarted(frozenListeners, clazz)
      val rv = action()
      return rv
    }
    finally {
      fireReadActionFinished(frozenListeners, clazz)

      myReadActionsInThread.set(myReadActionsInThread.get() - 1)
      if (readPermitToRelease != null) {
        computationState.releaseReadPermit(readPermitToRelease)
      }
      myTopmostReadAction.set(currentReadState)
      fireAfterReadActionFinished(frozenListeners, clazz)
    }
  }

  override fun tryRunReadAction(action: Runnable): Boolean {
    handleLockAccess("fail-fast read lock")

    val frozenListeners = readActionListeners.doClone()
    fireBeforeReadActionStart(frozenListeners, action.javaClass)

    val currentReadState = myTopmostReadAction.get()
    myTopmostReadAction.set(true)
    val computationState = getComputationState()
    val currentPermit = computationState.getThisThreadPermit()
    var readPermitToRelease: ReadPermit? = null
    try {
      when (currentPermit) {
        null -> {
          readPermitToRelease = computationState.tryAcquireReadPermit()
          if (readPermitToRelease == null) {
            return false
          }
        }
        is ParallelizablePermit.Read, is ParallelizablePermit.Write, is ParallelizablePermit.WriteIntent -> {}
      }

      // For diagnostic purposes register that we in read action, even if we use stronger lock
      myReadActionsInThread.set(myReadActionsInThread.get() + 1)

      try {
        fireReadActionStarted(frozenListeners, action.javaClass)
        action.run()
        return true
      }
      finally {
        fireReadActionFinished(frozenListeners, action.javaClass)

        myReadActionsInThread.set(myReadActionsInThread.get() - 1)
        if (readPermitToRelease != null) {
          computationState.releaseReadPermit(readPermitToRelease)
        }
      }
    }
    finally {
      myTopmostReadAction.set(currentReadState)
      fireAfterReadActionFinished(frozenListeners, action.javaClass)
    }

  }

  override fun isReadLockedByThisThread(): Boolean {
    return getComputationState().getThisThreadPermit() is ParallelizablePermit.Read
  }

  @ApiStatus.Internal
  override fun addWriteActionListener(listener: WriteActionListener) {
    myWriteActionListeners.add(listener)
  }

  @ApiStatus.Internal
  override fun addWriteIntentReadActionListener(listener: WriteIntentReadActionListener) {
    myWriteIntentActionListeners.add(listener)
  }

  override fun removeWriteIntentReadActionListener(listener: WriteIntentReadActionListener) {
    check(myWriteIntentActionListeners.remove(listener)) {
      "WriteIntentReadActionListener $listener is not registered"
    }
  }

  @ApiStatus.Internal
  override fun removeWriteActionListener(listener: WriteActionListener) {
    check(myWriteActionListeners.remove(listener)) {
      "WriteActionListener $listener is not registered"
    }
  }

  fun setLockAcquisitionListener(listener: LockAcquisitionListener<*>) {
    if (myLockAcquisitionListener != null)
      error("LockAcquisitionListener already registered")
    myLockAcquisitionListener = listener
  }

  @ApiStatus.Internal
  fun setLockAcquisitionInterceptor(consumer: (Deferred<*>) -> Unit) {
    myLockInterceptor.set(PermitWaitingInterceptor(consumer))
  }

  @ApiStatus.Internal
  override fun setWriteLockReacquisitionListener(listener: WriteLockReacquisitionListener) {
    if (myWriteLockReacquisitionListener != null)
      error("WriteLockReacquisitionListener already registered")
    myWriteLockReacquisitionListener = listener
  }

  @ApiStatus.Internal
  override fun removeWriteLockReacquisitionListener(listener: WriteLockReacquisitionListener) {
    if (myWriteLockReacquisitionListener != listener)
      error("WriteLockReacquisitionListener is not registered")
    myWriteLockReacquisitionListener = null
  }

  @ApiStatus.Internal
  fun setLegacyIndicatorProvider(provider: LegacyProgressIndicatorProvider) {
    if (myLegacyProgressIndicatorProvider != null)
      error("LegacyProgressIndicatorProvider already registered")
    myLegacyProgressIndicatorProvider = provider
  }

  @ApiStatus.Internal
  fun removeLegacyIndicatorProvider(provider: LegacyProgressIndicatorProvider) {
    if (myLegacyProgressIndicatorProvider != provider)
      error("LegacyProgressIndicatorProvider is not registered")
    myLegacyProgressIndicatorProvider = null
  }

  fun removeLockAcquisitionListener(listener: LockAcquisitionListener<*>) {
    if (myLockAcquisitionListener != listener)
      error("LockAcquisitionListener is not registered")
    myLockAcquisitionListener = null
  }

  override fun <T> runWriteAction(clazz: Class<*>, action: () -> T): T {
    val computationState = getComputationState()
    val writeIntentInitResult = prepareWriteIntentAcquiredBeforeWriteBlocking(computationState, clazz)
    try {
      val writeInitResult = prepareWriteFromWriteIntentBlocking(computationState, clazz, writeIntentInitResult)
      return writeInitResult.applyThreadLocalActions().use {
        action()
      }
    }
    catch (e: CancellationException) {
      val job = currentThreadContext()[Job]
      if (job != null && job.isCancelled && e !is ProcessCanceledException) {
        // the lock acquisition was promptly canceled, so we need to rethrow a PCE from here to comply with blocking context
        throw ProcessCanceledException(e)
      }
      else {
        throw e
      }
    }
    finally {
      writeIntentInitResult.release()
    }
  }

  override suspend fun <T> runWriteAction(action: () -> T): T {
    val computationState = getComputationState()
    val writeIntentInitResult = prepareWriteIntentAcquiredBeforeWriteSuspending(computationState)
    try {
      val writeInitResult = prepareWriteFromWriteIntentSuspending(computationState, writeIntentInitResult)
      return writeInitResult.applyThreadLocalActions().use {
        action()
      }
    } finally {
      // we have an assymetry with the blocking case here: `prepareWriteIntentAcquiredBeforeWriteSuspending` does not install the thread-local permit,
      // because there are no guarantees that the thread will be preserved between suspensions.
      // However, at the moment of `applyThreadLocalActions` we know that the thread will not change (i.e., there are no suspensions)
      // and we safely install thread-local data.
      // so here in release function we remove the thread-local not because it was added during the preparation of write-intent,
      // but because it was installed just before write action
      writeIntentInitResult.release()
    }
  }

  /**
   * The process of obtaining pure WA happens in two steps:
   * 1. First, we acquire the write-intent permit;
   * 2. Then, we upgrade the write-intent permit into write.
   * This is needed for code that temporarily releases the write lock.
   * Since we still want to preserve the atomicity of write action, we pre-acquire the write-intent lock before write.
   */
  private data class PreparatoryWriteIntent(val permit: Permit, val needRelease: Boolean, val state: ComputationState, val listeners: List<WriteActionListener>) {
    fun release() {
      if (!(needRelease && permit is WriteIntentPermit)) {
        return
      }
      state.releaseWriteIntentPermit(permit)
    }
  }

  private suspend fun prepareWriteIntentAcquiredBeforeWriteSuspending(computationState: ComputationState): PreparatoryWriteIntent {
    val frozenListeners = prepareWriteIntentForWriteLockAcquisition(computationState, Any::class.java)
    val permit = computationState.getThisThreadPermit()

    try {
      when (permit) {
        null -> {
          val writeIntent = computationState.acquireWriteIntentPermitSuspending()
          return PreparatoryWriteIntent(writeIntent, true, computationState, frozenListeners)
        }
        else -> return gatherPreparatoryData(permit, computationState, frozenListeners)
      }
    } catch (e : Throwable) {
      endPendingWriteAction(computationState)
      throw e
    }
  }


  private fun prepareWriteIntentAcquiredBeforeWriteBlocking(computationState: ComputationState, clazz: Class<*>): PreparatoryWriteIntent {

    val frozenListeners = prepareWriteIntentForWriteLockAcquisition(computationState, clazz)

    val permit = computationState.getThisThreadPermit()

    try {
      when (permit) {
        null -> {
          val writeIntent = computationState.acquireWriteIntentPermit()
          return PreparatoryWriteIntent(writeIntent, true, computationState, frozenListeners)
        }
        else -> return gatherPreparatoryData(permit, computationState, frozenListeners)
      }
    } catch (e : Throwable) {
      endPendingWriteAction(computationState)
      throw e
    }
  }

  private fun gatherPreparatoryData(parallelizablePermit: ParallelizablePermit, state: ComputationState, frozenListeners: List<WriteActionListener>): PreparatoryWriteIntent {
    when (parallelizablePermit) {
      is ParallelizablePermit.Read -> {
        error("WriteAction can not be called from ReadAction")
      }
      is ParallelizablePermit.WriteIntent -> {
        checkWriteFromRead("Write", "Write Intent")
        return PreparatoryWriteIntent(parallelizablePermit.writeIntentPermit, false, state, frozenListeners)
      }
      is ParallelizablePermit.Write -> {
        checkWriteFromRead("Write", "Write")
        return PreparatoryWriteIntent(parallelizablePermit.writePermit, false, state, frozenListeners)
      }
    }
  }

  private fun prepareWriteIntentForWriteLockAcquisition(state: ComputationState, clazz: Class<*>): List<WriteActionListener> {
    val frozenListeners = myWriteActionListeners.doClone()
    // Read permit is incompatible
    check(state.getThisThreadPermit() !is ParallelizablePermit.Read) { "WriteAction can not be called from ReadAction" }

    // Check that write action is not disabled
    // NB: It is before all cancellations will be run via fireBeforeWriteActionStart
    // It is change for old behavior, when ProgressUtilService checked this AFTER all cancellations.
    if (myNoWriteActionCounter.get() > 0) {
      throwCannotWriteException()
    }

    handleLockAccess("write lock")

    startPendingWriteAction(state)

    if (myWriteActionsStack.isEmpty()) {
      fireBeforeWriteActionStart(frozenListeners, clazz)
    }
    return frozenListeners
  }


  private fun prepareWriteFromWriteIntentBlocking(state: ComputationState, clazz: Class<*>, preparatoryWriteIntent: PreparatoryWriteIntent): WriteLockInitResult {
    val (shouldRelease, exposedData) = try {
      when (preparatoryWriteIntent.permit) {
        is WriteIntentPermit -> {
          val exposedPermitData = processWriteLockAcquisition {
            state.upgradeWritePermit(preparatoryWriteIntent.permit)
          }
          true to exposedPermitData
        }
        is WritePermit -> false to null
        else -> error("Only WriteIntentPermit or WritePermit must be passed to this function")
      }
    }
    catch (e: Throwable) {
      endPendingWriteAction(state)
      throw e
    }
    return WriteLockInitResult(shouldRelease, preparatoryWriteIntent.listeners, state, clazz, exposedData, this)
  }

  private suspend fun prepareWriteFromWriteIntentSuspending(state: ComputationState, preparatoryWriteIntent: PreparatoryWriteIntent): WriteLockInitResult {
    val (shouldRelease, exposedData) = try {
      when (preparatoryWriteIntent.permit) {
        is WriteIntentPermit -> {
          val exposedData = processWriteLockAcquisitionSuspending {
            state.upgradeWritePermitSuspending(preparatoryWriteIntent.permit)
          }
          true to exposedData
        }
        is WritePermit -> false to null
        else -> error("Only WriteIntentPermit or WritePermit must be passed to this function")
      }
    }
    catch (e: Throwable) {
      endPendingWriteAction(state)
      throw e
    }
    return WriteLockInitResult(shouldRelease, preparatoryWriteIntent.listeners, state, Any::class.java, exposedData, this)
  }

  private fun startPendingWriteAction(state: ComputationState) {
    val stateLevel = state.level()
    myWriteActionPending.get()[stateLevel].incrementAndGet()
  }

  private fun endPendingWriteAction(state: ComputationState) {
    val stateLevel = state.level()
    myWriteActionPending.get()[stateLevel].decrementAndGet()
  }

  private data class WriteLockInitResult(
    val shouldRelease: Boolean,
    val listeners: List<WriteActionListener>,
    val state: ComputationState,
    val clazz: Class<*>,
    val exposedData: ExposedWritePermitData?,
    val support: NestedLocksThreadingSupport,
  ) {

    init {
      if (shouldRelease) {
        check(support.hack_getPublishedWriteData() == null) {
          "'upgradeWritePermit' must not be preceded by another 'upgradeWritePermit'"
        }
      }
    }

    // we use AccessToken here to remove some service stacktraces
    // DO NOT suspend inside the token application, as it touches some sensitive thread locals
    fun applyThreadLocalActions(): AccessToken {
      if (exposedData != null) {
        support.hack_setPublishedPermitData(exposedData)
        support.thisLevelPermit.set(exposedData.finalWritePermit)
      }

      support.myWriteAcquired = Thread.currentThread()
      support.endPendingWriteAction(state)

      val currentReadState = support.myTopmostReadAction.get()
      support.myTopmostReadAction.set(false)

      support.myWriteActionsStack.add(clazz)
      support.fireWriteActionStarted(listeners, clazz)
      val thread = Thread.currentThread()

      return object : AccessToken() {
        override fun finish() {
          check(thread == Thread.currentThread()) {
            "Release of write lock should happen on the same thread as it was acquired"
          }
          support.fireWriteActionFinished(listeners, clazz)
          support.myWriteActionsStack.removeLast()
          if (shouldRelease) {
            support.myWriteAcquired = null
            state.releaseWritePermit()
          }
          support.myTopmostReadAction.set(currentReadState)
          if (shouldRelease) {
            support.fireAfterWriteActionFinished(listeners, clazz)
            support.drainWriteActionFollowups()
          }
        }

      }
    }
  }


  override fun executeSuspendingWriteAction(action: () -> Unit) {
    val state = getComputationState()
    val permit = state.getThisThreadPermit()
    if (permit is ParallelizablePermit.WriteIntent) {
      action()
      return
    }

    check(permit is ParallelizablePermit.Write) {
      "Suspending write action must be called under write lock or write-intent lock"
    }
    val prevBase = myWriteStackBase
    myWriteStackBase = myWriteActionsStack.size
    myWriteAcquired = null
    val exposedPermitData = checkNotNull(hack_getPublishedWriteData()) {
      "Suspending write action was requested, but the thread did not start write action properly"
    }
    hack_setPublishedPermitData(null)
    var writePermitIndex = exposedPermitData.writePermitStack.lastIndex
    while (writePermitIndex >= 0) {
      exposedPermitData.writePermitStack[writePermitIndex--].release()
    }
    val rootWriteIntentPermit = exposedPermitData.originalWriteIntentPermit
    permit.writePermit.release()
    hack_setThisLevelPermit(rootWriteIntentPermit)
    drainWriteActionFollowups()
    try {
      action()
    }
    finally {
      myWriteLockReacquisitionListener?.beforeWriteLockReacquired()
      val newWritePermit = runSuspendMaybeConsuming(false) {
        rootWriteIntentPermit.acquireWriteActionPermit()
      }
      hack_setThisLevelPermit(newWritePermit)
      val newWritePermits = Array(exposedPermitData.writeIntentStack.size) {
        runSuspendMaybeConsuming(false) {
          exposedPermitData.writeIntentStack[it].acquireWriteActionPermit()
        }
      }
      hack_setPublishedPermitData(exposedPermitData.copy(writePermitStack = newWritePermits, finalWritePermit = newWritePermit))
      myWriteAcquired = Thread.currentThread()
      myWriteStackBase = prevBase
    }
  }

  override fun isWriteActionInProgress(): Boolean = myWriteAcquired != null


  /**
   * **DISCLAIMER: THE CONCEPT BELOW IS NOT HOW IT WORKS IN THE PLATFORM**
   *
   * In an ideal world, it would be more correct to check all write actions starting from the level of the context lock.
   * I.e., the following:
   * ```
   * writeAction {} // pending
   * modalProgress {}
   * readAction { } // does not start, because  same-level WA is pending
   * ```
   * This solution would ensure that we don't waste computation power on useless read action that will be overwritten by a same level pending write action.
   *
   * **^^^ THIS IS NOT HOW IT WORKS IN THE PLATFORM**
   *
   * Unfortunately, the IJ Platform does not follow the principle of structured concurrency. Historically, we have a lot of modal computations
   * that may depend on the non-modal read action to complete. For example, our code for saving, which may wait until previously scheduled `RefreshSession`s finish.
   * So we chose to permit ALL read actions if there is not top-level pending WA.
   */
  override fun isWriteActionPending(): Boolean {
    return myWriteActionPending.get().last().get() > 0
  }

  override fun isWriteAccessAllowed(): Boolean = myWriteAcquired == Thread.currentThread()

  override fun hasWriteAction(actionClass: Class<*>): Boolean {
    for (i in myWriteActionsStack.size - 1 downTo 0) {
      val action = myWriteActionsStack[i]
      if (actionClass == action || ReflectionUtil.isAssignable(actionClass, action)) {
        return true
      }
    }
    return false
  }

  @Deprecated("Use `runReadAction` instead")
  override fun acquireReadActionLock(): CleanupAction {
    logger.error("`ThreadingSupport.acquireReadActionLock` is deprecated and going to be removed soon. Use `runReadAction()` instead")
    val computationState = getComputationState()
    val currentPermit = computationState.getThisThreadPermit()
    if (currentPermit is ParallelizablePermit.Write) {
      throw IllegalStateException("Write Action can not request Read Access Token")
    }
    if (currentPermit is ReadPermit || currentPermit is WriteIntentPermit) {
      return { }
    }
    val capturedListener = readActionListeners
    val capturedPermit = run {
      fireBeforeReadActionStart(capturedListener, javaClass)
      val p = computationState.acquireReadPermit()
      fireReadActionStarted(capturedListener, javaClass)
      p
    }
    return {
      fireReadActionFinished(capturedListener, javaClass)
      computationState.releaseReadPermit(capturedPermit)
      fireAfterReadActionFinished(capturedListener, javaClass)
    }
  }

  @Deprecated("Use `runWriteAction`, `WriteAction.run`, or `WriteAction.compute` instead")
  override fun acquireWriteActionLock(marker: Class<*>): () -> Unit {
    logger.error("`ThreadingSupport.acquireWriteActionLock` is deprecated and going to be removed soon. Use `runWriteAction()` instead")
    val token = WriteAccessToken(marker)
    return token::finish
  }

  override fun prohibitWriteActionsInside(): () -> Unit {
    myNoWriteActionCounter.set(myNoWriteActionCounter.get() + 1)
    return {
      myNoWriteActionCounter.set(myNoWriteActionCounter.get() - 1)
    }
  }

  override fun prohibitTakingLocksInsideAndRun(action: Runnable, failSoftly: Boolean, advice: String) {
    val currentValue = myLockingProhibited.get()
    myLockingProhibited.set(failSoftly to advice)
    try {
      action.run()
    }
    finally {
      myLockingProhibited.set(currentValue)
    }
  }

  override fun allowTakingLocksInsideAndRun(action: Runnable) {
    val currentValue = myLockingProhibited.get()
    myLockingProhibited.set(null)
    try {
      action.run()
    }
    finally {
      myLockingProhibited.set(currentValue)
    }
  }

  override fun getLockingProhibitedAdvice(): String? {
    return myLockingProhibited.get()?.second
  }


  override fun isInsideUnlockedWriteIntentLock(): Boolean {
    return false
  }

  private fun <T> processWriteLockAcquisition(acquisitor: () -> T): T {
    val prevResult = myLockAcquisitionListener?.beforeWriteLockAcquired()
    try {
      return acquisitor()
    }
    finally {
      @Suppress("MEMBER_PROJECTED_OUT")
      myLockAcquisitionListener?.afterWriteLockAcquired(prevResult)
    }
  }

  private suspend fun <T> processWriteLockAcquisitionSuspending(acquisitor: suspend () -> T): T {
    val prevResult = myLockAcquisitionListener?.beforeWriteLockAcquired()
    try {
      return acquisitor()
    }
    finally {
      @Suppress("MEMBER_PROJECTED_OUT")
      myLockAcquisitionListener?.afterWriteLockAcquired(prevResult)
    }
  }


  private fun fireBeforeReadActionStart(list: List<ReadActionListener>, clazz: Class<*>) {
    list.traverse {
      it.beforeReadActionStart(clazz)
    }
  }

  private fun fireReadActionStarted(list: List<ReadActionListener>, clazz: Class<*>) {
    list.traverse {
      it.readActionStarted(clazz)
    }
  }

  private fun fireReadActionFinished(list: List<ReadActionListener>, clazz: Class<*>) {
    list.traverseBackwards {
      it.readActionFinished(clazz)
    }
  }

  private fun fireAfterReadActionFinished(list: List<ReadActionListener>, clazz: Class<*>) {
    list.traverseBackwards {
      it.afterReadActionFinished(clazz)
    }
  }

  private fun fireBeforeWriteActionStart(listeners: List<WriteActionListener>, clazz: Class<*>) {
    listeners.traverse {
      it.beforeWriteActionStart(clazz)
    }
  }

  private fun fireWriteActionStarted(listeners: List<WriteActionListener>, clazz: Class<*>) {
    listeners.traverse {
      it.writeActionStarted(clazz)
    }
  }

  private fun fireWriteActionFinished(listeners: List<WriteActionListener>, clazz: Class<*>) {
    listeners.traverseBackwards {
      it.writeActionFinished(clazz)
    }
  }

  private fun fireAfterWriteActionFinished(listeners: List<WriteActionListener>, clazz: Class<*>) {
    listeners.traverseBackwards {
      it.afterWriteActionFinished(clazz)
    }
  }

  private fun fireBeforeWriteIntentReadActionStart(listeners: List<WriteIntentReadActionListener>, clazz: Class<*>) {
    listeners.traverse {
      it.beforeWriteIntentReadActionStart(clazz)
    }
  }

  private fun fireWriteIntentActionStarted(listeners: List<WriteIntentReadActionListener>, clazz: Class<*>) {
    listeners.traverse {
      it.writeIntentReadActionStarted(clazz)
    }

  }

  private fun fireWriteIntentActionFinished(listeners: List<WriteIntentReadActionListener>, clazz: Class<*>) {
    listeners.traverseBackwards {
      it.writeIntentReadActionFinished(clazz)
    }
  }

  private fun afterWriteIntentReadActionFinished(listeners: List<WriteIntentReadActionListener>, clazz: Class<*>) {
    listeners.traverseBackwards {
      it.afterWriteIntentReadActionFinished(clazz)
    }
  }

  @Deprecated("")
  private inner class WriteAccessToken(private val clazz: Class<*>) : AccessToken() {
    val compState = getComputationState()
    val writeIntentPreparatoryData: PreparatoryWriteIntent
    val writeLockInitResult: AccessToken

    init {
      markThreadNameInStackTrace()
      writeIntentPreparatoryData = prepareWriteIntentAcquiredBeforeWriteBlocking(compState, clazz)
      writeLockInitResult = try {
        prepareWriteFromWriteIntentBlocking(compState, clazz, writeIntentPreparatoryData).applyThreadLocalActions()
      }
      catch (e: Throwable) {
        // this code can be cancelled during the acquisition of a write lock
        // we need to not forget to release the acquired write-intent lock
        writeIntentPreparatoryData.release()
        throw e
      }
    }

    override fun finish() {
      try {
        try {
          writeLockInitResult.finish()
        } finally {
          writeIntentPreparatoryData.release()
        }
      }
      finally {
        unmarkThreadNameInStackTrace()
      }
    }

    private fun markThreadNameInStackTrace() {
      val id = id()

      val thread = Thread.currentThread()
      thread.name = thread.name + id
    }

    private fun unmarkThreadNameInStackTrace() {
      val id = id()

      val thread = Thread.currentThread()
      var name = thread.name
      name = StringUtil.replace(name!!, id, "")
      thread.name = name
    }

    private fun id(): String {
      return " [WriteAccessToken]"
    }
  }

  private fun checkWriteFromRead(whatIsCalled: String, permitUsed: String) {
    if (!reportInvalidActionChains) {
      return
    }
    if (myReadActionsInThread.get() > 0) {
      val stackBottom = if (permitUsed == "Write") "Write" else "WriteIntentRead"
      logger.warn("${whatIsCalled} Action is called from Read Action with ${permitUsed} lock.\n" +
                  "Looks like there is ${stackBottom}Action -> ReadAction -> ${whatIsCalled.replace(" ", "")}Action call chain." +
                  "It is not error technically, as Read Action uses more strong lock, but looks like logical error.", Throwable())
    }
  }

  private fun throwCannotWriteException() {
    throw java.lang.IllegalStateException("Write actions are prohibited")
  }

  override fun <T> relaxPreventiveLockingActions(action: () -> T): T {
    return withThreadLocal(ignorePreventiveActions, {true}).use {
      action()
    }
  }

  override fun <T> releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack(action: () -> T): T {
    val state = getComputationState()
    val permit = state.getThisThreadPermit()
    if (permit !is ParallelizablePermit.WriteIntent) {
      throw IllegalStateException("This function expects the Write-Intent read action to be acquired")
    }
    // for now, we release only the top-level WI lock.
    // There is no evidence that this method is called in deep parallelization stacks
    state.releaseWriteIntentPermit(permit.writeIntentPermit)
    try {
      drainWriteActionFollowups()
      myWriteIntentAcquired.set(false)
      return action()
    }
    finally {
      myWriteIntentAcquired.set(true)
      // non-cancellable section here because we need to prohibit prompt cancellation of lock acquisition in this `finally`
      // otherwise the outer release in `runWriteIntentReadAction` would fail with NPE
      installThreadContext(currentThreadContext().minusKey(Job), true) {
        state.acquireWriteIntentPermit()
      }
    }
  }

  /**
   * The proof of correctness for this method should follow the path of each individual runnable.
   * 1. The runnable can be invoked in-place if there are no write actions
   * 2. The runnable can be invoked when the current write action finishes **if** the current write action finishes late enough to catch this update
   * 3. The runnable can be invoked in-place if current write already finished early enough.
   * ABA problem is irrelevant here, as the scheduled runnable will be invoked on the termination of the new write action
   */
  override fun runWhenWriteActionIsCompleted(action: () -> Unit) {
    val isWriteActionDemanded = isWriteActionPendingOrRunning()
    if (!isWriteActionDemanded) {
      return action()
    }
    synchronized(pendingWriteActionFollowup) {
      pendingWriteActionFollowup.add(action)
    }
    val isWriteActionDemanded2 = isWriteActionPendingOrRunning()
    if (!isWriteActionDemanded2) {
      drainWriteActionFollowups()
    }
  }

  private fun drainWriteActionFollowups() {
    if (isWriteActionPendingOrRunning()) {
      return
    }
    val list: ArrayList<Runnable>
    synchronized(pendingWriteActionFollowup) {
      list = ArrayList(pendingWriteActionFollowup)
      pendingWriteActionFollowup.clear()
    }
    for (runnable in list) {
      runnable.run()
    }
  }

  private fun isWriteActionPendingOrRunning(): Boolean {
    return isWriteActionPending() || myWriteAcquired != null
  }

  @OptIn(InternalCoroutinesApi::class)
  fun <T> runSuspendMaybeConsuming(tryCompensateParallelism: Boolean, block: suspend () -> T): T {
    return if (tryCompensateParallelism && readLockCompensationTimeout != -1) {
      IntellijCoroutines.runAndCompensateParallelism(readLockCompensationTimeout.toDuration(DurationUnit.MILLISECONDS)) {
        runSuspendWithWaitingConsumer(block, myLockInterceptor.get())
      }
    }
    else {
      runSuspendWithWaitingConsumer(block, myLockInterceptor.get())
    }
  }

  /**
   * Normally, management of [thisLevelPermit] should happen in acquire-release functions.
   * If something uses [hack_setThisLevelPermit], then it is a hack
   */
  @Suppress("FunctionName") // this function is for hackers
  fun hack_setThisLevelPermit(permit: Permit?) {
    thisLevelPermit.set(permit)
  }

  @Suppress("FunctionName") // this function is for hackers
  private fun hack_getPublishedWriteData(): ExposedWritePermitData? {
    return publishedPermits
  }

  @Suppress("FunctionName") // this function is for hackers
  private fun hack_setPublishedPermitData(newData: ExposedWritePermitData?) {
    publishedPermits = newData
  }

  override fun transferWriteActionAndBlock(blockingExecutor: (ThreadingSupport.RunnableWithTransferredWriteAction) -> Unit, action: Runnable) {
    val completionMarker = AtomicBoolean(false)
    val currentState = getComputationState()
    val permit = currentState.getThisThreadPermit()
    check(permit is ParallelizablePermit.Write) {
      "Attempt to transfer write action with existing permit: $permit. Write Permit is required here"
    }
    blockingExecutor(object : ThreadingSupport.RunnableWithTransferredWriteAction() {
      override fun run() {
        val currentPermit = thisLevelPermit.get()
        hack_setThisLevelPermit(permit.writePermit)
        val currentWriteThreadAcquired = myWriteAcquired
        myWriteAcquired = Thread.currentThread()
        try {
          action.run()
        }
        finally {
          myWriteAcquired = currentWriteThreadAcquired
          hack_setThisLevelPermit(currentPermit)
          completionMarker.set(true)
        }
      }
    })
    check(completionMarker.get()) {
      "The executor must run the action synchronously"
    }
  }
}


/**
 * Runs [block] and invokes [interceptor] to handle waiting if [block] does not finish in time
 */
@OptIn(InternalCoroutinesApi::class)
private fun <T> runSuspendWithWaitingConsumer(block: suspend () -> T, interceptor: PermitWaitingInterceptor?): T {
  val currentJob = currentThreadContext()[Job]
  val run = RunSuspend<T>(currentJob, interceptor)
  block.startCoroutine(run)
  return run.await()
}

private class RunSuspend<T>(val job: Job?, val interceptor: PermitWaitingInterceptor?) : Continuation<T> {
  override val context: CoroutineContext
    get() = job ?: EmptyCoroutineContext

  val resultDeferred: CompletableDeferred<T> = CompletableDeferred()

  override fun resumeWith(result: Result<T>) = synchronized(this) {
    if (result.isSuccess) {
      resultDeferred.complete(result.getOrThrow())
    }
    else {
      resultDeferred.completeExceptionally(result.exceptionOrNull()!!)
    }
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") (this as Object).notifyAll()
  }

  fun await(): T {
    if (interceptor == null) {
      synchronized(this) {
        var interrupted = false
        while (true) {
          if (resultDeferred.isCompleted) {
            if (interrupted) {
              // Restore "interrupted" flag
              Thread.currentThread().interrupt()
            }
            return resultDeferred.getOrThrow()
          }
          else {
            try {
              @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
              ((this as Object).wait())
            }
            catch (_: InterruptedException) {
              // Suppress exception or token could be lost.
              interrupted = true
            }
          }
        }
      }
    } else {
      if (!resultDeferred.isCompleted) {
        interceptor.consumer(resultDeferred)
      }
      return resultDeferred.getOrThrow() // consumer returns when `result` gets non-nullable value
    }
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> Deferred<T>.getOrThrow(): T {
  getCompletionExceptionOrNull()?.let { throw it }
  return getCompleted() // throw up failure
}

private data class PermitWaitingInterceptor(
  val consumer: (Deferred<*>) -> Unit,
)

