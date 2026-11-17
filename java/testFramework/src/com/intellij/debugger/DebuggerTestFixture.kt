// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger

import com.intellij.JavaTestUtil
import com.intellij.compiler.CompilerManagerImpl
import com.intellij.debugger.engine.AsyncStacksUtils
import com.intellij.debugger.engine.ContextUtil
import com.intellij.debugger.engine.DebugProcessAdapterImpl
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.JavaSourcePositionHighlighter
import com.intellij.debugger.engine.RemoteStateState
import com.intellij.debugger.engine.StackFrameContext
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.SuspendContextRunnable
import com.intellij.debugger.engine.evaluation.CodeFragmentKind
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.GenericDebuggerRunnerSettings
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.impl.RemoteConnectionBuilder
import com.intellij.debugger.impl.SynchronizationBasedSemaphore
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.debugger.settings.ViewsGeneralSettings
import com.intellij.debugger.ui.breakpoints.Breakpoint
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor
import com.intellij.debugger.ui.tree.render.NodeRenderer
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionTestFixture
import com.intellij.execution.ExecutionTestOptions
import com.intellij.execution.Executor
import com.intellij.execution.OutputCheckerFactory
import com.intellij.execution.application.JavaConsoleDecorator
import com.intellij.execution.configurations.JavaCommandLineState
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executionTestFixture
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiImplicitClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ImplicitClassSearch
import com.intellij.testFramework.ThreadTracker
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.ThrowableRunnable
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import com.intellij.util.lang.CompoundRuntimeException
import com.intellij.util.ui.EDT
import com.intellij.util.ui.EdtInvocationManager
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.XSteppingSuspendContext
import com.intellij.xdebugger.impl.frame.HiddenFramesStackFrame
import com.intellij.xdebugger.impl.frame.XStackFrameWithSeparatorAbove
import com.sun.jdi.Value
import com.sun.jdi.VirtualMachine
import com.sun.jdi.request.StepRequest
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties
import org.jetbrains.java.debugger.breakpoints.properties.JavaMethodBreakpointProperties
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import java.nio.file.Path
import java.text.MessageFormat
import java.util.ArrayDeque
import java.util.MissingResourceException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.milliseconds

/**
 * The options of [debuggerTestFixture].
 * Each option replaces a hook of [ExecutionWithDebuggerToolsTestCase] or [DebuggerTestCase] and has the default of that hook.
 * A Java test sets them with the fluent `with*` methods.
 */
@TestOnly
class DebuggerTestOptions {
  /** The options of the execution fixture inside. */
  var execution: ExecutionTestOptions = ExecutionTestOptions()
    private set

  /** The first value of [DebuggerTestFixture.logAllCommands]. It replaces [ExecutionWithDebuggerToolsTestCase.logAllCommands]. */
  var logAllCommands: Boolean = true
    private set

  /**
   * When `true`, the teardown fails the test when a queued [DebuggerTestFixture.onBreakpoint] action did not run.
   * It replaces [ExecutionWithDebuggerToolsTestCase.assertAllBreakpointsHit].
   */
  var assertAllBreakpointsHit: Boolean = true
    private set

  /**
   * When `true`, [DebuggerTestFixture.runProcessAndAwaitCompleted] compares the output with the `.out` file.
   * Set it to `false` where a JUnit 3 subclass overrode `checkTestOutput` with an empty method.
   */
  var checkTestOutput: Boolean = true
    private set

  /** The JDI trace mode of a local debug process. It replaces [DebuggerTestCase.getTraceMode]. */
  var traceMode: Int = VirtualMachine.TRACE_NONE
    private set

  /**
   * Creates the breakpoints for the main class of a local debug process.
   * It replaces an override of `createBreakpoints(String)`. When it is `null`, the fixture calls [DebuggerTestFixture.createBreakpoints].
   */
  var createBreakpoints: BiConsumer<DebuggerTestFixture, String>? = null
    private set

  fun withExecution(options: ExecutionTestOptions): DebuggerTestOptions = apply { execution = options }

  fun withLogAllCommands(value: Boolean): DebuggerTestOptions = apply { logAllCommands = value }

  fun withAssertAllBreakpointsHit(value: Boolean): DebuggerTestOptions = apply { assertAllBreakpointsHit = value }

  fun withCheckTestOutput(value: Boolean): DebuggerTestOptions = apply { checkTestOutput = value }

  fun withTraceMode(value: Int): DebuggerTestOptions = apply { traceMode = value }

  fun withCreateBreakpoints(action: BiConsumer<DebuggerTestFixture, String>?): DebuggerTestOptions = apply { createBreakpoints = action }
}

/**
 * Creates the JUnit 6 replacement of [ExecutionWithDebuggerToolsTestCase] and [DebuggerTestCase].
 *
 * Declare it as an instance field of a `@TestApplication` class, because each test needs its own project and test name.
 * Wrap the body of each test in [DebuggerTestFixture.runTest].
 *
 * The fixture does this setup, in this order:
 * 1. It refreshes the `src` directory of [testAppPath] recursively, so that PSI sees a new file of the test app.
 * 2. It creates the execution fixture inside, with [DebuggerTestOptions.execution]. See [executionTestFixture].
 * 3. In a write action on the EDT, it registers the internal JDK as `JDK` until the end of the test.
 *    It sets the socket transport, `SKIP_CONSTRUCTORS` and `SKIP_GETTERS` to `false`, and `SHOW_DECLARED_TYPE` to `true`.
 *    It does not restore these settings, as [DebuggerTestCase] did not.
 * 4. It registers two [DebuggerTestFixture.atDebuggerTearDown] actions: it stops the debug process, and it closes all editors.
 *
 * Only step 3 and the closing of the editors run on the EDT. The test body runs off the EDT, as in the JUnit 3 bases.
 *
 * The teardown order is the order of the JUnit 3 bases:
 * 1. The [DebuggerTestFixture.atDebuggerTearDown] actions, in the reverse order of registration.
 * 2. The wait for the end of the JDI threads, up to 100 s.
 * 3. The check that each queued [DebuggerTestFixture.onBreakpoint] action ran, unless [DebuggerTestOptions.assertAllBreakpointsHit] is `false`.
 * 4. The teardown of the execution fixture, then the project closes.
 * 5. The exceptions that [DebuggerTestFixture.addException] collected fail the test.
 * 6. The check that the javac threads of the project stopped.
 * 7. The test fails when it created a debug process and did not call [DebuggerTestFixture.runTest]. This step is skipped when the test already failed.
 *
 * Known limits:
 * - One fixture replaces both JUnit 3 bases. So a former [ExecutionWithDebuggerToolsTestCase] subclass also gets the settings of step 3,
 *   and its teardown stops the debug process.
 * - The fixture sets no data context provider of the application. [com.intellij.debugger.impl.DescriptorTestHelper] sets one.
 * - JUnit 6 runs the test methods in another order than JUnit 3. A test that edits a file of the test app or a global setting
 *   can change a later test. The execution fixture reloads the unsaved documents, but it does not restore the settings.
 *
 * @param testAppPath the directory of the test app. It holds `src`, and the expected output files in `outs`.
 */
