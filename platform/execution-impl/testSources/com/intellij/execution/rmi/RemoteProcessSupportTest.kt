package com.intellij.execution.rmi

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.net.NetUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.OutputStream
import java.rmi.registry.LocateRegistry
import java.rmi.registry.Registry
import java.rmi.server.UnicastRemoteObject
import java.util.concurrent.CopyOnWriteArrayList

@TestApplication
class RemoteProcessSupportTest {
  private val target = Any()
  private val watchdog = object : IdeaWatchdog {
    override fun dieNowTestOnly(exitCode: Int) = Unit
    override fun die(): Boolean = true
    override fun isAlive(): Boolean = true
    override fun ping(): Boolean = true
    override fun getWaitTimeoutMillis(): Long = 60_000
    override fun getPulseTimeoutMillis(): Long = 60_000
  }
  private var registry: Registry? = null
  private lateinit var support: TestSupport

  @BeforeEach
  fun setUp() {
    val port = NetUtils.findAvailableSocketPort()
    if (!RemoteObject.IN_PROCESS) {
      registry = LocateRegistry.createRegistry(port).also {
        val stub = UnicastRemoteObject.exportObject(watchdog, 0)
        it.bind(IdeaWatchdog.BINDING_NAME, stub)
        it.bind("entry", stub)
      }
    }
    support = TestSupport(port, watchdog)
  }

  @AfterEach
  fun tearDown() {
    support.release(target, null).get()
    support.stopAll(true)
    registry?.let {
      UnicastRemoteObject.unexportObject(watchdog, true)
      UnicastRemoteObject.unexportObject(it, true)
    }
  }

  @Test
  fun selectionReturnsTheActiveConfiguration(): Unit = timeoutRunBlocking {
    val active = Any()
    val candidate = Any()
    val acquiringThread = Thread.currentThread()
    support.acquire(target, active, null)
    support.onSelected = { requested, selected ->
      assertSame(candidate, requested)
      assertSame(active, selected)
      assertSame(acquiringThread, Thread.currentThread())
    }
    val configuration = Ref.create(candidate)

    assertTrue(support.acquireSelected(target, configuration).ping())

    assertSame(active, configuration.get())
    assertEquals(listOf(active), support.prepared)
    assertEquals(listOf(active), support.created)
  }

  @Test
  fun concurrentSelectionsShareThePendingLaunch(): Unit = timeoutRunBlocking {
    val firstConfiguration = Any()
    val secondCandidate = Any()
    val secondConfiguration = Ref.create(secondCandidate)
    val starting = CompletableDeferred<Unit>()
    val continueStart = CompletableDeferred<Unit>()
    val selected = CompletableDeferred<Unit>()
    support.beforeStart = {
      starting.complete(Unit)
      runBlocking { continueStart.await() }
    }
    support.onSelected = { requested, actual ->
      if (requested === secondCandidate) {
        assertSame(firstConfiguration, actual)
        selected.complete(Unit)
      }
    }
    val first = async(Dispatchers.IO) { support.acquireSelected(target, Ref.create(firstConfiguration)) }
    try {
      starting.await()
      val second = async(Dispatchers.IO) { support.acquireSelected(target, secondConfiguration) }
      selected.await()
      continueStart.complete(Unit)

      assertTrue(first.await().ping())
      assertTrue(second.await().ping())
      assertSame(firstConfiguration, secondConfiguration.get())
      assertEquals(listOf(firstConfiguration), support.prepared)
      assertEquals(listOf(firstConfiguration), support.created)
    }
    finally {
      continueStart.complete(Unit)
    }
  }

  @Test
  fun exactAcquisitionsKeepSeparateConfigurations(): Unit = timeoutRunBlocking {
    val first = Any()
    val second = Any()
    support.onSelected = { _, _ -> error("An exact acquisition must not select a configuration") }
    support.acquire(target, first, null)
    support.acquire(target, second, null)

    assertEquals(setOf(first, second), support.getActiveConfigurations(target).toSet())
    assertEquals(listOf(first, second), support.prepared)
  }

  @Test
  fun aFailedLaunchCanReserveAgain(): Unit = timeoutRunBlocking {
    val configuration = Any()
    support.beforeStart = { throw ExecutionException("Test launch failure") }
    assertThrows<ExecutionException> { support.acquire(target, configuration, null) }
    if (!RemoteObject.IN_PROCESS) {
      assertTrue(support.getActiveConfigurations(target).isEmpty())
    }

    support.beforeStart = {}
    assertTrue(support.acquire(target, configuration, null).ping())
    assertEquals(listOf(configuration), support.getActiveConfigurations(target))
    assertEquals(if (RemoteObject.IN_PROCESS) 1 else 2, support.prepared.size)
  }

