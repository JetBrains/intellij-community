// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.indexing.contentQueue.dev


import com.intellij.find.ngrams.TrigramIndex
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.psi.impl.cache.impl.id.IdIndex
import com.intellij.psi.stubs.StubUpdatingIndex
import com.intellij.util.ConcurrencyUtil.newNamedThreadFactory
import com.intellij.util.SystemProperties.*
import com.intellij.util.TimeoutUtil
import com.intellij.util.indexing.*
import com.intellij.util.indexing.FileIndexingResult.ApplicationMode
import com.intellij.util.indexing.contentQueue.IndexUpdateRunner.Companion.INDEXING_PARALLELIZATION
import com.intellij.util.indexing.contentQueue.IndexUpdateRunner.Companion.TRACER
import com.intellij.util.indexing.events.VfsEventsMerger
import io.opentelemetry.context.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors.newSingleThreadExecutor
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport
import java.util.function.Supplier
import kotlin.math.abs

/** Abstracts out writing of indexed files data into the actual index storages */
abstract class IndexWriter {

  /** Same as [writeAsync], but not suspended -- to call from java, in a synchronous manner */
  //TODO RC: only SameThread implementation is called from java code -- mb there is better way to allow that without
  //         trashing common interface?
  fun writeSync(fileIndexingResult: FileIndexingResult, finishCallback: () -> Unit) {
    if (preProcess(fileIndexingResult, finishCallback)) {
      return
    }

    writeChangesToIndexesSync(fileIndexingResult, finishCallback)
  }

  open suspend fun writeAsync(fileIndexingResult: FileIndexingResult, finishCallback: () -> Unit) {
    if (preProcess(fileIndexingResult, finishCallback)) {
      return
    }

    writeChangesToIndexes(fileIndexingResult, finishCallback)
  }

  /** Synchronous pre-processing part of index changes applying */
  protected fun preProcess(fileIndexingResult: FileIndexingResult, finishCallback: () -> Unit): Boolean {
    val startedAtNs = System.nanoTime()

    if (fileIndexingResult.removeDataFromIndicesForFile()) {
      fileIndexingResult.indexImpl().removeDataFromIndicesForFile(
        fileIndexingResult.fileId(),
        fileIndexingResult.file(),
        "invalid_or_large_file_or_indexing_delete_request"
      )
    }

    if (fileIndexingResult.appliers().isEmpty() && fileIndexingResult.removers().isEmpty()) {
      fileIndexingResult.markFileProcessed(true) { "empty appliers" }
      fileIndexingResult.addApplicationTimeNanos(System.nanoTime() - startedAtNs)
      finishCallback()
      return true
    }

    fileIndexingResult.addApplicationTimeNanos(System.nanoTime() - startedAtNs)
    return false
  }

  protected abstract suspend fun writeChangesToIndexes(
    fileIndexingResult: FileIndexingResult,
    finishCallback: () -> Unit,
  )

  protected abstract fun writeChangesToIndexesSync(
    fileIndexingResult: FileIndexingResult,
    finishCallback: () -> Unit,
  )

  companion object {
    private val LOG = Logger.getInstance(IndexWriter::class.java)

    /**
     * When disabled, each indexing thread writes its produced updates to the indexes by itself.
     * When enabled, indexing threads are preparing updates and submitting writing to the dedicated threads:
     * IdIndex, TrigramIndex, Stubs and the rest.
     * By default, it is enabled for multiprocessor systems, where we can benefit from the parallel processing.
     *
     * BEWARE: The idea is not only to parallelize index writing, but to _limit_ the parallelism -- currently,
     * each particular index writing is protected by lock, hence >1 thread trying to update the same index only
     * increases contention on the lock, so throughput likely decreases, not increases.
     * This is why we 'assign' a worker/thread to a specific set of the indexes and don't use >1 thread/worker per index
     */
    @JvmField
    val WRITE_INDEXES_ON_SEPARATE_THREAD = getBooleanProperty("idea.write.indexes.on.separate.thread",
                                                              UnindexedFilesUpdater.getMaxNumberOfIndexingThreads() > 5)

    private val PARALLEL_WRITER_IMPL: String? = System.getProperty("IndexWriter.parallel.impl")

    private val defaultParallelWriter: ParallelIndexWriter = when (PARALLEL_WRITER_IMPL) {
      "FakeIndexWriter" -> FakeIndexWriter
      "ApplyViaCoroutinesWriter" -> ApplyViaCoroutinesWriter()
      "LegacyMultiThreadedIndexWriter" -> LegacyMultiThreadedIndexWriter()

      "MultiThreadedWithSuspendIndexWriter", null -> MultiThreadedWithSuspendIndexWriter()
      else -> {
        LOG.info("Unrecognized value [IndexWriter.parallel.impl='$PARALLEL_WRITER_IMPL'] -- use default MultiThreadedWithSuspendIndexWriter")
        MultiThreadedWithSuspendIndexWriter()
      }
    }

    init {
      LOG.info("Use $defaultParallelWriter as (parallel) index writer implementation")
    }

    @JvmStatic
    fun suitableWriter(applicationMode: ApplicationMode, forceWriteSynchronously: Boolean = false): IndexWriter {
      if (forceWriteSynchronously) {
        return SameThreadIndexWriter
      }
      if (!WRITE_INDEXES_ON_SEPARATE_THREAD) {
        return SameThreadIndexWriter
      }
      return when (applicationMode) {
        ApplicationMode.SameThreadOutsideReadLock -> SameThreadIndexWriter
        ApplicationMode.AnotherThread -> defaultParallelWriter()
      }
    }

    @JvmStatic
    fun defaultParallelWriter(): ParallelIndexWriter = defaultParallelWriter
  }
}

