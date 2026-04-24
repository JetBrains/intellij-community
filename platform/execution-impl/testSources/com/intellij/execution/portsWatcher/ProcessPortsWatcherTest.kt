// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.portsWatcher

import com.intellij.execution.portsWatcher.impl.ProcessPortsWatcherImpl
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.process.ProcessCloseUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.net.NetUtils
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions
import org.junit.After
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ProcessPortsWatcherTest {

  companion object {

    private val logger = thisLogger()

    var server1: HttpServer? = null
    var server2: HttpServer? = null
    var expectedListeningPort1: ListeningPort? = null
    var expectedListeningPort2: ListeningPort? = null

    @JvmStatic
    @BeforeClass
    fun setUp() {
      server1 = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
      server2 = HttpServer.create(InetSocketAddress("0.0.0.0", 0), 0)
      server1!!.start()
      server2!!.start()
      expectedListeningPort1 = ListeningPortImpl(server1!!.address.port, ProcessHandle.current().pid())
      expectedListeningPort2 = ListeningPortImpl(server2!!.address.port, ProcessHandle.current().pid())
    }

    @JvmField
    @ClassRule
    val appRule = ApplicationRule()

    @JvmStatic
    @AfterClass
    fun tearDown() {
      server1?.stop(3)
      server2?.stop(3)
    }
  }

  @After
  fun resetProcessWatchRegistry() {
    enableProcessWatch(true)
  }

  @Test
  fun testListeningPortHandler() = timeoutRunBlocking(30.seconds) {
    var server: HttpServer? = null
    val coroutineScope = this.childScope("Watcher")
    try {
      val currentPid = ProcessHandle.current().pid()

      val handler = object : ListeningPortHandler {
        var startedPorts = 0
        override fun onPortListeningStarted(port: ListeningPort) {
          startedPorts += 1
        }

        var endedPorts = 0
        override fun onPortListeningEnded(port: ListeningPort) {
          endedPorts += 1
        }
      }

      ProcessPortsWatcher.startWatching(
        eelDescriptor = LocalEelDescriptor,
        pid = currentPid,
        handler = handler,
        coroutineScope = coroutineScope,
        options = PortListeningOptions.INCLUDE_SELF_AND_CHILDREN
      )

      delay(3.seconds)

      val startedBefore = handler.startedPorts
      val endedBefore = handler.endedPorts
      server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
      server.start()

      waitUntil(10.seconds, "handler did not get event about started port") {
        handler.startedPorts > startedBefore
      }

      Assertions.assertThat(handler.startedPorts).isEqualTo(startedBefore + 1)

      server?.stop(3)
      server = null

      waitUntil(10.seconds, "handler did not get event about ended port") {
        handler.endedPorts > endedBefore
      }

      Assertions.assertThat(handler.endedPorts).isEqualTo(endedBefore + 1)
    }
    finally {
      server?.stop(3)
      coroutineScope.cancel()
    }
    Unit
  }

  @Test
  fun testParentOnly() = timeoutRunBlocking(30.seconds) {
    Assertions.assertThat(server1).isNotNull()
    Assertions.assertThat(server2).isNotNull()
    val currentPid = ProcessHandle.current().pid()
    val noopHandler = object : ListeningPortHandler {
      override fun onPortListeningStarted(port: ListeningPort) {}
    }
    val watcher = ProcessPortsWatcherImpl(
      eelDescriptor = LocalEelDescriptor,
      pid = currentPid,
      handler = noopHandler,
      options = PortListeningOptions.INCLUDE_SELF_AND_CHILDREN
    )
    val ports = watcher.scanPortsOnce()

    Assertions.assertThat(ports).filteredOn { it == expectedListeningPort1 }.hasSize(1)
    Assertions.assertThat(ports).filteredOn { it == expectedListeningPort2 }.hasSize(1)

    val watcherNoParent = ProcessPortsWatcherImpl(
      eelDescriptor = LocalEelDescriptor,
      pid = currentPid,
      handler = noopHandler,
      options = PortListeningOptions.INCLUDE_CHILDREN
    )
    val portsNoParent = watcherNoParent.scanPortsOnce()
    Assertions.assertThat(portsNoParent).hasSize(0)
    Unit
  }

  @Test
  fun testTwoGenerationsChildren() {
    val runtime = Runtime.getRuntime()
    val python = getPythonExecutable()

    lateinit var tmpPythonFile: Path

    var child1: Process? = null
    var child2: Process? = null

    try {
      tmpPythonFile = Path.of("child2.py")
      val (port1, port2) = getTwoFreePorts()

      // Start python http server
      child1 = runtime.exec(arrayOf(python, "-m", "http.server", "--bind", "127.0.0.1", port1.toString()), null, null)
      Assertions.assertThat(child1).isNotNull()

      val platformDependentChildArg =
        if (SystemInfoRt.isWindows) {
          "import os; os.system(\"\\\"$python\\\" -m http.server $port2\")"
        }
        else {
          "import os; os.system(\"$python -m http.server $port2\")"
        }

      tmpPythonFile.writeText(platformDependentChildArg)

      child2 = runtime.exec(arrayOf(python, "child2.py"), null, null)
      Assertions.assertThat(child2).isNotNull()

      val currentPid = ProcessHandle.current().pid()

      val watcherNoParent = ProcessPortsWatcherImpl(
        eelDescriptor = LocalEelDescriptor,
        pid = currentPid,
        handler = object : ListeningPortHandler {
          override fun onPortListeningStarted(port: ListeningPort) {
          }
        },
        options = PortListeningOptions.INCLUDE_CHILDREN,
      )
      Assertions.assertThat(child1)
        .withFailMessage { "child1 is not alive. output: ${child1.inputStream.readAllBytes().decodeToString()}. error: ${child1.errorStream.readAllBytes().decodeToString()}" }
        .matches { it.isAlive }
      Assertions.assertThat(child2)
        .withFailMessage { "child2 is not alive. output: ${child2.inputStream.readAllBytes().decodeToString()}. error: ${child1.errorStream.readAllBytes().decodeToString()}" }
        .matches { it.isAlive }

      var localhostPortsCountFound = false
      var localhostPortsCountZerosFound = false

      runBlocking {
        withTimeoutOrNull(5.seconds) {
          while (!localhostPortsCountFound || !localhostPortsCountZerosFound) {
            val portsNoParent = watcherNoParent.scanPortsOnce()

            if (!localhostPortsCountFound) {
              val localhostPortsCount = portsNoParent.count { port ->
                port.pid == child1.pid()
                && port.port == port1
              }

              localhostPortsCountFound = localhostPortsCount >= 1
            }

            if (!localhostPortsCountZerosFound) {
              val localhostPortsCountZeros = portsNoParent.count { port ->
                port.pid !in listOf(currentPid, child2.pid(), child1.pid())
                && port.port == port2
              }

              localhostPortsCountZerosFound = localhostPortsCountZeros >= 1
            }

            delay(200.milliseconds)
          }
        }
      }

      Assertions.assertThat(localhostPortsCountFound).withFailMessage { "child1 port is not detected or not correct" }.isTrue()
      Assertions.assertThat(localhostPortsCountZerosFound).withFailMessage { "child2 port is not detected or not correct" }.isTrue()
    }
    finally {
      tmpPythonFile.deleteIfExists()
      child1?.let { process -> stopProcess(process) }
      child2?.let { process -> stopProcess(process) }
    }
  }

  @Test
  fun testStartWatch_ProcessWatchDisabled() = timeoutRunBlocking(30.seconds) {
    enableProcessWatch(false)

    var server: HttpServer? = null
    val coroutineScope = this.childScope("Watcher")
    try {
      val currentPid = ProcessHandle.current().pid()

      val handler = object : ListeningPortHandler {
        var startedPorts = 0
        override fun onPortListeningStarted(port: ListeningPort) {
          startedPorts += 1
        }

        var endedPorts = 0
        override fun onPortListeningEnded(port: ListeningPort) {
          endedPorts += 1
        }
      }

      val processWatcher = ProcessPortsWatcher.startWatching(
        eelDescriptor = LocalEelDescriptor,
        pid = currentPid,
        handler = handler,
        coroutineScope = coroutineScope,
        options = PortListeningOptions.INCLUDE_SELF_AND_CHILDREN
      ) as ProcessPortsWatcherImpl
      Assertions.assertThat(processWatcher.isProcessWatchForListeningPortsEnabled).isFalse()

      val startedBefore = handler.startedPorts
      val endedBefore = handler.endedPorts

      server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
      server.start()

      val elapsedStart = waitUntilOrTimeout(3.seconds) { handler.startedPorts > startedBefore }
      Assertions.assertThat(elapsedStart).isFalse()
      Assertions.assertThat(handler.startedPorts).isEqualTo(0)

      server?.stop(3)
      server = null

      val elapsedStop = waitUntilOrTimeout(3.seconds) { handler.endedPorts > endedBefore }
      Assertions.assertThat(elapsedStop).isFalse()
      Assertions.assertThat(handler.endedPorts).isEqualTo(0)
    }
    finally {
      server?.stop(3)
      coroutineScope.cancel()
    }
    Unit
  }

  //region Private Methods

  /** Polls [condition] until it returns true or [timeout] elapses. Throws AssertionError with [message] on timeout. */
  private suspend fun waitUntil(timeout: Duration, message: String, condition: () -> Boolean) {
    if (!waitUntilOrTimeout(timeout, condition)) {
      throw AssertionError(message)
    }
  }

  /** Polls [condition] until it returns true or [timeout] elapses. Returns whether the condition became true. */
  private suspend fun waitUntilOrTimeout(timeout: Duration, condition: () -> Boolean): Boolean {
    val conditionMet = withTimeoutOrNull(timeout) {
      while (!condition()) {
        delay(10.milliseconds)
      }
      true
    }
    return conditionMet == true
  }

  private fun stopProcess(process: Process) {
    try {
      ProcessCloseUtil.close(process)
    }
    catch (t: Throwable) {
      logger.error("Failed to terminate process ${process.pid()} ", t)
    }
  }

  private fun enableProcessWatch(enable: Boolean) {
    val key = "rdct.portForwarding.processWatch.enabled"

    logger.info("Set port forwarding process watcher registry flag '$key' to state: $enable")
    Registry.get(key).setValue(enable)
  }

  private fun getPythonExecutable(): String {
    // Get a python path
    val python = if (SystemInfoRt.isWindows) {
      "C:\\Program Files\\Python38\\python.exe"
    }
    else {
      "python3"
    }

    logger.info("Defined python executable path: $python")

    // Assert python installed
    val runtime = Runtime.getRuntime()
    val pythonVersion = runtime.exec(arrayOf(python, "--version"), null, null)
    val versionExitCode = pythonVersion.waitFor()

    Assertions.assertThat(versionExitCode).withFailMessage { "python3 is not installed." }.isEqualTo(0)

    return python
  }

  private fun findFreePort(): Int = NetUtils.findAvailableSocketPort()

  /**
   * Get two distinct free ports.
   */
  private fun getTwoFreePorts(): Pair<Int, Int> {
    val port1 = findFreePort()
    var port2 = findFreePort()
    while (port2 == port1) {
      port2 = findFreePort()
    }
    return port1 to port2
  }

  //endregion Private Methods
}