@JvmOverloads
@TestOnly
fun debuggerTestFixture(
  testAppPath: String,
  outputChecker: OutputCheckerFactory,
  options: DebuggerTestOptions = DebuggerTestOptions(),
): TestFixture<DebuggerTestFixture> = testFixture("debuggerTestFixture") { context ->
  var debugger: DebuggerTestFixture? = null
  val afterProjectClose = testFixture("debuggerTestFixture after the project closes") {
    initialized(Unit) {
      debugger?.checkAfterProjectClose(testFailed = context.extensionContext.executionException.isPresent)
    }
  }
  refreshSources(testAppPath)
  val execution = executionTestFixture(testAppPath, outputChecker, options.execution, afterProjectClose).init()
  edtWriteAction {
    JavaTestUtil.setupInternalJdkAsTestJDK(execution.disposable, DebuggerTestFixture.TEST_JDK_NAME)
    DebuggerSettings.getInstance().transport = DebuggerSettings.SOCKET_TRANSPORT
    DebuggerSettings.getInstance().SKIP_CONSTRUCTORS = false
    DebuggerSettings.getInstance().SKIP_GETTERS = false
    NodeRendererSettings.getInstance().classRenderer.SHOW_DECLARED_TYPE = true
  }
  val fixture = DebuggerTestFixture(execution, options)
  debugger = fixture
  initialized(fixture) {
    fixture.tearDown()
  }
}

/** Without a recursive refresh, PSI does not see a new file of the test app, and [DebuggerTestFixture.createBreakpoints] fails. */
private fun refreshSources(testAppPath: String) {
  val srcPath = FileUtil.toSystemIndependentName(Path.of(testAppPath, ExecutionTestFixture.SOURCES_DIRECTORY_NAME).toString())
  val srcDir = StandardFileSystems.local().refreshAndFindFileByPath(srcPath)
  if (srcDir != null) {
    VfsUtil.markDirtyAndRefresh(false, true, true, srcDir)
  }
}

/**
 * The state and the helpers of a test that [debuggerTestFixture] creates.
 * The members keep the names and the behavior of the members of [ExecutionWithDebuggerToolsTestCase] and [DebuggerTestCase].
 * The members of [com.intellij.execution.ExecutionTestCase] are on [execution].
 */