/** Applies all the changes to the indexes on the calling thread */
object SameThreadIndexWriter : IndexWriter() {

  override fun writeChangesToIndexesSync(fileIndexingResult: FileIndexingResult, finishCallback: () -> Unit) {
    applyChangesToIndexes(fileIndexingResult, finishCallback)
  }

  override suspend fun writeChangesToIndexes(fileIndexingResult: FileIndexingResult, finishCallback: () -> Unit) {
    applyChangesToIndexes(fileIndexingResult, finishCallback)
  }

  private fun applyChangesToIndexes(
    fileIndexingResult: FileIndexingResult,
    finishCallback: () -> Unit,
  ) {

    val startedAtNs = System.nanoTime()
    var allModificationsSuccessful = true
    try {
      for (applier in fileIndexingResult.appliers()) {
        val applied = applier.apply()
        allModificationsSuccessful = allModificationsSuccessful && applied
        if (!applied) {
          VfsEventsMerger.tryLog("NOT_APPLIED", fileIndexingResult.file()) { applier.toString() }
        }
      }

      for (remover in fileIndexingResult.removers()) {
        val removed = remover.remove()
        allModificationsSuccessful = allModificationsSuccessful && removed
        if (!removed) {
          VfsEventsMerger.tryLog("NOT_REMOVED", fileIndexingResult.file()) { remover.toString() }
        }
      }
    }
    catch (pce: ProcessCanceledException) {
      allModificationsSuccessful = false
      if (FileBasedIndexEx.TRACE_STUB_INDEX_UPDATES) {
        Logger.getInstance(FileIndexingResult::class.java)
          .infoWithDebug("applyModifications interrupted,fileId=${fileIndexingResult.fileId()},$pce", RuntimeException(pce))
      }
      throw pce
    }
    catch (t: Throwable) {
      allModificationsSuccessful = false
      Logger.getInstance(FileIndexingResult::class.java)
        .warn("applyModifications interrupted,fileId=${fileIndexingResult.fileId()},$t",
              if (FileBasedIndexEx.TRACE_STUB_INDEX_UPDATES) t else null)
      throw t
    }
    finally {
      val debugString = Supplier<String> {
        " updated_indexes=" + fileIndexingResult.statistics().perIndexerEvaluateIndexValueTimes.keys +
        " deleted_indexes=" + fileIndexingResult.statistics().perIndexerEvaluatingIndexValueRemoversTimes.keys
      }

      fileIndexingResult.markFileProcessed(allModificationsSuccessful, debugString)
      fileIndexingResult.addApplicationTimeNanos(System.nanoTime() - startedAtNs)

      finishCallback()
    }
  }
}

/* ================ parallelized IndexWriter implementations: ====================================================*/

/** Base writers are for: IdIndex, Stubs and Trigrams  */
private val BASE_WRITERS_NUMBER = 3

/** Aux writers used to write other indexes in parallel. But each index is 100% written on the same thread.  */
private val AUX_WRITERS_NUMBER = 1

/** Total number of index writing threads  */
val TOTAL_WRITERS_NUMBER = BASE_WRITERS_NUMBER + AUX_WRITERS_NUMBER

abstract class ParallelIndexWriter(val workersCount: Int = TOTAL_WRITERS_NUMBER) : IndexWriter() {

  override fun writeChangesToIndexesSync(fileIndexingResult: FileIndexingResult, finishCallback: () -> Unit) {
    throw UnsupportedOperationException("writeChangesToIndexesSync is not supported")
    //TODO implement like this? But it doesn't really needed: only SameThread implementation is really called from java code
    //runBlockingCancellable { writeChangesToIndexes(fileIndexingResult, finishCallback) }
  }

  /** Waiting till current async tasks are finished */
  abstract fun waitCurrentIndexingToFinish()

  /**
   * Monitoring: provides information about time spent on writing (applying) index changes by specific worker, if any,
   * in the units asked.
   * Implementation is free to not provide this information -- in which case it must return -1
   */
  abstract fun totalTimeSpentWriting(unit: TimeUnit, workerNo: Int): Long

  /**
   * Monitoring: provides information about time spent on writing (applying) index changes by specific worker, if any,
   * in the units asked.
   * Implementation is free to not provide this information -- in which case it must return -1
   */
  abstract fun totalTimeIndexersSlept(unit: TimeUnit): Long

  /** @return number of writes queued at this moment. (Contrary to other monitoring methods, this is not an aggregate value) */
  open fun writesQueued(): Int  = 0

  /**
   * @return worker index for indexId, in [0..workersCount).
   * Allow partitioning indexes writing to different threads to avoid concurrency.
   * We may add aux executors if necessary
   */
  protected fun workerIndexFor(indexId: IndexId<*, *>): Int {
    if (indexId === IdIndex.NAME) {
      return 0
    }
    else if (indexId === StubUpdatingIndex.INDEX_ID) {
      return 1
    }
    else if (indexId === TrigramIndex.INDEX_ID) {
      return 2
    }
    return if (AUX_WRITERS_NUMBER == 1)
      BASE_WRITERS_NUMBER
    else
      BASE_WRITERS_NUMBER + abs(indexId.name.hashCode()) % AUX_WRITERS_NUMBER
  }
}