  @Test
  fun aFailedSelectionDoesNotReserveAKey(): Unit = timeoutRunBlocking {
    val configuration = Ref.create(Any())
    support.onSelected = { _, _ -> throw ExecutionException("Test selection failure") }
    assertThrows<ExecutionException> { support.acquireSelected(target, configuration) }
    assertTrue(support.getActiveConfigurations(target).isEmpty())

    support.onSelected = { _, _ -> }
    assertTrue(support.acquireSelected(target, configuration).ping())
    assertEquals(listOf(configuration.get()), support.prepared)
  }

  @Test
  fun aWaitingSelectionFollowsTheReplacementProcess(): Unit = timeoutRunBlocking {
    if (RemoteObject.IN_PROCESS) return@timeoutRunBlocking
    val firstConfiguration = Any()
    val waitingCandidate = Any()
    val waitingConfiguration = Ref.create(waitingCandidate)
    val replacement = Any()
    val starting = CompletableDeferred<Unit>()
    val continueStart = CompletableDeferred<Unit>()
    val selected = CompletableDeferred<Unit>()
    support.beforeStart = { configuration ->
      if (configuration === firstConfiguration) {
        starting.complete(Unit)
        runBlocking { continueStart.await() }
        throw ExecutionException("Test launch failure")
      }
    }
    support.onSelected = { requested, _ ->
      if (requested === waitingCandidate) selected.complete(Unit)
    }
    val first = async(Dispatchers.IO) {
      assertThrows<ExecutionException> { support.acquireSelected(target, Ref.create(firstConfiguration)) }
    }
    try {
      starting.await()
      val waiting = async(Dispatchers.IO) { support.acquireSelected(target, waitingConfiguration) }
      selected.await()
      support.acquire(target, replacement, null)
      continueStart.complete(Unit)
      first.await()

      assertTrue(waiting.await().ping())
      assertSame(replacement, waitingConfiguration.get())
      assertEquals(listOf(firstConfiguration, replacement), support.prepared)
      assertEquals(listOf(replacement), support.getActiveConfigurations(target))
    }
    finally {
      continueStart.complete(Unit)
    }
  }

  @Test
  fun selectionKeepsDifferentTargetsSeparate(): Unit = timeoutRunBlocking {
    val otherTarget = Any()
    val otherConfiguration = Any()
    val configuration = Ref.create(Any())
    try {
      support.acquire(otherTarget, otherConfiguration, null)
      support.acquireSelected(target, configuration)

      assertEquals(listOf(configuration.get()), support.getActiveConfigurations(target))
      assertEquals(listOf(otherConfiguration, configuration.get()), support.prepared)
    }
    finally {
      support.release(otherTarget, null).get()
    }
  }

  private class TestSupport(private val port: Int, private val watchdog: IdeaWatchdog)
    : RemoteProcessSupport<Any, IdeaWatchdog, Any>(IdeaWatchdog::class.java) {
    val prepared = CopyOnWriteArrayList<Any>()
    val created = CopyOnWriteArrayList<Any>()
    var onSelected: (Any, Any) -> Unit = { _, _ -> }
    var beforeStart: (Any) -> Unit = {}

    fun acquireSelected(target: Any, configuration: Ref<Any>): IdeaWatchdog =
      acquireWithConfigurationSelection(target, configuration, null)

    override fun fireModificationCountChanged() = Unit
    override fun getName(target: Any): String = "Test process"

    override fun selectConfiguration(target: Any, configuration: Any, processKeys: Iterable<Pair<Any, Any>>): Any {
      val selected = processKeys.firstOrNull { it.first === target }?.second ?: configuration
      onSelected(configuration, selected)
      return selected
    }

    override fun onCreated(target: Any, configuration: Any, result: IdeaWatchdog) {
      created.add(configuration)
    }

    override fun getRunProfileState(target: Any, configuration: Any, executor: Executor): RunProfileState {
      prepared.add(configuration)
      beforeStart(configuration)
      return RunProfileState { _, _ -> DefaultExecutionResult(TestProcessHandler(port)) }
    }

    override fun acquireInProcessFactory(target: Any, configuration: Any): ThrowableComputable<IdeaWatchdog?, Exception> {
      prepared.add(configuration)
      return ThrowableComputable {
        beforeStart(configuration)
        watchdog
      }
    }
  }

  private class TestProcessHandler(private val port: Int) : ProcessHandler() {
    override fun startNotify() {
      super.startNotify()
      notifyTextAvailable("Port/ServicesPort/ID:$port/$port/entry", ProcessOutputType.STDOUT)
    }

    override fun destroyProcessImpl() = notifyProcessTerminated(0)
    override fun detachProcessImpl() = notifyProcessDetached()
    override fun detachIsDefault(): Boolean = false
    override fun getProcessInput(): OutputStream? = null
  }
}