@TestOnly
class DebuggerTestFixture internal constructor(
  /** The execution fixture of the test: the project, the module, the output checker and the wait helpers. */
  val execution: ExecutionTestFixture,
  private val options: DebuggerTestOptions,
) {
  /** The project of the test. */
  val project: Project
    get() = execution.project

  /** The main Java module of the test. */
  val module: Module
    get() = execution.module

  /** The test root disposable. */
  val disposable: Disposable
    get() = execution.disposable

  /**
   * When `true`, the fixture prints the location of each stop and of each resume or step command to the output.
   * A test can change it in its body. The first value is [DebuggerTestOptions.logAllCommands].
   */
  var logAllCommands: Boolean = options.logAllCommands

  /** `false` after [createBreakpoints] set a `suspendPolicy` on a breakpoint. */
  var wasUsedOnlyDefaultSuspendPolicy: Boolean = true
    private set

  /**
   * The debugger session of the test. [createLocalProcess] and [createLocalSession] set it.
   * [runProcessAndAwaitCompleted] sets it to `null` after the end of the process. A test can set it, as it set `myDebuggerSession`.
   */
  var debuggerSession: DebuggerSession? = null

  /** The debug process of [debuggerSession], or `null`. It replaces `getDebugProcess()`. */
  val debugProcess: DebugProcessImpl?
    get() = debuggerSession?.process

  /**
   * The debug process that the test created. It replaces the field `myDebugProcess`.
   * The teardown stops it, and the resume and step commands use it. Unlike [debugProcess], it stays after the end of the process.
   * [createLocalProcess] and [createLocalSession] set it. A test that sets [debuggerSession] by hand sets it too.
   */
  var createdDebugProcess: DebugProcessImpl? = null

  /** The execution environment of the last local debug process. */
  var executionEnvironment: ExecutionEnvironment? = null
    private set

  /** The run profile state of the last local debug process. */
  var runnableState: RunProfileState? = null
    private set

  private var breakpointProvider: BreakpointProvider? = null
  private val exceptions = ArrayList<Throwable>()
  private val ratherLaterRequests = ArrayList<InvokeRatherLaterRequest>()
  private val tearDownRunnables = ArrayList<ThrowableRunnable<Throwable>>()
  private var compilerManager: CompilerManagerImpl? = null
  private val processStarted = AtomicBoolean()
  private var processRunCalled = false
  private var isInRunTest = false
  private var runTestCalled = false
  private var debugProcessCreated = false

  init {
    atDebuggerTearDown {
      val process = createdDebugProcess
      if (process != null) {
        process.stop(true)
        process.waitFor()
        process.dispose()
      }
    }
    atDebuggerTearDown {
      runInEdtAndWait {
        FileEditorManagerEx.getInstanceEx(project).closeAllFiles()
      }
    }
  }

  /** Adds [msg] as system output to the checker of [execution]. */
  fun systemPrintln(@NonNls msg: String) {
    execution.systemPrintln(msg)
  }

  /** Adds [s] and a line break as output of [outputType] to the checker of [execution]. */
  fun println(@NonNls s: String, outputType: Key<*>) {
    execution.println(s, outputType)
  }

  /**
   * Runs the test: [body], then [runProcessAndAwaitCompleted], as `DebuggerTestCase.runTestRunnable` did.
   *
   * The order is:
   * 1. It initializes the checker of [execution] with the test name.
   * 2. It runs [body] on the current thread. The body creates the debug process and queues the actions, for example [onBreakpoint].
   * 3. It calls [runProcessAndAwaitCompleted]: it starts the debuggee, waits for its end and for the debugger commands,
   *    throws the collected exceptions and compares the output with the `.out` file.
   *
   * When [body] throws, the debuggee does not start, and the output check does not run.
   * A call inside [body] runs only the nested body, because one test can call the body of another test.
   *
   * After the test, the teardown runs in this order:
   * the [atDebuggerTearDown] actions in the reverse order, the wait for the JDI threads, the check of the [onBreakpoint] queue,
   * the teardown of [execution] and the project, the exceptions of [addException], and the check of the javac threads.
   * At the end, the teardown fails a test that created a debug process and did not call [runTest].
   */
  fun runTest(body: ThrowableRunnable<Throwable>) {
    if (isInRunTest) {
      body.run()
      return
    }
    isInRunTest = true
    runTestCalled = true
    try {
      execution.checker.init(execution.testName)
      body.run()
      runProcessAndAwaitCompleted()
    }
    finally {
      isInRunTest = false
    }
  }

  /**
   * The Kotlin form of [runTest].
   * It runs [body] in `timeoutRunBlocking` with the timeout of [ExecutionTestFixture.timeoutMillis], then [runProcessAndAwaitCompleted].
   */
  @JvmSynthetic
  fun runTest(body: suspend () -> Unit) {
    runTest(ThrowableRunnable {
      timeoutRunBlocking(execution.timeoutMillis.milliseconds) {
        body()
      }
    })
  }

  /**
   * Starts the debuggee, waits for its end and for the debugger commands, and checks that the session is gone.
   * Then it throws the collected exceptions and compares the output with the `.out` file, unless [DebuggerTestOptions.checkTestOutput] is `false`.
   * [runTest] calls it after the body. A second call in the same test does nothing.
   */
  fun runProcessAndAwaitCompleted() {
    if (processRunCalled) {
      return
    }
    processRunCalled = true
    val process = debugProcess
    if (process != null) {
      runProcess()
      execution.waitProcess(process.processHandler)
      waitForCompleted()
      // DebuggerSession.dispose() unregisters the session in Application.invokeLater().
      // The Swing events of waitForCompleted() can run before it, e.g. while a background write action holds the lock.
      ApplicationManager.getApplication().invokeAndWait(EmptyRunnable.getInstance(), ModalityState.any())
      assertNull(DebuggerManagerEx.getInstanceEx(project).getDebugProcess(process.processHandler))
      debuggerSession = null
    }

    throwExceptionsIfAny()
    if (options.checkTestOutput) {
      execution.checker.checkValid(execution.testProjectJdk)
    }
  }

  /**
   * Starts the debuggee of [debugProcess] and does not wait for it. A second call does nothing.
   * Call it in the body when the test talks to the running session.
   * [runTest] then waits for the end of the debuggee in [runProcessAndAwaitCompleted].
   */
  fun runProcess() {
    val process = debugProcess
    if (process != null && processStarted.compareAndSet(false, true)) {
      process.processHandler.startNotify()
    }
  }

  internal fun tearDown() {
    runAll(
      { runAll(tearDownRunnables.toList()) { it.run() } },
      {
        ThreadTracker.awaitJDIThreadsTermination(100, TimeUnit.SECONDS)
        ratherLaterRequests.clear()
        val provider = breakpointProvider
        breakpointProvider = null
        if (options.assertAllBreakpointsHit && provider != null) {
          provider.assertAllBreakpointsHit()
        }
      },
    )
  }

  internal fun checkAfterProjectClose(testFailed: Boolean) {
    runAll(
      { throwExceptionsIfAny() },
      {
        val manager = compilerManager
        if (manager != null) {
          // ExternalJavacManager.stop() runs on the project disposal, so the Netty threads must be gone now.
          assertTrue(manager.awaitNettyThreadPoolTermination(1, TimeUnit.MINUTES))
          compilerManager = null
        }
      },
      {
        if (debugProcessCreated && !runTestCalled && !testFailed) {
          fail("The test created a debug process, but did not call runTest. Wrap the test body in runTest.")
        }
      },
    )
  }

  /**
   * Runs [runnable] in the teardown, before the teardown of the execution fixture.
   * The actions run in the reverse order of registration.
   * To run code later, register it on [disposable].
   */
  fun atDebuggerTearDown(runnable: ThrowableRunnable<Throwable>) {
    tearDownRunnables.add(0, runnable)
  }

  /** Restores the current value of a setting in the teardown. */
  fun <T> restoreSettingAfterTest(getter: Supplier<out T>, setter: Consumer<in T>) {
    val oldValue = getter.get()
    atDebuggerTearDown { setter.accept(oldValue) }
  }

  /** Sets a setting to [newValue] and restores the old value in the teardown. */
  fun <T> setSettingForTest(getter: Supplier<out T>, setter: Consumer<in T>, newValue: T) {
    val oldValue = getter.get()
    setter.accept(newValue)
    atDebuggerTearDown { setter.accept(oldValue) }
  }

  /** Disables [renderer] until the end of the test. */
  fun disableRenderer(renderer: NodeRenderer) {
    setRendererEnabled(renderer, false)
  }

  /** Enables [renderer] until the end of the test. */
  fun enableRenderer(renderer: NodeRenderer) {
    setRendererEnabled(renderer, true)
  }

  private fun setRendererEnabled(renderer: NodeRenderer, state: Boolean) {
    setSettingForTest({ renderer.isEnabled }, { renderer.isEnabled = it }, state)
  }

  private fun performDebuggerCommand(logPrefix: String, context: SuspendContextImpl, action: Runnable) {
    if (logAllCommands) {
      printContext(logPrefix, context)
    }
    DebuggerInvocationUtil.invokeLater(project, action)
  }

  /**
   * Replaces [resume] with the resume of [com.intellij.debugger.impl.DescriptorTestHelper], as `DescriptorTestCase.resume` overrode it.
   * The fixture and the test call [resume], so each resume goes through the handler.
   */
  internal var resumeHandler: Consumer<SuspendContextImpl>? = null

  /**
   * Resumes [context] with the lowest priority. It prints the location first when [logAllCommands] is `true`.
   * With a [com.intellij.debugger.impl.DescriptorTestHelper], it first prints the logged descriptors, as `DescriptorTestCase` did.
   */
  fun resume(context: SuspendContextImpl) {
    val handler = resumeHandler
    if (handler != null) {
      handler.accept(context)
    }
    else {
      resumeNow(context)
    }
  }

  /** The resume of [resume] without the [resumeHandler]. */
  internal fun resumeNow(context: SuspendContextImpl) {
    performDebuggerCommand("Resuming ", context) {
      createdDebugProcess!!.session.resumeSuspendContext(context, PrioritizedTask.Priority.LOWEST)
    }
  }

  /** Steps into from [context]. It prints the location first when [logAllCommands] is `true`. */
  @JvmOverloads
  fun stepInto(context: SuspendContextImpl, ignoreFilters: Boolean = false) {
    performDebuggerCommand("Stepping into ", context) {
      createdDebugProcess!!.session.stepInto(context, ignoreFilters, null, StepRequest.STEP_LINE)
    }
  }

  /** Steps over from [context]. It prints the location first when [logAllCommands] is `true`. */
  fun stepOver(context: SuspendContextImpl) {
    performDebuggerCommand("Stepping over ", context) {
      createdDebugProcess!!.session.stepOver(context, false, null, StepRequest.STEP_LINE)
    }
  }

  /** Steps out from [context]. It prints the location first when [logAllCommands] is `true`. */
  fun stepOut(context: SuspendContextImpl) {
    performDebuggerCommand("Stepping out ", context) {
      createdDebugProcess!!.session.stepOut(context, StepRequest.STEP_LINE)
    }
  }

  /** Resumes the XDebugger session on the EDT. It prints the location first when [logAllCommands] is `true`. */
  fun resume() {
    SwingUtilities.invokeLater {
      if (logAllCommands) {
        printContext("Resuming ", debugProcess!!.debuggerContext)
      }
      val session = debuggerSession!!.xDebugSession
      assertNotNull(session)
      session!!.resume()
    }
  }

  private fun throwExceptionsIfAny() {
    synchronized(exceptions) {
      CompoundRuntimeException.throwIfNotEmpty(exceptions)
      exceptions.clear()
    }
  }

  private fun getBreakpointProvider(): BreakpointProvider {
    breakpointProvider?.let { return it }
    val process = debugProcess
    assertNotNull(process, "Debug process was not started")
    val created = createdDebugProcess!!
    val provider = BreakpointProvider(created)
    breakpointProvider = provider
    if (USE_XSESSION_PAUSE_LISTENER_KEY.get(created.project, false) == true) {
      useXSessionPauseListener(process!!)
    }
    else {
      process!!.addDebugProcessListener(provider, disposable)
    }
    return provider
  }

  private fun useXSessionPauseListener(debugProcess: DebugProcessImpl) {
    val jvmSession = debugProcess.session
    val process = debugProcess.xdebugProcess
    assert(process != null)
    val xSession = process!!.session
    xSession.addSessionListener(object : XDebugSessionListener {
      override fun sessionPaused() {
        val suspendContext = jvmSession.contextManager.context.suspendContext
        if (suspendContext == null) {
          val xSessionSuspendContext = xSession.suspendContext
          assert(xSessionSuspendContext is XSteppingSuspendContext) {
            "Suspension context is null and XSuspendContext is  $xSessionSuspendContext"
          }
        }
        else {
          breakpointProvider!!.pausedWrapper(suspendContext)
        }
      }
    })
  }

  /**
   * Queues an action to run a single time.
   *
   * Each time the VM stops, the fixture polls one action from the queue and runs it.
   * The VM can stop at a breakpoint, or after a command such as [stepInto] or [stepOver].
   * The action must end with a resume command, for example [resume] or a step.
   * On an [AssertionError], the fixture calls [resume].
   */
  fun onBreakpoint(runnable: SuspendContextRunnable) {
    getBreakpointProvider().onBreakpoint(runnable)
  }

  /**
   * Runs an action each time the VM stops, at a breakpoint or after a command such as [stepInto].
   * It runs after the one-time action of [onBreakpoint].
   */
  fun onEveryBreakpoint(runnable: SuspendContextRunnable) {
    getBreakpointProvider().onEveryBreakpoint(runnable)
  }

  /**
   * Collects each stop of the VM, as [onEveryBreakpoint] reports it, in a [PauseMonitor].
   * Call it before the debuggee starts, for example before [runProcess].
   */
  fun createPauseMonitor(): PauseMonitor {
    val pauses = LinkedBlockingQueue<SuspendContextImpl>()
    onEveryBreakpoint { suspendContext -> pauses.add(suspendContext) }
    return PauseMonitor(pauses)
  }

  /** The stops of the VM that [createPauseMonitor] collects, in their order. */
  class PauseMonitor internal constructor(private val pauses: LinkedBlockingQueue<SuspendContextImpl>) {
    /** Takes the next stop. It waits up to 1 minute and returns `null` after the timeout. */
    fun waitForPause(): SuspendContextImpl? = pauses.poll(1, TimeUnit.MINUTES)
  }

  /** Queues two actions to run a single time, one after the other. See [onBreakpoint]. */
  fun onStop(runnable: SuspendContextRunnable, then: SuspendContextRunnable) {
    onBreakpoint { suspendContext ->
      try {
        runnable.run(suspendContext)
      }
      finally {
        then.run(suspendContext)
      }
    }
  }

  /** Queues an action to run a single time, see [onBreakpoint]. After the action, the execution resumes. */
  fun doWhenPausedThenResume(runnable: SuspendContextRunnable) {
    onStop(runnable) { resume(it) }
  }

  /** Prints the index and the method of [frameProxy]. */
  @Throws(EvaluateException::class)
  fun printFrameProxy(frameProxy: StackFrameProxyImpl) {
    val frameIndex = frameProxy.frameIndex
    val method = frameProxy.location().method()
    systemPrintln("frameProxy($frameIndex) = $method")
  }

  /** Prints the cached location of an initialized debugger context. It does not query the JVM. */
  fun printContext(context: DebuggerContextImpl) {
    printContext("", context)
  }

  /** Prints [prefix] and the cached location of an initialized debugger context. It does not query the JVM. */
  fun printContext(prefix: String, context: DebuggerContextImpl) {
    printContextImpl(prefix, context) { context.sourcePosition }
  }

  /** Prints the frame location. It queries the JVM on the debugger manager thread without a read lock. */
  @RequiresReadLockAbsence
  fun printContext(context: SuspendContextImpl) {
    printContext("", context)
  }

  /** Prints [prefix] and the frame location. It queries the JVM on the debugger manager thread without a read lock. */
  @RequiresReadLockAbsence
  fun printContext(prefix: String, context: SuspendContextImpl) {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    printContextImpl(prefix, context) { ContextUtil.getSourcePosition(context) }
  }

  private fun printContextImpl(prefix: String, context: StackFrameContext, sourcePosition: Supplier<SourcePosition?>) {
    if (context.frameProxy != null) {
      val position = sourcePosition.get()
      systemPrintln(prefix + ReadAction.computeBlocking<String, RuntimeException> { toDisplayableString(position) })
    }
    else {
      systemPrintln("Context thread is null")
    }
  }

  /** Prints the frame location with 20 characters of the source text around it, and the highlighted range. */
  @RequiresReadLockAbsence
  fun printContextWithText(context: SuspendContextImpl) {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    if (context.frameProxy != null) {
      val sourcePosition = requireNotNull(ContextUtil.getSourcePosition(context))
      ReadAction.computeBlocking<Unit, RuntimeException> {
        val offset = sourcePosition.offset
        val document = PsiDocumentManager.getInstance(project).getDocument(sourcePosition.file)
        val text = requireNotNull(document).immutableCharSequence
        var positionText = ""
        if (offset > -1) {
          val before = text.subSequence(maxOf(0, offset - 20), offset)
          val after = text.subSequence(offset, minOf(offset + 20, text.length))
          positionText = StringUtil.escapeLineBreak(" [$before<*>$after]")
        }
        systemPrintln(toDisplayableString(sourcePosition) + positionText)
      }
      printHighlightingRange(context)
    }
    else {
      systemPrintln("Context thread is null")
    }
  }

  @RequiresReadLockAbsence
  private fun printHighlightingRange(context: SuspendContextImpl) {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    val position = context.debugProcess.positionManager.getSourcePosition(context.location)
    val actualText = ReadAction.computeBlocking<String?, RuntimeException> {
      if (position == null) {
        return@computeBlocking null
      }
      val range = JavaSourcePositionHighlighter().getHighlightRange(position)
      range?.substring(position.file.text)
    }
    if (actualText != null) {
      systemPrintln("Highlight code range: '" + StringUtil.escapeLineBreak(actualText) + "'")
    }
    else {
      systemPrintln("Highlight whole line")
    }
  }

  /** Runs [runnable] on the EDT after the debugger manager thread and the EDT pass each other [RATHER_LATER_INVOKES_N] times. */
  fun invokeRatherLater(context: SuspendContextImpl, runnable: Runnable) {
    invokeRatherLater(context.debugProcess, object : SuspendContextCommandImpl(context) {
      override fun contextAction(suspendContext: SuspendContextImpl) {
        DebuggerInvocationUtil.invokeLater(project, runnable)
      }

      override fun commandCancelled() {
        LOG.error("invokeRatherLater was silently cancelled " + runnable.javaClass)
      }
    })
  }

  /** Schedules [command] on the debugger manager thread of [debugProcess] after the threads pass each other [RATHER_LATER_INVOKES_N] times. */
  fun invokeRatherLater(command: DebuggerCommandImpl) {
    invokeRatherLater(debugProcess!!, command)
  }

  /** Schedules [command] on the debugger manager thread of [debugProcess] after the threads pass each other [RATHER_LATER_INVOKES_N] times. */
  fun invokeRatherLater(debugProcess: DebugProcessImpl, command: DebuggerCommandImpl) {
    UIUtil.invokeLaterIfNeeded {
      ratherLaterRequests.add(InvokeRatherLaterRequest(command, debugProcess))
      if (ratherLaterRequests.size == 1) {
        pumpSwingThread()
      }
    }
  }

  private fun pumpSwingThread() {
    LOG.assertTrue(EDT.isCurrentThreadEdt())

    val request = ratherLaterRequests[0]
    request.invokesN++

    if (request.invokesN == RATHER_LATER_INVOKES_N) {
      ratherLaterRequests.removeAt(0)
      if (ratherLaterRequests.isNotEmpty()) {
        pumpSwingThread()
      }
    }

    val command = request.debuggerCommand
    if (command is SuspendContextCommandImpl) {
      val suspendContext = command.suspendContext!!
      suspendContext.managerThread.schedule(object : SuspendContextCommandImpl(suspendContext) {
        override val priority: PrioritizedTask.Priority
          get() = PrioritizedTask.Priority.LOWEST

        override fun contextAction(suspendContext: SuspendContextImpl) {
          pumpDebuggerThread(request)
        }

        override fun commandCancelled() {
          pumpDebuggerThread(request)
        }
      })
    }
    else {
      request.debugProcess.managerThread.schedule(object : DebuggerCommandImpl(PrioritizedTask.Priority.LOWEST) {
        override fun action() {
          pumpDebuggerThread(request)
        }

        override fun commandCancelled() {
          pumpDebuggerThread(request)
        }
      })
    }
  }

  private fun pumpDebuggerThread(request: InvokeRatherLaterRequest) {
    if (request.invokesN == RATHER_LATER_INVOKES_N) {
      ApplicationManager.getApplication().executeOnPooledThread(Runnable {
        val managerThread = request.debugProcess.managerThread
        while (hasDispatchedCommands(managerThread)) {
          TimeoutUtil.sleep(1)
        }
        managerThread.schedule(request.debuggerCommand)
      })
    }
    else if (!EDT.isCurrentThreadEdt()) {
      EdtInvocationManager.getInstance().invokeAndWait { pumpSwingThread() }
    }
    else {
      SwingUtilities.invokeLater { pumpSwingThread() }
    }
  }

  /** Adds [e] to the exceptions that fail the test at the end of [runProcessAndAwaitCompleted] and in the teardown. */
  fun addException(e: Throwable) {
    synchronized(exceptions) {
      exceptions.add(e)
    }
  }

  /** Fails the test with the text of [th]. It always throws. */
  fun error(th: Throwable): Nothing = fail(StringUtil.getThrowableText(th))

  /**
   * Creates the breakpoints of the `Breakpoint!` comments in [file].
   *
   * A breakpoint comment has the form `[kind] Breakpoint! [property...]`. It sets a breakpoint on the next line.
   * The kinds are `Method`, `Field(fieldName)`, `Exception(java.lang.RuntimeException)`, `ConditionalReturn` and `Line`.
   * No kind means a line breakpoint.
   * The properties of all breakpoints are `Class filters(-MethodClassFilter$A)`, `Condition(i >= 10 || (i % 5 == 0))`,
   * `LogExpression("i = " + i)`, `Pass count(13)`, `Enabled(false)` and `suspendPolicy(SuspendAll | SuspendThread | SuspendNone)`.
   * The properties of a method breakpoint are `Emulated(true)`, `OnEntry(true)` and `OnExit(true)`.
   * The property of an exception breakpoint is `Catch class filters(-ExceptionTest,-com.intellij.rt.*)`.
   * The property of a line breakpoint is `lambdaOrdinal(0)`.
   * See [ExecutionWithDebuggerToolsTestCase.createBreakpoints] for the full description.
   */
  fun createBreakpoints(file: PsiFile) {
    val runnable = Runnable {
      val breakpointManager = DebuggerManagerEx.getInstanceEx(project).breakpointManager
      val document: Document? =
        BinaryFileTypeDecompilers.getInstance().allowDecompilerSlowOperation { PsiDocumentManager.getInstance(project).getDocument(file) }
      assert(document != null)
      val text = document!!.text

      var offset = text.indexOf("Breakpoint!")
      while (offset != -1) {
        createBreakpoint(breakpointManager, file, document, text, offset)
        offset = text.indexOf("Breakpoint!", offset + 1)
      }
    }
    if (!EDT.isCurrentThreadEdt()) {
      DebuggerInvocationUtil.invokeAndWait(project, runnable, ModalityState.defaultModalityState())
    }
    else {
      runnable.run()
    }
  }

  private fun createBreakpoint(
    breakpointManager: com.intellij.debugger.ui.breakpoints.BreakpointManager,
    file: PsiFile,
    document: Document,
    text: String,
    offset: Int,
  ) {
    val commentLine = document.getLineNumber(offset)
    val fileName = file.virtualFile.name
    val breakpointLocation = fileName + ":" + (commentLine + 1 + 1)

    val commentText = text.substring(document.getLineStartOffset(commentLine), document.getLineEndOffset(commentLine))
    val comment = BreakpointComment.parse(commentText, file.virtualFile.presentableUrl, commentLine)

    val breakpoint: Breakpoint<*>?
    when (val kind = comment.readKind()) {
      "Method" -> {
        breakpoint = breakpointManager.addMethodBreakpoint(document, commentLine + 1)
        if (breakpoint != null) {
          systemPrintln("MethodBreakpoint created at $breakpointLocation")
          val properties = breakpoint.xBreakpoint.properties as JavaMethodBreakpointProperties

          val emulated = comment.readBooleanValue("Emulated")
          if (emulated != null) {
            properties.EMULATED = emulated
            systemPrintln("Emulated = $emulated")
          }

          val entry = comment.readBooleanValue("OnEntry")
          if (entry != null) {
            properties.WATCH_ENTRY = entry
            systemPrintln("On Entry = $entry")
          }

          val exit = comment.readBooleanValue("OnExit")
          if (exit != null) {
            properties.WATCH_EXIT = exit
            systemPrintln("On Exit = $exit")
          }
        }
      }
      "Field" -> {
        breakpoint = breakpointManager.addFieldBreakpoint(document, commentLine + 1, comment.readKindValue())
        if (breakpoint != null) {
          systemPrintln("FieldBreakpoint created at $breakpointLocation")
        }
      }
      "Exception" -> {
        val exceptionClassName = requireNotNull(comment.readKindValue())
        breakpoint = breakpointManager.addExceptionBreakpoint(exceptionClassName)
        if (breakpoint != null) {
          systemPrintln("ExceptionBreakpoint created at $breakpointLocation")
          val catchClassFiltersStr = comment.readValue("Catch class filters")
          if (catchClassFiltersStr != null) {
            val filters = BreakpointComment.parseClassFilters(catchClassFiltersStr)
            breakpoint.setCatchFiltersEnabled(true)
            breakpoint.setCatchClassFilters(filters.first)
            breakpoint.setCatchClassExclusionFilters(filters.second)
            systemPrintln("Catch class filters = $catchClassFiltersStr")
          }
        }
      }
      "ConditionalReturn" -> {
        breakpoint = breakpointManager.addLineBreakpoint(document, commentLine + 1) { p ->
          // Note that we don't support `return` inside of lambda in unit tests.
          p.encodedInlinePosition = JavaLineBreakpointProperties.encodeInlinePosition(JavaLineBreakpointProperties.NO_LAMBDA, true)
        }
        if (breakpoint != null) {
          systemPrintln("ConditionalReturnBreakpoint created at $breakpointLocation")
        }
      }
      "Line" -> {
        val lambdaOrdinal = comment.readIntValue("lambdaOrdinal")
        breakpoint = breakpointManager.addLineBreakpoint(document, commentLine + 1) { p ->
          if (lambdaOrdinal != null) {
            p.encodedInlinePosition = JavaLineBreakpointProperties.encodeInlinePosition(lambdaOrdinal, false)
          }
        }
        if (breakpoint != null) {
          systemPrintln("LineBreakpoint created at $breakpointLocation")
        }
      }
      else -> throw IllegalArgumentException("Invalid kind '" + kind + "' at " + fileName + ":" + (commentLine + 1))
    }

    if (breakpoint == null) {
      LOG.error("Unable to set a breakpoint at line " + (commentLine + 1))
      return
    }

    val enabled = comment.readValue("Enabled")
    if (enabled != null) {
      breakpoint.xBreakpoint.setEnabled(enabled.toBoolean())
      systemPrintln("Enabled = $enabled")
    }

    val suspendPolicy = comment.readValue("suspendPolicy")
    if (suspendPolicy != null) {
      breakpoint.suspendPolicy = suspendPolicy
      systemPrintln("SUSPEND_POLICY = $suspendPolicy")
      wasUsedOnlyDefaultSuspendPolicy = false
    }

    val condition = comment.readValue("Condition")
    if (condition != null) {
      breakpoint.setCondition(TextWithImportsImpl(CodeFragmentKind.EXPRESSION, condition))
      systemPrintln("Condition = $condition")
    }

    val logExpression = comment.readValue("LogExpression")
    if (logExpression != null) {
      breakpoint.xBreakpoint.setLogExpression(logExpression)
      systemPrintln("LogExpression = $logExpression")
    }

    val passCount = comment.readIntValue("Pass count")
    if (passCount != null) {
      breakpoint.isCountFilterEnabled = true
      breakpoint.countFilter = passCount
      systemPrintln("Pass count = $passCount")
    }

    val classFiltersStr = comment.readValue("Class filters")
    if (classFiltersStr != null) {
      breakpoint.isClassFiltersEnabled = true
      val filters = BreakpointComment.parseClassFilters(classFiltersStr)
      breakpoint.classFilters = filters.first
      breakpoint.classExclusionFilters = filters.second
      systemPrintln("Class filters = $classFiltersStr")
    }

    comment.done()
  }

  /**
   * Creates the breakpoints of the class [className], or of the compact source file with this name.
   * It fails the test when it finds neither.
   */
  fun createBreakpoints(className: String) {
    val psiFile = ReadAction.computeBlocking<PsiFile?, RuntimeException> {
      val psiClass = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project))
      if (psiClass != null) {
        return@computeBlocking psiClass.containingFile
      }
      // else try to find a compact source file with the same name
      val implicitClass = findImplicitClass(className)
      if (implicitClass != null) {
        return@computeBlocking implicitClass.containingFile
      }
      fail<Unit>("Class for breakpoint installation not found $className")
      null
    }
    createBreakpoints(psiFile!!)
  }

  private fun createBreakpointsForMainClass(className: String) {
    val action = options.createBreakpoints
    if (action != null) {
      action.accept(this, className)
    }
    else {
      createBreakpoints(className)
    }
  }

  /** Finds the implicit class of the compact source file [className] in the project scope. */
  fun findImplicitClass(className: String): PsiImplicitClass? {
    return ReadAction.computeCancellableUnsafe<PsiImplicitClass?, RuntimeException> {
      ImplicitClassSearch.search(className, project, GlobalSearchScope.projectScope(project)).findFirst()
    }
  }

  /** Evaluates [code] of [kind] in [evaluationContext]. */
  @Throws(EvaluateException::class)
  fun evaluate(kind: CodeFragmentKind, code: String, evaluationContext: EvaluationContextImpl): Value? {
    val watchItemDescriptor = WatchItemDescriptor(project, TextWithImportsImpl(kind, code))
    watchItemDescriptor.setContext(evaluationContext)
    val exception = watchItemDescriptor.evaluateException
    if (exception != null) {
      throw exception
    }
    return watchItemDescriptor.value
  }

  /** Evaluates [code] of [kind] in the frame of [suspendContext]. */
  @Throws(EvaluateException::class)
  fun evaluate(kind: CodeFragmentKind, code: String, suspendContext: SuspendContextImpl): Value? {
    return evaluate(kind, code, createEvaluationContext(suspendContext))
  }

  /**
   * Creates an evaluation context for the frame and the `this` object of [suspendContext].
   * When the `this` object is not available, it fails the test through [error].
   */
  fun createEvaluationContext(suspendContext: SuspendContextImpl): EvaluationContextImpl {
    try {
      val proxy = suspendContext.frameProxy
      assertNotNull(proxy)
      return EvaluationContextImpl(suspendContext, proxy, proxy!!.thisObject())
    }
    catch (e: EvaluateException) {
      error(e)
    }
  }

  /** Creates and initializes a debugger context for the frame of [suspendContext]. */
  fun createDebuggerContext(suspendContext: SuspendContextImpl): DebuggerContextImpl {
    val stackFrame = suspendContext.frameProxy
    var session: DebuggerSession? = null
    UIUtil.invokeAndWaitIfNeeded(Runnable {
      session = DebuggerManagerEx.getInstanceEx(project).getSession(suspendContext.debugProcess)
    })
    val debuggerContext = DebuggerContextImpl.createDebuggerContext(session, suspendContext, stackFrame?.threadProxy(), stackFrame)
    debuggerContext.initCaches()
    return debuggerContext
  }

  /** Adds a line breakpoint at line 3 of the class `HelloWorld`. */
  fun createBreakpointInHelloWorld() {
    DebuggerInvocationUtil.invokeAndWait(project, {
      val breakpointManager = DebuggerManagerEx.getInstanceEx(project).breakpointManager
      val psiClass = JavaPsiFacade.getInstance(project).findClass("HelloWorld", GlobalSearchScope.allScope(project))
      assertNotNull(psiClass)
      val document = PsiDocumentManager.getInstance(project).getDocument(psiClass!!.containingFile)
      assertNotNull(breakpointManager.addLineBreakpoint(document!!, 3))
    }, ApplicationManager.getApplication().defaultModalityState)
  }

  /** Creates a local debug process of `HelloWorld` with a breakpoint at line 3. */
  fun createHelloWorldProcessWithBreakpoint() {
    createLocalProcess("HelloWorld")
    createBreakpointInHelloWorld()
  }

  /** Creates a local debug process of [className] with the socket transport. It sets [debuggerSession] and [createdDebugProcess]. */
  fun createLocalProcess(className: String) {
    createLocalProcess(execution.createJavaParameters(className))
  }

  /** Creates a local debug process with [javaParameters] and the socket transport. It sets [debuggerSession] and [createdDebugProcess]. */
  fun createLocalProcess(javaParameters: JavaParameters) {
    LOG.assertTrue(createdDebugProcess == null)
    val session = createLocalProcess(DebuggerSettings.SOCKET_TRANSPORT, javaParameters)
    debuggerSession = session
    createdDebugProcess = session.process
  }

  /**
   * Creates a local debug process with [transport] and returns its session.
   * It creates the breakpoints of the main class first. It does not set [debuggerSession].
   */
  fun createLocalProcess(transport: Int, javaParameters: JavaParameters): DebuggerSession {
    createBreakpointsForMainClass(javaParameters.mainClass)

    DebuggerSettings.getInstance().transport = transport

    val debuggerRunnerSettings = GenericDebuggerRunnerSettings()
    debuggerRunnerSettings.setLocal(true)
    debuggerRunnerSettings.transport = transport
    debuggerRunnerSettings.debugPort = if (transport == DebuggerSettings.SOCKET_TRANSPORT) "0" else DEFAULT_ADDRESS.toString()

    val mockConfiguration = MockConfiguration(project, module)
    val environment = ExecutionEnvironmentBuilder(project, DefaultDebugExecutor.getDebugExecutorInstance())
      .runnerSettings(debuggerRunnerSettings)
      .runProfile(mockConfiguration)
      .build()
    executionEnvironment = environment
    val state = createMockJavaCommandLineState(environment, javaParameters, mockConfiguration)
    runnableState = state

    val debugParameters = RemoteConnectionBuilder(debuggerRunnerSettings.LOCAL, debuggerRunnerSettings.transport, debuggerRunnerSettings.debugPort)
      .project(project)
      .checkValidity(true)
      .asyncAgent(false) // add manually to allow early tmp folder deletion
      .create(javaParameters)

    AsyncStacksUtils.addDebuggerAgent(javaParameters, project, true, disposable)

    environment.putUserData(DefaultDebugEnvironment.DEBUGGER_TRACE_MODE, options.traceMode)
    val session = attachVirtualMachine(state, environment, debugParameters, false)

    val processHandler = session.process.processHandler
    session.process.addProcessListener(object : ProcessListener {
      override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        execution.print(event.text, outputType)
      }
    })

    val process = DebuggerManagerEx.getInstanceEx(project).getDebugProcess(processHandler)
    assertNotNull(process)
    return session
  }

  /**
   * Creates a local debug session with the asynchronous stack trace agent, and starts its XDebugger session at once.
   * It sets [debuggerSession] and [createdDebugProcess].
   */
  fun createLocalSession(javaParameters: JavaParameters): DebuggerSession {
    createBreakpointsForMainClass(javaParameters.mainClass)
    DebuggerSettings.getInstance().transport = DebuggerSettings.SOCKET_TRANSPORT

    val debuggerRunnerSettings = GenericDebuggerRunnerSettings()
    debuggerRunnerSettings.LOCAL = true

    val debugParameters = RemoteConnectionBuilder(debuggerRunnerSettings.LOCAL, debuggerRunnerSettings.transport, debuggerRunnerSettings.debugPort)
      .project(project)
      .asyncAgent(true)
      .create(javaParameters)

    val mockConfiguration = MockConfiguration(project, module)
    val environment = ExecutionEnvironmentBuilder(project, DefaultDebugExecutor.getDebugExecutorInstance())
      .runnerSettings(debuggerRunnerSettings)
      .runProfile(mockConfiguration)
      .build()
    val state = createMockJavaCommandLineState(environment, javaParameters, mockConfiguration)
    runnableState = state

    val sessionEnvironment = ExecutionEnvironmentBuilder(project, DefaultDebugExecutor.getDebugExecutorInstance())
      .runProfile(mockConfiguration)
      .build()
    executionEnvironment = sessionEnvironment
    val debugEnvironment = DefaultDebugEnvironment(sessionEnvironment, state, debugParameters, false)
    val session = DebuggerManagerEx.getInstanceEx(project).attachVirtualMachine(debugEnvironment)
    assertNotNull(session, "Failed to attach debugger session")
    debugProcessCreated = true
    debuggerSession = session

    ApplicationManager.getApplication().invokeAndWait({
      try {
        val starter = object : XDebugProcessStarter() {
          override fun start(session: XDebugSession): XDebugProcess = JavaDebugProcess.create(session, debuggerSession!!)
        }
        val xSession = XDebuggerManager.getInstance(project).newSessionBuilder(starter)
          .environment(sessionEnvironment)
          .startSession().session as XDebugSessionImpl
        xSession.activateSession(false) // activate the session immediately
      }
      catch (e: ExecutionException) {
        LOG.error(e)
      }
    }, ModalityState.any())
    val process = session!!.process
    createdDebugProcess = process

    process.addProcessListener(object : ProcessListener {
      override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        execution.print(event.text, outputType)
      }
    })

    return session
  }

  /**
   * Starts the debuggee of [javaParameters] and attaches a remote debugger session to it.
   * In the server mode, the debugger listens and the debuggee connects. It does not set [debuggerSession].
   */
  fun createRemoteProcess(transport: Int, serverMode: Boolean, javaParameters: JavaParameters): DebuggerSession {
    val remoteConnection = RemoteConnectionBuilder(serverMode, transport, null)
      .suspend(true)
      .create(javaParameters)

    if (remoteConnection.isUseSockets && !serverMode) {
      // JDWP with address=<port> listens on 127.0.0.1.
      // Use the same address for the debugger, because Bazel tests can otherwise select ::1.
      remoteConnection.debuggerHostName = "127.0.0.1"
    }

    val commandLine = javaParameters.toCommandLine()

    val debuggerSession: DebuggerSession
    if (serverMode) {
      debuggerSession = attachVM(remoteConnection, false)
      commandLine.createProcess()
    }
    else {
      commandLine.createProcess()
      debuggerSession = attachVM(remoteConnection, true)
    }

    val processHandler = debuggerSession.process.processHandler
    val process = DebuggerManagerEx.getInstanceEx(project).getDebugProcess(processHandler)
    assertNotNull(process)
    return debuggerSession
  }

  /** Attaches a debugger session to the remote VM of [remoteConnection]. Its output goes to the checker of [execution]. */
  fun attachVM(remoteConnection: RemoteConnection, pollConnection: Boolean): DebuggerSession {
    val remoteState = RemoteStateState(project, remoteConnection)
    val environment = ExecutionEnvironmentBuilder(project, DefaultDebugExecutor.getDebugExecutorInstance())
      .runProfile(MockConfiguration(project, module))
      .build()
    var debuggerSession: DebuggerSession? = null
    try {
      debuggerSession = attachVirtualMachine(remoteState, environment, remoteConnection, pollConnection)
    }
    catch (e: ExecutionException) {
      fail<Unit>(e.message)
    }
    debuggerSession!!.process.processHandler.addProcessListener(object : ProcessListener {
      override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        execution.print(event.text, outputType)
      }
    })
    return debuggerSession
  }

  /** Attaches a debugger session with [state] and starts its XDebugger session on the EDT. Call it off the EDT. */
  fun attachVirtualMachine(
    state: RunProfileState,
    environment: ExecutionEnvironment,
    remoteConnection: RemoteConnection,
    pollConnection: Boolean,
  ): DebuggerSession {
    assertFalse(EDT.isCurrentThreadEdt())
    val debuggerSession = DebuggerManagerEx.getInstanceEx(project)
      .attachVirtualMachine(DefaultDebugEnvironment(environment, state, remoteConnection, pollConnection))
    assertNotNull(debuggerSession)
    debugProcessCreated = true
    ApplicationManager.getApplication().invokeAndWait({
      try {
        val starter = object : XDebugProcessStarter() {
          override fun start(session: XDebugSession): XDebugProcess = JavaDebugProcess.create(session, debuggerSession!!)
        }
        XDebuggerManager.getInstance(project).newSessionBuilder(starter)
          .environment(environment)
          .startSession()
      }
      catch (e: ExecutionException) {
        fail<Unit>(e.message)
      }
    }, ModalityState.any())
    return debuggerSession!!
  }

  /** Disposes [debuggerSession] on the EDT. */
  fun disposeSession(debuggerSession: DebuggerSession) {
    UIUtil.invokeAndWaitIfNeeded(Runnable { debuggerSession.dispose() })
  }

  /**
   * Prints the frames of the XDebugger session between two marker lines.
   * [collectFrames] gives the frames, and [framePresentation] gives the text of a frame.
   * The defaults are the defaults of [DebuggerTestCase]: no frames, and `toString()`.
   */
  @JvmOverloads
  fun printAsyncStackTrace(
    collectFrames: Function<XDebugSession?, List<XStackFrame>> = Function { emptyList() },
    framePresentation: Function<XStackFrame, String> = Function { it.toString() },
  ) {
    if (!logAllCommands) {
      printContext(debugProcess!!.debuggerContext)
    }
    val frames = collectFrames.apply(debuggerSession!!.xDebugSession)
    systemPrintln("vvv stack trace vvv")
    for (frame in frames) {
      if (frame is XStackFrameWithSeparatorAbove && frame.hasSeparatorAbove()) {
        systemPrintln("-- " + frame.captionAboveOf + " --")
      }
      if (frame is HiddenFramesStackFrame) {
        systemPrintln("  <hidden frames>")
      }
      else {
        systemPrintln("  " + framePresentation.apply(frame))
      }
    }
    systemPrintln("^^^ stack trace ^^^")
  }

  /** Runs [runnable] on a pooled thread each time the XDebugger session pauses. It prints the location when [logAllCommands] is `true`. */
  fun doWhenXSessionPaused(runnable: ThrowableRunnable<Throwable>) {
    doWhenXSessionPaused(runnable, false)
  }

  /** The same as [doWhenXSessionPaused], and then it calls [resume]. */
  fun doWhenXSessionPausedThenResume(runnable: ThrowableRunnable<Throwable>) {
    doWhenXSessionPaused(runnable, true)
  }

  private fun doWhenXSessionPaused(runnable: ThrowableRunnable<Throwable>, thenResume: Boolean) {
    val session = debuggerSession!!.xDebugSession
    assertNotNull(session)
    session!!.addSessionListener(object : XDebugSessionListener {
      override fun sessionPaused() {
        if (logAllCommands) {
          printContext("Stopped at ", debugProcess!!.debuggerContext)
        }
        ApplicationManager.getApplication().executeOnPooledThread(Runnable {
          try {
            runnable.run()
          }
          catch (e: Throwable) {
            addException(e)
          }
          finally {
            if (thenResume) {
              resume()
            }
          }
        })
      }
    })
  }

  /**
   * Sets the registry and the settings that make the JDWP packet count stable, until the end of the test.
   * It turns off the advanced features that cache data, and the DFA assist.
   */
  fun setUpPacketsMeasureTest() {
    execution.setRegistryPropertyForTest("debugger.track.instrumentation", "false")
    execution.setRegistryPropertyForTest("debugger.evaluate.single.threaded.timeout", "-1")
    execution.setRegistryPropertyForTest("debugger.preload.types.async", "false")
    execution.setRegistryPropertyForTest("debugger.preload.types.hierarchy", "false")
    // Enabling these advanced features makes packets number unstable, because they use caching.
    // Packets number tests are targeted for core debugger functionality, so we do not want to mess up with advanced features.
    // A better approach is to add performance tests for these features separately.
    try {
      execution.setRegistryPropertyForTest("debugger.navigation.from.console.to.sources", "off")
      execution.setRegistryPropertyForTest("debugger.log.capture.batched", "false")
    }
    catch (_: MissingResourceException) {
    }

    val settings = ViewsGeneralSettings.getInstance()
    val dfa = settings.USE_DFA_ASSIST
    val dfaGray = settings.USE_DFA_ASSIST_GRAY_OUT
    settings.USE_DFA_ASSIST = false
    settings.USE_DFA_ASSIST_GRAY_OUT = false
    Disposer.register(disposable) {
      settings.USE_DFA_ASSIST = dfa
      settings.USE_DFA_ASSIST_GRAY_OUT = dfaGray
    }
  }

  private fun waitForCompleted() {
    val s = SynchronizationBasedSemaphore()
    s.down()

    val request = debugProcess!!.managerThread.currentRequest
    val thread = object : Thread("Joining $request") {
      override fun run() {
        try {
          request.join()
        }
        catch (_: Exception) {
        }
      }
    }
    thread.start()
    if (request.isDone) {
      thread.interrupt()
    }
    execution.waitFor {
      try {
        thread.join()
      }
      catch (_: InterruptedException) {
      }
    }

    invokeRatherLater(object : DebuggerCommandImpl() {
      override fun action() {
        LOG.assertTrue(false)
      }

      override fun commandCancelled() {
        // We wait for invokeRatherLater's
        invokeRatherLater(object : DebuggerCommandImpl() {
          override fun action() {
            LOG.assertTrue(false)
          }

          override fun commandCancelled() {
            s.up()
          }
        })
      }
    })

    execution.waitFor { s.waitFor() }
    val manager = CompilerManager.getInstance(project) as CompilerManagerImpl
    compilerManager = manager
    manager.waitForExternalJavacToTerminate(1, TimeUnit.MINUTES)
  }

  private fun createMockJavaCommandLineState(
    environment: ExecutionEnvironment,
    parameters: JavaParameters,
    mockConfiguration: MockConfiguration,
  ): JavaCommandLineState {
    return object : JavaCommandLineState(environment) {
      override fun createJavaParameters(): JavaParameters = parameters

      override fun createTargetedCommandLine(request: TargetEnvironmentRequest): TargetedCommandLineBuilder {
        return javaParameters.toCommandLine(request)
      }

      override fun createConsole(executor: Executor): ConsoleView? {
        val console = super.createConsole(executor)
        return if (console == null) null else JavaConsoleDecorator.decorate(console, mockConfiguration, executor)
      }
    }
  }

  private class InvokeRatherLaterRequest(val debuggerCommand: DebuggerCommandImpl, val debugProcess: DebugProcessImpl) {
    var invokesN: Int = 0
  }

  private inner class BreakpointProvider(private val debugProcess: DebugProcessImpl) : DebugProcessAdapterImpl() {
    private val repeatingRunnables = ArrayList<SuspendContextRunnable>()
    private val scriptRunnables = ArrayDeque<SuspendContextRunnable>()
    private var breakpointCount = 0

    fun onBreakpoint(runnable: SuspendContextRunnable) {
      scriptRunnables.add(runnable)
      breakpointCount++
    }

    fun onEveryBreakpoint(runnable: SuspendContextRunnable) {
      repeatingRunnables.add(runnable)
    }

    override fun paused(suspendContext: SuspendContextImpl) {
      // Need to add SuspendContextCommandImpl because the stepping pause is not now in SuspendContextCommandImpl
      pausedWrapper(suspendContext)
    }

    fun pausedWrapper(suspendContext: SuspendContextImpl) {
      if (DebugProcessImpl.isInSuspendCommand(suspendContext)) {
        pausedImpl(suspendContext)
      }
      else {
        suspendContext.managerThread.invokeNow(object : SuspendContextCommandImpl(suspendContext) {
          override fun contextAction(suspendContext: SuspendContextImpl) {
            pausedImpl(suspendContext)
          }
        })
      }
    }

    private fun pausedImpl(suspendContext: SuspendContextImpl) {
      try {
        if (logAllCommands) {
          printContext("Stopped at ", suspendContext)
        }

        if (scriptRunnables.isEmpty() && repeatingRunnables.isEmpty()) {
          if (!logAllCommands) { // for compatibility
            execution.print("resuming ", ProcessOutputTypes.SYSTEM)
            printContext(suspendContext)
          }
          resume(suspendContext)
          return
        }
        scriptRunnables.poll()?.run(suspendContext)
        for (runnable in repeatingRunnables) {
          runnable.run(suspendContext)
        }
      }
      catch (e: Exception) {
        addException(e)
        error(e)
      }
      catch (e: AssertionError) {
        addException(e)
        resume(suspendContext)
      }
    }

    // executed in manager thread
    override fun resumed(suspendContext: SuspendContextImpl) {
      val pausedContext = debugProcess.suspendManager.pausedContext
      // do not switch context on resume inside stepping
      if (pausedContext != null && !debugProcess.isSteppingInProgress) {
        paused(pausedContext)
      }
    }

    fun assertAllBreakpointsHit() {
      if (breakpointCount > 0 && !scriptRunnables.isEmpty()) {
        fail<Unit>(MessageFormat.format("{0} from {1} breakpoints are not hit", scriptRunnables.size, breakpointCount))
      }
    }
  }

  companion object {
    /** The port of a debug process with the shared memory transport. */
    const val DEFAULT_ADDRESS: Int = 3456

    /** The name of the internal JDK that the fixture registers. */
    const val TEST_JDK_NAME: String = "JDK"

    /** The number of times that [invokeRatherLater] passes a command between the EDT and the debugger manager thread. */
    const val RATHER_LATER_INVOKES_N: Int = 10

    private val LOG = Logger.getInstance(DebuggerTestFixture::class.java)

    /** The file name and the 1-based line of [sourcePosition], or "unknown source position". */
    @JvmStatic
    @RequiresReadLock
    fun toDisplayableString(sourcePosition: SourcePosition?): String {
      if (sourcePosition == null) return "unknown source position"
      var line = sourcePosition.line
      if (line >= 0) {
        line++
      }
      return sourcePosition.file.virtualFile.name + ":" + line
    }

    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    private fun hasDispatchedCommands(managerThread: DebuggerManagerThreadImpl): Boolean = managerThread.hasDispatchedCommands()
  }
}