/**
 * Distribute the writing to N+1 threads (single-threaded [com.intellij.execution.Executor]s, really),
 * for the N heaviest indexes, +1 for all other indexes combined.
 * Then writing queue is too large, it parks the thread that invokes [writeChangesToIndexes] until the queue
 * size drops down again.
 */
class LegacyMultiThreadedIndexWriter(workersCount: Int = TOTAL_WRITERS_NUMBER) : ParallelIndexWriter(workersCount) {
  companion object {
    private val LOG: Logger = Logger.getInstance(LegacyMultiThreadedIndexWriter::class.java)
  }


  /** Max number of queued updates per indexing thread, after which one indexing thread is going to sleep until the queue is shrunk.  */
  private val MAX_ALLOWED_WRITES_IN_QUEUE_PER_INDEXER: Int = getIntProperty("IndexUpdateWriter.MAX_ALLOWED_WRITES_IN_QUEUE_PER_INDEXER",
                                                                            100)

  /** Calibrated on indexing IDEA project: median write time for a single index entry.  */
  private val EXPECTED_SINGLE_WRITE_TIME_NS: Long = getLongProperty("IndexUpdateWriter.EXPECTED_SINGLE_WRITE_TIME_NS", 2500)

  /** Time (in milliseconds) we are waiting writers to finish their job and shutdown.  */
  private val WRITERS_SHUTDOWN_WAITING_TIME_MS: Long = 10000


  /** Number of asynchronous updates scheduled by [.scheduleIndexWriting] (total, across all writing pools)  */
  private val indexWritesQueued: AtomicInteger = AtomicInteger()

  /** Number of currently sleeping indexers, because of too large updates queue  */
  private val sleepingIndexers: AtomicInteger = AtomicInteger()

  private var writers: List<ExecutorService>

  init {
    val pool = mutableListOf(
      createExecutorForIndexWriting("IdIndex Writer"),
      createExecutorForIndexWriting("Stubs Writer"),
      createExecutorForIndexWriting("Trigram Writer")
    )
    repeat(AUX_WRITERS_NUMBER) { writerNo ->
      pool.add(createExecutorForIndexWriting("Aux Index Writer #${writerNo + 1}"))
    }

    check(pool.size == TOTAL_WRITERS_NUMBER) { "pool.size(=${pool.size}) must be == TOTAL_WRITERS_NUMBER(=$TOTAL_WRITERS_NUMBER)" }

    writers = pool
  }

  /* ================= monitoring fields ======================================================== */

  /** Total time (nanoseconds) spent on index writing, since app start, per worker  */
  private val totalTimeSpentOnIndexWritingNs: Array<AtomicLong> = Array(workersCount) {
    AtomicLong()
  }

  /** Total time (nanoseconds) indexers have slept while the writers queue was too long  */
  private val totalTimeSleptNs: AtomicLong = AtomicLong()


  override suspend fun writeChangesToIndexes(fileIndexingResult: FileIndexingResult, finishCallback: () -> Unit) {
    val startedAtNs = System.nanoTime()

    val workersToSchedule = BitSet(workersCount)

    for (applier in fileIndexingResult.appliers()) {
      workersToSchedule.set(workerIndexFor(applier.indexId))
    }

    for (remover in fileIndexingResult.removers()) {
      workersToSchedule.set(workerIndexFor(remover.indexId))
    }

    fileIndexingResult.addApplicationTimeNanos(System.nanoTime() - startedAtNs) //other parts will be added inside applyModifications()
    // Schedule appliers to dedicated executors
    val updatesLeftCounter = AtomicInteger(workersToSchedule.cardinality())
    val otelTelemetryContext = Context.current()
    for (executorIndex in 0 until workersCount) {
      if (workersToSchedule[executorIndex]) {
        //Schedule applyModifications() on all the writers that are applicable. Inside the method each
        // SingleIndexValueApplier inside decides for itself on which writer it is ready to run.
        scheduleIndexWriting(executorIndex) {
          otelTelemetryContext.makeCurrent().use {
            TRACER.spanBuilder("applyModificationsToIndex").setAttribute("i", executorIndex.toLong()).use {

              applyModificationsInExecutor(fileIndexingResult, executorIndex, updatesLeftCounter, finishCallback)

            }
          }
        }
      }
    }
  }

  private fun applyModificationsInExecutor(
    fileIndexingResult: FileIndexingResult,
    executorIndex: Int,
    updatesLeftCounter: AtomicInteger,
    finishCallback: () -> Unit,
  ) {

    val startedAtNs = System.nanoTime()
    var allModificationsSuccessful = true
    try {
      for (applier in fileIndexingResult.appliers()) {
        if (executorIndex == workerIndexFor(applier.indexId)) {
          val applied = applier.apply()
          allModificationsSuccessful = allModificationsSuccessful && applied
          if (!applied) {
            VfsEventsMerger.tryLog("NOT_APPLIED", fileIndexingResult.file()) { applier.toString() }
          }
        }
      }

      for (remover in fileIndexingResult.removers()) {
        if (executorIndex == workerIndexFor(remover.indexId)) {
          val removed = remover.remove()
          allModificationsSuccessful = allModificationsSuccessful && removed
          if (!removed) {
            VfsEventsMerger.tryLog("NOT_REMOVED", fileIndexingResult.file()) { remover.toString() }
          }
        }
      }
    }
    catch (pce: ProcessCanceledException) {
      allModificationsSuccessful = false
      if (FileBasedIndexEx.TRACE_STUB_INDEX_UPDATES) {
        Logger.getInstance(FileIndexingResult::class.java)
          .infoWithDebug("applyModifications interrupted,fileId=${fileIndexingResult.fileId()},$pce", RuntimeException(pce))
      }
      throw pce
    }
    catch (t: Throwable) {
      allModificationsSuccessful = false
      Logger.getInstance(FileIndexingResult::class.java)
        .warn("applyModifications interrupted,fileId=${fileIndexingResult.fileId()},$t",
              if (FileBasedIndexEx.TRACE_STUB_INDEX_UPDATES) t else null)
      throw t
    }
    finally {
      val lastOrOnlyInvocationForFile = updatesLeftCounter.decrementAndGet() == 0
      val debugString = Supplier<String> {
        " updated_indexes=" + fileIndexingResult.statistics().perIndexerEvaluateIndexValueTimes.keys +
        " deleted_indexes=" + fileIndexingResult.statistics().perIndexerEvaluatingIndexValueRemoversTimes.keys
      }

      if (lastOrOnlyInvocationForFile) {
        fileIndexingResult.markFileProcessed(allModificationsSuccessful, debugString)
      }
      else {
        VfsEventsMerger.tryLog("HAS_MORE_MODIFICATIONS", fileIndexingResult.file(), debugString)
      }

      fileIndexingResult.addApplicationTimeNanos(System.nanoTime() - startedAtNs)

      if (lastOrOnlyInvocationForFile) {
        finishCallback()
      }
    }
  }

  private fun createExecutorForIndexWriting(name: String): ExecutorService =
    newSingleThreadExecutor(newNamedThreadFactory(name))

  private fun scheduleIndexWriting(writerIndex: Int, runnable: Runnable) {
    indexWritesQueued.incrementAndGet()
    sleepIfWriterQueueLarge(INDEXING_PARALLELIZATION)

    writers[writerIndex].execute {
      val startedAtNs = System.nanoTime()
      try {
        ProgressManager.getInstance().executeNonCancelableSection(runnable)
      }
      finally {
        indexWritesQueued.decrementAndGet()

        val elapsedNs = System.nanoTime() - startedAtNs
        totalTimeSpentOnIndexWritingNs[writerIndex].addAndGet(elapsedNs)
      }
    }
  }

  private fun sleepIfWriterQueueLarge(numberOfIndexingWorkers: Int) {
    val currentlySleeping = sleepingIndexers.get()
    val couldBeSleeping = currentlySleeping + 1
    val writesInQueueToSleep = MAX_ALLOWED_WRITES_IN_QUEUE_PER_INDEXER * (numberOfIndexingWorkers + couldBeSleeping)
    val writesInQueue = indexWritesQueued.get()
    //TODO RC: why we don't repeat the CAS below if it fails?
    if (writesInQueue > writesInQueueToSleep && sleepingIndexers.compareAndSet(currentlySleeping, couldBeSleeping)) {
      val writesToWakeUp = writesInQueueToSleep - MAX_ALLOWED_WRITES_IN_QUEUE_PER_INDEXER
      LOG.debug("Sleeping indexer: ", couldBeSleeping, " of ", numberOfIndexingWorkers, "; writes queued: ", writesInQueue,
                "; wake up when queue shrinks to ", writesToWakeUp)
      //TODO RC: EXPECTED_SINGLE_WRITE_TIME_NS should be dynamically adjusted to actual value, not fixed
      val napTimeNs = MAX_ALLOWED_WRITES_IN_QUEUE_PER_INDEXER * EXPECTED_SINGLE_WRITE_TIME_NS
      try {
        val sleptNs = sleepUntilUpdatesQueueIsShrunk(writesToWakeUp, napTimeNs)
        LOG.debug("Waking indexer ", sleepingIndexers.get(), " of ", numberOfIndexingWorkers, " by ", indexWritesQueued.get(),
                  " updates in queue, should have wake up on ", writesToWakeUp,
                  "; slept for ", sleptNs, " ns")
        totalTimeSleptNs.addAndGet(sleptNs)
      }
      finally {
        sleepingIndexers.decrementAndGet()
      }
    }
  }


  /**
   * Waiting till index writing threads finish their jobs.
   *
   * @see .WRITERS_SHUTDOWN_WAITING_TIME_MS
   */
  override fun waitCurrentIndexingToFinish() {
    if (writers.isEmpty()) {
      return
    }

    val futures: MutableList<Future<*>> = ArrayList<Future<*>>(writers.size)
    writers.forEach { writer -> futures.add(writer.submit(EmptyRunnable.getInstance())) }

    val startTime = System.currentTimeMillis()
    while (!futures.isEmpty()) {
      val iterator = futures.iterator()
      while (iterator.hasNext()) {
        val future = iterator.next()
        if (future.isDone) {
          iterator.remove()
        }
      }
      TimeoutUtil.sleep(10)
      if (System.currentTimeMillis() - startTime > WRITERS_SHUTDOWN_WAITING_TIME_MS) {
        val queueSize = indexWritesQueued.get()
        val errorMessage = "Failed to shutdown index writers, queue size: $queueSize; executors active: $futures"
        if (queueSize == 0) {
          LOG.warn(errorMessage)
        }
        else {
          LOG.error(errorMessage)
        }
        return
      }
    }
  }


  /**
   * Puts the indexing thread to the sleep until the queue of updates is shrunk enough to increase the number of indexing threads.
   * To balance the load better, each next sleeping indexer checks for the queue more frequently.
   */
  private fun sleepUntilUpdatesQueueIsShrunk(writesToWakeUp: Int, napTimeNs: Long): Long {
    val sleepStart = System.nanoTime()
    var iterationNo = 1
    while (writesToWakeUp < indexWritesQueued.get()) {
      //TODO RC: why increase backoff-time with iteration#? It is more natural to scale backoff-time as
      //         (INDEX_WRITES_QUEUED - writesToWakeUp) * EXPECTED_SINGLE_WRITE_TIME_NS
      LockSupport.parkNanos(napTimeNs * iterationNo) //=linear backoff
      iterationNo++
    }
    return (System.nanoTime() - sleepStart)
  }

  //======================== metrics accessors: ==================================================

  override fun totalTimeSpentWriting(unit: TimeUnit, workerNo: Int): Long {
    return unit.convert(totalTimeSpentOnIndexWritingNs[workerNo].get(), NANOSECONDS)
  }


  override fun totalTimeIndexersSlept(unit: TimeUnit): Long {
    return unit.convert(totalTimeSleptNs.get(), NANOSECONDS)
  }
}

/**
 * Distribute the writing to N+1 threads (single-threaded [com.intellij.execution.Executor]s, really),
 * for the N heaviest indexes, +1 for all other indexes combined.
 * Differs from [LegacyMultiThreadedIndexWriter] is that it uses suspend for backpressure, instead of parking:
 * i.e. then writing queue is too large, it suspends the coroutine that invokes [writeChangesToIndexes] until
 * the queue size drops down.
 */
class MultiThreadedWithSuspendIndexWriter(workersCount: Int = TOTAL_WRITERS_NUMBER) : ParallelIndexWriter(workersCount) {
  companion object {
    private val LOG: Logger = Logger.getInstance(LegacyMultiThreadedIndexWriter::class.java)
  }


  /** Max number of queued updates per indexing thread, after which one indexing thread is going to sleep until the queue is shrunk.  */
  private val MAX_ALLOWED_WRITES_IN_QUEUE_PER_INDEXER: Int = getIntProperty("IndexUpdateWriter.MAX_ALLOWED_WRITES_IN_QUEUE_PER_INDEXER", 100)

  /** Calibrated on indexing IDEA project: median write time for a single index entry.  */
  private val EXPECTED_SINGLE_WRITE_TIME_NS: Long = getLongProperty("IndexUpdateWriter.EXPECTED_SINGLE_WRITE_TIME_NS", 2500)

  /** Time (in milliseconds) we are waiting writers to finish their job and shutdown.  */
  private val WRITERS_SHUTDOWN_WAITING_TIME_MS: Long = 10000


  /** Number of asynchronous updates scheduled by [.scheduleIndexWriting] (total, across all writing pools)  */
  private val indexWritesQueued: AtomicInteger = AtomicInteger()

  /** Number of currently sleeping indexers, because of too large updates queue  */
  private val sleepingIndexers: AtomicInteger = AtomicInteger()

  private var writers: List<ExecutorService>

  init {
    val pool = mutableListOf(
      createWorker("IdIndex Writer"),
      createWorker("Stubs Writer"),
      createWorker("Trigram Writer")
    )
    repeat(AUX_WRITERS_NUMBER) { writerNo ->
      pool.add(createWorker("Aux Index Writer #${writerNo + 1}"))
    }

    check(pool.size == TOTAL_WRITERS_NUMBER) { "pool.size(=${pool.size}) must be == TOTAL_WRITERS_NUMBER(=$TOTAL_WRITERS_NUMBER)" }

    writers = pool
  }

  private fun createWorker(name: String): ExecutorService {
    //ThreadPoolExecutor(nThreads, nThreads,
    //                   0L, TimeUnit.MILLISECONDS,
    //                   LinkedBlockingQueue<Runnable>(),
    //                   newNamedThreadFactory(name))
    return newSingleThreadExecutor(newNamedThreadFactory(name))
  }

  /* ================= monitoring fields ======================================================== */

  /** Total time (nanoseconds) spent on index writing, since app start, per worker  */
  private val totalTimeSpentOnIndexWritingNs: Array<AtomicLong> = Array(workersCount) {
    AtomicLong()
  }

  /** Total time (nanoseconds) indexers have slept while the writers queue was too long  */
  private val totalTimeSleptNs: AtomicLong = AtomicLong()


  override suspend fun writeChangesToIndexes(fileIndexingResult: FileIndexingResult, finishCallback: () -> Unit) {
    val startedAtNs = System.nanoTime()

    val workersToSchedule = BitSet(workersCount)

    for (applier in fileIndexingResult.appliers()) {
      workersToSchedule.set(workerIndexFor(applier.indexId))
    }

    for (remover in fileIndexingResult.removers()) {
      workersToSchedule.set(workerIndexFor(remover.indexId))
    }

    fileIndexingResult.addApplicationTimeNanos(System.nanoTime() - startedAtNs) //other parts will be added inside applyModifications()

    // Schedule appliers to dedicated executors
    val updatesLeftCounter = AtomicInteger(workersToSchedule.cardinality())
    val otelTelemetryContext = Context.current()
    for (executorIndex in 0 until workersCount) {
      if (workersToSchedule[executorIndex]) {
        //Schedule applyModifications() on all the writers that are applicable.
        // Inside the method each SingleIndexValueApplier decided on which writer it is ready to run.
        scheduleIndexWriting(executorIndex) {
          otelTelemetryContext.makeCurrent().use {
            TRACER.spanBuilder("applyModificationsToIndex").setAttribute("i", executorIndex.toLong()).use {

              applyModificationsInExecutor(fileIndexingResult, executorIndex, updatesLeftCounter, finishCallback)

            }
          }
        }
      }
    }
  }

  private fun applyModificationsInExecutor(
    fileIndexingResult: FileIndexingResult,
    executorIndex: Int,
    updatesLeftCounter: AtomicInteger,
    finishCallback: () -> Unit,
  ) {

    val startedAtNs = System.nanoTime()
    var allModificationsSuccessful = true
    try {
      for (applier in fileIndexingResult.appliers()) {
        if (executorIndex == workerIndexFor(applier.indexId)) {
          val applied = applier.apply()
          allModificationsSuccessful = allModificationsSuccessful && applied
          if (!applied) {
            VfsEventsMerger.tryLog("NOT_APPLIED", fileIndexingResult.file()) { applier.toString() }
          }
        }
      }

      for (remover in fileIndexingResult.removers()) {
        if (executorIndex == workerIndexFor(remover.indexId)) {
          val removed = remover.remove()
          allModificationsSuccessful = allModificationsSuccessful && removed
          if (!removed) {
            VfsEventsMerger.tryLog("NOT_REMOVED", fileIndexingResult.file()) { remover.toString() }
          }
        }
      }
    }
    catch (pce: ProcessCanceledException) {
      allModificationsSuccessful = false
      if (FileBasedIndexEx.TRACE_STUB_INDEX_UPDATES) {
        Logger.getInstance(FileIndexingResult::class.java)
          .infoWithDebug("applyModifications interrupted,fileId=${fileIndexingResult.fileId()},$pce", RuntimeException(pce))
      }
      throw pce
    }
    catch (t: Throwable) {
      allModificationsSuccessful = false
      Logger.getInstance(FileIndexingResult::class.java)
        .warn("applyModifications interrupted,fileId=${fileIndexingResult.fileId()},$t",
              if (FileBasedIndexEx.TRACE_STUB_INDEX_UPDATES) t else null)
      throw t
    }
    finally {
      val lastOrOnlyInvocationForFile = updatesLeftCounter.decrementAndGet() == 0
      val debugString = {
        " updated_indexes=" + fileIndexingResult.statistics().perIndexerEvaluateIndexValueTimes.keys +
        " deleted_indexes=" + fileIndexingResult.statistics().perIndexerEvaluatingIndexValueRemoversTimes.keys
      }

      if (lastOrOnlyInvocationForFile) {
        fileIndexingResult.markFileProcessed(allModificationsSuccessful, debugString)
      }
      else {
        VfsEventsMerger.tryLog("HAS_MORE_MODIFICATIONS", fileIndexingResult.file(), debugString)
      }

      fileIndexingResult.addApplicationTimeNanos(System.nanoTime() - startedAtNs)

      if (lastOrOnlyInvocationForFile) {
        finishCallback()
      }
    }
  }

  private suspend fun scheduleIndexWriting(writerIndex: Int, runnable: Runnable) {
    indexWritesQueued.incrementAndGet()
    suspendIfWriterQueueLarge(INDEXING_PARALLELIZATION)

    writers[writerIndex].execute {
      val startedAtNs = System.nanoTime()
      try {
        ProgressManager.getInstance().executeNonCancelableSection(runnable)
      }
      finally {
        indexWritesQueued.decrementAndGet()

        val elapsedNs = System.nanoTime() - startedAtNs
        totalTimeSpentOnIndexWritingNs[writerIndex].addAndGet(elapsedNs)
      }
    }
  }

  private suspend fun suspendIfWriterQueueLarge(numberOfIndexingWorkers: Int) {
    val currentlySleeping = sleepingIndexers.get()
    val couldBeSleeping = currentlySleeping + 1
    val writesInQueueToSleep = MAX_ALLOWED_WRITES_IN_QUEUE_PER_INDEXER * (numberOfIndexingWorkers + couldBeSleeping)
    val writesInQueue = indexWritesQueued.get()
    //TODO RC: why we don't repeat the CAS below if it fails?
    if (writesInQueue > writesInQueueToSleep && sleepingIndexers.compareAndSet(currentlySleeping, couldBeSleeping)) {
      val writesToWakeUp = writesInQueueToSleep - MAX_ALLOWED_WRITES_IN_QUEUE_PER_INDEXER
      if (LOG.isDebugEnabled) {
        LOG.debug("Sleeping indexer: ", couldBeSleeping, " of ", numberOfIndexingWorkers, "; writes queued: ", writesInQueue,
                  "; wake up when queue shrinks to ", writesToWakeUp)
      }
      //TODO RC: EXPECTED_SINGLE_WRITE_TIME_NS should be dynamically adjusted to actual value, not fixed
      val napTimeNs = MAX_ALLOWED_WRITES_IN_QUEUE_PER_INDEXER * EXPECTED_SINGLE_WRITE_TIME_NS
      try {
        val sleptNs = suspendUntilUpdatesQueueIsShrunk(writesToWakeUp, napTimeNs)
        if (LOG.isDebugEnabled) {
          LOG.debug("Waking indexer ", sleepingIndexers.get(), " of ", numberOfIndexingWorkers, " by ", indexWritesQueued.get(),
                    " updates in queue, should have wake up on ", writesToWakeUp,
                    "; slept for ", sleptNs, " ns")
        }
        totalTimeSleptNs.addAndGet(sleptNs)
      }
      finally {
        sleepingIndexers.decrementAndGet()
      }
    }
  }


  /**
   * Waiting till index writing threads finish their jobs.
   *
   * @see .WRITERS_SHUTDOWN_WAITING_TIME_MS
   */
  override fun waitCurrentIndexingToFinish() {
    if (writers.isEmpty()) {
      return
    }

    val futures: MutableList<Future<*>> = ArrayList<Future<*>>(writers.size)
    writers.forEach { writer -> futures.add(writer.submit(EmptyRunnable.getInstance())) }

    val startTime = System.currentTimeMillis()
    while (!futures.isEmpty()) {
      val iterator = futures.iterator()
      while (iterator.hasNext()) {
        val future = iterator.next()
        if (future.isDone) {
          iterator.remove()
        }
      }
      TimeoutUtil.sleep(10)
      if (System.currentTimeMillis() - startTime > WRITERS_SHUTDOWN_WAITING_TIME_MS) {
        val queueSize = indexWritesQueued.get()
        val errorMessage = "Failed to shutdown index writers, queue size: $queueSize; executors active: $futures"
        if (queueSize == 0) {
          LOG.warn(errorMessage)
        }
        else {
          LOG.error(errorMessage)
        }
        return
      }
    }
  }


  /**
   * Suspends the indexing thread until the queue of updates is shrunk enough to increase the number of indexing
   * threads.
   * To balance the load better, each next sleeping indexer checks for the queue more frequently.
   */
  private suspend fun suspendUntilUpdatesQueueIsShrunk(writesToWakeUp: Int, napTimeNs: Long): Long {
    val sleepStartedAtNs = System.nanoTime()
    var iterationNo = 1
    while (writesToWakeUp < indexWritesQueued.get()) {
      //TODO RC: why increase backoff-time with iteration#? It is more natural to scale backoff-time as
      //         (INDEX_WRITES_QUEUED - writesToWakeUp) * EXPECTED_SINGLE_WRITE_TIME_NS
      val delayNs = napTimeNs * iterationNo //=linear backoff
      if (delayNs < 1_000_000) {
        yield()
      }
      else {
        delay(delayNs / 1_000_000)
      }
      iterationNo++
    }
    return (System.nanoTime() - sleepStartedAtNs)
  }

  //======================== metrics accessors: ==================================================

  override fun totalTimeSpentWriting(unit: TimeUnit, workerNo: Int): Long {
    return unit.convert(totalTimeSpentOnIndexWritingNs[workerNo].get(), NANOSECONDS)
  }

  override fun totalTimeIndexersSlept(unit: TimeUnit): Long {
    return unit.convert(totalTimeSleptNs.get(), NANOSECONDS)
  }

  override fun writesQueued(): Int = indexWritesQueued.get()
}


@OptIn(DelicateCoroutinesApi::class)
class ApplyViaCoroutinesWriter(workersCount: Int = TOTAL_WRITERS_NUMBER) : ParallelIndexWriter(workersCount) {
  companion object {
    private val LOG = Logger.getInstance(ApplyViaCoroutinesWriter::class.java)
  }

  //TODO RC: limit _total_ number of tasks across all coroutines?
  //         Right now I tend to think this is not really needed: if index_1 channel is overloaded, than all the
  //         indexers coroutines that generate the changes to apply to index_1 are automatically suspended (on
  //         channel.send() call), but it shouldn't prevent say index_5 indexers to index the files, and generate
  //         changes to be applied by index_5 -- as long, as index_5 channel is not overloaded. I.e. per-channel
  //         task limit is really more natural than total tasks limit, and allows for more parallelism.
  //         ...In practice, I don't expect this possibility for more parallelism to be realized, though. This is
  //         because of the structure of the indexers currently in play: we have 3 Super-Heavy-Weight indexes
  //         (Id, Trigram, Stub), which is applicable to almost all the files -- and 100s of relatively lightweight
  //         indexes, that are applicable to a subset of all the file only. Which means that if any of heavy-weight
  //         indexes' channel is overloaded (and those channels are the channels most likely to be overloaded) then
  //         almost every file indexing will be suspended because of attempt to send to one of those channels
  private val channels: Array<Channel<() -> Unit>> = Array(workersCount) {
    Channel(1024)
  }

  /** Total time (nanoseconds) spent on index writing, since app start, per worker  */
  private val totalTimeSpentOnIndexWritingNs: Array<AtomicLong> = Array(workersCount) {
    AtomicLong()
  }

  init {
    for ((workerIndex, channel) in channels.withIndex()) {
      //TODO RC: initialize the Writer in a container, and use supplied scope instead of GlobalScope?
      GlobalScope.launch(Dispatchers.IO + CoroutineName("IndexWriter(#$workerIndex)")) {
        channel.consumeEach { task ->
          val startedAtNs = System.nanoTime()
          try {
            withContext(NonCancellable) {
              task()
            }
          }
          catch (e: Throwable) {
            LOG.error("Error while applying changes to index", e)
          }
          finally {
            totalTimeSpentOnIndexWritingNs[workerIndex].addAndGet(System.nanoTime() - startedAtNs)
          }
        }
      }
    }
  }

  override suspend fun writeChangesToIndexes(fileIndexingResult: FileIndexingResult, finishCallback: () -> Unit) {
    val startedAtNs = System.nanoTime()

    val workersToSchedule = BitSet(workersCount)
    for (applier in fileIndexingResult.appliers()) {
      workersToSchedule.set(workerIndexFor(applier.indexId))
    }
    for (remover in fileIndexingResult.removers()) {
      workersToSchedule.set(workerIndexFor(remover.indexId))
    }

    fileIndexingResult.addApplicationTimeNanos(System.nanoTime() - startedAtNs) //other parts will be added inside applyModifications()
    // Schedule appliers to dedicated coroutines:
    val updatesLeftCounter = AtomicInteger(workersToSchedule.cardinality())
    val otelTelemetryContext = Context.current()
    for (workerIndex in 0 until workersCount) {
      if (workersToSchedule[workerIndex]) {
        //Schedule applyModifications() on all the writers that are applicable. Inside the method each
        // SingleIndexValueApplier/Remover decides for itself on which writer it is ready to run.
        channels[workerIndex].send {
          otelTelemetryContext.makeCurrent().use {
            TRACER.spanBuilder("applyModificationsToIndex").setAttribute("i", workerIndex.toLong()).use {

              applyModificationsInCoroutine(fileIndexingResult, workerIndex, updatesLeftCounter, finishCallback)

            }
          }
        }
      }
    }
  }

  override fun waitCurrentIndexingToFinish() {
    //send fake (empty) tasks to all the workers, and wait for them to complete the task
    runBlockingCancellable {
      (0..<workersCount).map { workerIndex ->
        val deferred = CompletableDeferred<Int>(null)
        channels[workerIndex].send { deferred.complete(workerIndex) }
        deferred
      }.awaitAll()
    }
  }

  override fun totalTimeSpentWriting(unit: TimeUnit, workerNo: Int): Long =
    unit.convert(totalTimeSpentOnIndexWritingNs[workerNo].get(), NANOSECONDS)

  /**
   * Assume indexers don't sleep in the coroutine world.
   * This is not entirely correct -- but it is unclear how to calculate the amount of time indexers' coroutines were
   * off-scheduled because of backpressure from the channels being filled up
   */
  override fun totalTimeIndexersSlept(unit: TimeUnit): Long = 0

  private fun applyModificationsInCoroutine(
    fileIndexingResult: FileIndexingResult,
    coroutineIndex: Int,
    updatesLeftCounter: AtomicInteger,
    finishCallback: () -> Unit,
  ) {

    val startedAtNs = System.nanoTime()

    var allModificationsSuccessful = true
    try {
      for (applier in fileIndexingResult.appliers()) {
        if (coroutineIndex == workerIndexFor(applier.indexId)) {
          val applied = applier.apply()
          allModificationsSuccessful = allModificationsSuccessful && applied
          if (!applied) {
            VfsEventsMerger.tryLog("NOT_APPLIED", fileIndexingResult.file()) { applier.toString() }
          }
        }
      }

      for (remover in fileIndexingResult.removers()) {
        if (coroutineIndex == workerIndexFor(remover.indexId)) {
          val removed = remover.remove()
          allModificationsSuccessful = allModificationsSuccessful && removed
          if (!removed) {
            VfsEventsMerger.tryLog("NOT_REMOVED", fileIndexingResult.file()) { remover.toString() }
          }
        }
      }
    }
    catch (pce: ProcessCanceledException) {
      allModificationsSuccessful = false
      if (FileBasedIndexEx.TRACE_STUB_INDEX_UPDATES) {
        Logger.getInstance(FileIndexingResult::class.java)
          .infoWithDebug("applyModifications interrupted,fileId=${fileIndexingResult.fileId()},$pce", RuntimeException(pce))
      }
      throw pce
    }
    catch (t: Throwable) {
      allModificationsSuccessful = false
      Logger.getInstance(FileIndexingResult::class.java)
        .warn("applyModifications interrupted,fileId=${fileIndexingResult.fileId()},$t",
              if (FileBasedIndexEx.TRACE_STUB_INDEX_UPDATES) t else null)
      throw t
    }
    finally {
      val lastOrOnlyInvocationForFile = (updatesLeftCounter.decrementAndGet() == 0)
      val debugString = {
        " updated_indexes=" + fileIndexingResult.statistics().perIndexerEvaluateIndexValueTimes.keys +
        " deleted_indexes=" + fileIndexingResult.statistics().perIndexerEvaluatingIndexValueRemoversTimes.keys
      }

      if (lastOrOnlyInvocationForFile) {
        fileIndexingResult.markFileProcessed(allModificationsSuccessful, debugString)
      }
      else {
        VfsEventsMerger.tryLog("HAS_MORE_MODIFICATIONS", fileIndexingResult.file(), debugString)
      }

      fileIndexingResult.addApplicationTimeNanos(System.nanoTime() - startedAtNs)

      if (lastOrOnlyInvocationForFile) {
        finishCallback()
      }
    }
  }
}


/**
 * Writes nothing, just throws away all the data supplied.
 * To be used in test/benchmarks as baseline/reference point
 */
object FakeIndexWriter : ParallelIndexWriter() {

  override suspend fun writeAsync(fileIndexingResult: FileIndexingResult, finishCallback: () -> Unit) {
    // write nothing, just report 'done':

    for (applier in fileIndexingResult.appliers()) {
      IndexingStamp.setFileIndexedStateCurrent(
        fileIndexingResult.fileId(),
        applier.indexId,
        applier.wasIndexProvidedByExtension()
      )
    }

    fileIndexingResult.markFileProcessed(/*success: */ true) { "nothing" }
    finishCallback()
  }

  override suspend fun writeChangesToIndexes(fileIndexingResult: FileIndexingResult, finishCallback: () -> Unit) = Unit

  override fun waitCurrentIndexingToFinish() = Unit

  override fun totalTimeSpentWriting(unit: TimeUnit, workerNo: Int): Long = 0

  override fun totalTimeIndexersSlept(unit: TimeUnit): Long = 0
}
