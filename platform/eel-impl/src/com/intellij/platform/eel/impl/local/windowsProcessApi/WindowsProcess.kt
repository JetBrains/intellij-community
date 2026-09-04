// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local.windowsProcessApi

import com.intellij.execution.Platform
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelExecApi
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.ref.Cleaner
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * A [Process] started by
 * [CreateProcessW](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-createprocessw).
 *
 * The JVM builds the command line itself, so `ProcessBuilder` cannot pass one raw string to Windows. This class can, and it also
 * reaches two flags the JVM hides: `CREATE_SUSPENDED` and `CREATE_NEW_CONSOLE`.
 *
 * The design follows the .NET implementation of `System.Diagnostics.Process` on Windows. Every Win32 call goes through [Kernel32].
 *
 * @param exePath the file to run, passed as `lpApplicationName`. Windows searches neither `PATH` nor the working directory for it.
 * @param commandLine passed as `lpCommandLine` without any change. Its first token becomes the child's `argv[0]`.
 * @param environment the whole environment of the child, or `null` to inherit the environment of this process.
 * @param directory the working directory, or `null` to inherit the working directory of this process.
 * @param redirection where the three standard streams go.
 * @param startSuspended creates the process with `CREATE_SUSPENDED`. Call [resumeSuspended] to start it.
 * @param newConsole gives the process a console of its own instead of hiding it. The console owns all three standard streams, so
 *   [redirection] must be [Redirection.PIPES], and this [Process] then exposes empty streams.
 */
internal class WindowsProcess(
  exePath: String,
  commandLine: String,
  private val environment: Map<String, String>?,
  directory: String?,
  redirection: Redirection,
  startSuspended: Boolean = false,
  newConsole: Boolean = false,
) : Process() {
  private val processHandle: MemorySegment
  private val threadHandle: MemorySegment
  private val processId: Long
  private val processInputStream: InputStream
  private val processOutputStream: OutputStream
  private val processErrStream: InputStream
  private val procHandle: ProcessHandle?
  private val cleanable: Cleaner.Cleanable

  /** `true` while the process waits for [resumeSuspended]. */
  var isSuspended: Boolean = startSuspended
    private set

  /** What this object knows about the end of the process. */
  private sealed interface State {
    /** The process can still run. */
    data object Running : State

    /** `WaitForSingleObject` saw the process handle signaled, and `GetExitCodeProcess` gave [code]. */
    data class Exited(val code: Int) : State
  }

  /**
   * A dead process stays dead, so remember the exit when the OS reports it. Then [closeHandles] can run at once, and [exitValue]
   * keeps answering afterwards. Only [recordExit] sets this field.
   */
  @Volatile
  private var state: State = State.Running

  /** Where each standard stream of the child is connected. */
  class Redirection(
    val stdIn: EelExecApi.Redirect,
    val stdOut: EelExecApi.Redirect,
    val stdErr: EelExecApi.RedirectStdErrTo,
  ) {
    val isAllPipes: Boolean
      get() = stdIn == EelExecApi.Redirect.Pipe &&
              stdOut == EelExecApi.Redirect.Pipe &&
              stdErr == EelExecApi.RedirectStdErrTo.Pipe

    companion object {
      val PIPES: Redirection = Redirection(EelExecApi.Redirect.Pipe, EelExecApi.Redirect.Pipe, EelExecApi.RedirectStdErrTo.Pipe)
    }
  }

  init {
    require(Platform.current() == Platform.WINDOWS) { "WindowsProcess works on Windows only" }
    require(!newConsole || redirection.isAllPipes) { "A process with a console of its own owns all three standard streams" }

    // The parent ends stay open and become the streams of this object. The child ends and the opened files close as soon as
    // CreateProcessW returns, because from that moment only the child needs them.
    val parentEnds = ParentEnds()
    val childHandles = mutableListOf<MemorySegment>()

    // CreateProcessW inherits either the three handles named here or nothing at all, and inheriting nothing would hand the child
    // the standard handles of the IDE. So name all three whenever the child has no console of its own.
    val useStdHandles = !newConsole
    Arena.ofConfined().use { arena ->
      val callState = arena.allocate(Kernel32.CALL_STATE)
      val startupInfo = arena.allocate(Kernel32.STARTUPINFOW)
      startupInfo.set(JAVA_INT, Kernel32.STARTUPINFOW_CB_OFFSET, Kernel32.STARTUPINFOW.byteSize().toInt())
      val processInfo = arena.allocate(Kernel32.PROCESS_INFORMATION)
      try {
        fun createProcess(): Boolean {
          if (useStdHandles) {
            val stdInHandle = when (val stdIn = redirection.stdIn) {
              EelExecApi.Redirect.Pipe -> createPipe(arena,
                                                     callState,
                                                     childHandles,
                                                     parentReads = false,
                                                     name = "stdin") { parentEnds.stdIn = it }
              EelExecApi.Redirect.Null -> openForReading(arena, callState, NUL_DEVICE, childHandles)
              is EelExecApi.Redirect.File -> openForReading(arena, callState, stdIn.path.toString(), childHandles)
            }
            val stdOutHandle = when (val stdOut = redirection.stdOut) {
              EelExecApi.Redirect.Pipe -> createPipe(arena,
                                                     callState,
                                                     childHandles,
                                                     parentReads = true,
                                                     name = "stdout") { parentEnds.stdOut = it }
              EelExecApi.Redirect.Null -> openForWriting(arena, callState, NUL_DEVICE, childHandles)
              is EelExecApi.Redirect.File -> openForWriting(arena, callState, stdOut.path.toString(), childHandles)
            }
            val stdErrHandle = when (val stdErr = redirection.stdErr) {
              EelExecApi.RedirectStdErrTo.Pipe -> createPipe(arena,
                                                             callState,
                                                             childHandles,
                                                             parentReads = true,
                                                             name = "stderr") { parentEnds.stdErr = it }
              EelExecApi.RedirectStdErrTo.Null -> openForWriting(arena, callState, NUL_DEVICE, childHandles)
              EelExecApi.RedirectStdErrTo.Stdout -> stdOutHandle
              is EelExecApi.RedirectStdErrTo.File -> openForWriting(arena, callState, stdErr.path.toString(), childHandles)
            }
            startupInfo.set(ADDRESS, Kernel32.STARTUPINFOW_STD_INPUT_OFFSET, stdInHandle)
            startupInfo.set(ADDRESS, Kernel32.STARTUPINFOW_STD_OUTPUT_OFFSET, stdOutHandle)
            startupInfo.set(ADDRESS, Kernel32.STARTUPINFOW_STD_ERROR_OFFSET, stdErrHandle)
            startupInfo.set(JAVA_INT, Kernel32.STARTUPINFOW_FLAGS_OFFSET, Kernel32.STARTF_USESTDHANDLES)
          }

          val creationFlags = Kernel32.CREATE_UNICODE_ENVIRONMENT or
            (if (startSuspended) Kernel32.CREATE_SUSPENDED else 0) or
            (if (newConsole) Kernel32.CREATE_NEW_CONSOLE or Kernel32.CREATE_NEW_PROCESS_GROUP else Kernel32.CREATE_NO_WINDOW)

          val created = Kernel32.createProcess(
            callState = callState,
            application = arena.allocateFrom(exePath, StandardCharsets.UTF_16LE),
            // CreateProcessW may write into lpCommandLine, so give it a buffer of its own.
            commandLine = arena.allocateFrom(commandLine.trimEnd(Char.MIN_VALUE), StandardCharsets.UTF_16LE),
            inheritHandles = useStdHandles,
            creationFlags = creationFlags,
            environment = environmentBlock(arena),
            currentDirectory = directory?.let { arena.allocateFrom(it, StandardCharsets.UTF_16LE) } ?: MemorySegment.NULL,
            startupInfo = startupInfo,
            processInformation = processInfo,
          )
          return created
        }

        // To avoid handle leaks, we need to synchronize on processCreationGlobalLock when using std handles.
        // See: https://web.archive.org/web/20130610174104/http://support.microsoft.com/kb/315939
        val created = if(useStdHandles) synchronized(processCreationGlobalLock) { createProcess() } else createProcess()

        if (!created) {
          val lastError = Kernel32.lastError(callState)
          throw WindowsProcessCreationException(Kernel32.formatWin32Error("CreateProcessW", lastError), lastError)
        }
      }
      catch (e: Throwable) {
        // Nobody will ever read the parent ends, so close them here instead of leaking them.
        closeHandle(callState, parentEnds.stdIn)
        closeHandle(callState, parentEnds.stdOut)
        closeHandle(callState, parentEnds.stdErr)
        throw when (e) {
          is WindowsProcessCreationException -> e
          else -> WindowsProcessCreationException("Failed to create a process: ${e.message}", 0, e)
        }
      }
      finally {
        for (handle in childHandles) {
          closeHandle(callState, handle)
        }
      }

      processHandle = processInfo.get(ADDRESS, Kernel32.PROCESS_INFORMATION_PROCESS_OFFSET)
      threadHandle = processInfo.get(ADDRESS, Kernel32.PROCESS_INFORMATION_THREAD_OFFSET)
      processId = processInfo.get(JAVA_INT, Kernel32.PROCESS_INFORMATION_PROCESS_ID_OFFSET).toLong() and 0xFFFF_FFFFL
    }

    processOutputStream = if (parentEnds.stdIn.address() != 0L) WindowsOutputStream(parentEnds.stdIn) else NullOutputStream
    processInputStream = if (parentEnds.stdOut.address() != 0L) WindowsInputStream(parentEnds.stdOut) else NullInputStream
    processErrStream = if (parentEnds.stdErr.address() != 0L) WindowsInputStream(parentEnds.stdErr) else NullInputStream

    procHandle = ProcessHandle.of(processId).orElse(null)
    cleanable = cleaner.register(this, CloseHandles(processHandle, threadHandle))
  }

  /**
   * Starts the main thread of a process created with `CREATE_SUSPENDED`.
   *
   * @throws IOException when the process was not suspended, was resumed already, or the OS refuses to resume it.
   */
  @Throws(IOException::class)
  fun resumeSuspended() {
    if (!isSuspended) {
      throw IOException("The process $processId is not suspended")
    }
    Arena.ofConfined().use { arena ->
      val callState = arena.allocate(Kernel32.CALL_STATE)
      // ResumeThread answers the previous suspend count, and -1 on failure.
      val previousSuspendCount = Kernel32.resumeThread(callState, threadHandle)
      if (previousSuspendCount == -1) {
        val lastError = Kernel32.lastError(callState)
        throw WindowsProcessException(Kernel32.formatWin32Error("ResumeThread", lastError), lastError)
      }
    }
    isSuspended = false
  }

  /**
   * Closes the process and the thread handle. It does not stop the process. Idempotent, and the [Cleaner] calls it anyway once this
   * object becomes unreachable.
   *
   * Call it only after [waitFor], [isAlive] or [exitValue] saw the process exit. This object then remembers the exit code. Before
   * that, every later call that needs the process handle fails.
   */
  fun closeHandles() {
    cleanable.clean()
  }

  override fun getOutputStream(): OutputStream = processOutputStream

  override fun getInputStream(): InputStream = processInputStream

  override fun getErrorStream(): InputStream = processErrStream

  override fun toHandle(): ProcessHandle? = procHandle

  override fun supportsNormalTermination(): Boolean = false

  override fun waitFor(): Int {
    when (val known = state) {
      is State.Exited -> return known.code
      State.Running -> Unit
    }
    Arena.ofConfined().use { arena ->
      val callState = arena.allocate(Kernel32.CALL_STATE)
      when (val result = Kernel32.waitForSingleObject(callState, processHandle, Kernel32.INFINITE)) {
        Kernel32.WAIT_OBJECT_0 -> Unit
        Kernel32.WAIT_FAILED -> throwWaitFailed(callState)
        else -> throw WindowsProcessException("WaitForSingleObject answered $result", 0)
      }
    }
    return recordExit().code
  }

  override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
    when (state) {
      is State.Exited -> return true
      State.Running -> Unit
    }
    // INFINITE is 0xFFFFFFFF, so keep the timeout below it to never wait forever by accident.
    val millis = unit.toMillis(timeout).coerceIn(0L, 0xFFFF_FFFEL)
    Arena.ofConfined().use { arena ->
      val callState = arena.allocate(Kernel32.CALL_STATE)
      return when (val result = Kernel32.waitForSingleObject(callState, processHandle, millis.toInt())) {
        Kernel32.WAIT_OBJECT_0 -> {
          recordExit()
          true
        }
        Kernel32.WAIT_TIMEOUT -> false
        Kernel32.WAIT_FAILED -> throwWaitFailed(callState)
        else -> throw WindowsProcessException("WaitForSingleObject answered $result", 0)
      }
    }
  }

  override fun isAlive(): Boolean =
    when (currentState()) {
      State.Running -> true
      is State.Exited -> false
    }

  override fun exitValue(): Int =
    when (val current = currentState()) {
      State.Running -> throw IllegalThreadStateException("The process $processId is still alive")
      is State.Exited -> current.code
    }

  override fun destroy() {
    if (!isAlive) return
    Arena.ofConfined().use { arena ->
      val callState = arena.allocate(Kernel32.CALL_STATE)
      // -1 keeps the forced exit code apart from a real one. Some tools answer a small positive number, f.i. robocopy answers 1.
      if (!Kernel32.terminateProcess(callState, processHandle, -1)) {
        val lastError = Kernel32.lastError(callState)
        logger.info(Kernel32.formatWin32Error("TerminateProcess for pid $processId", lastError))
        return
      }
    }
    // TerminateProcess only starts the termination, so do not touch the state here. The next isAlive or waitFor sees the exit.
  }

  override fun destroyForcibly(): Process {
    destroy()
    return this
  }

  /** Answers the remembered exit, or asks the OS without a wait whether the process exited. */
  private fun currentState(): State =
    when (val known = state) {
      is State.Exited -> known
      State.Running -> if (isProcessAlive(processHandle)) State.Running else recordExit()
    }

  /**
   * Reads the exit code and remembers it in [state]. Call it only after `WaitForSingleObject` saw the process handle signaled.
   * Before that, `GetExitCodeProcess` answers `STILL_ACTIVE`.
   */
  private fun recordExit(): State.Exited {
    Arena.ofConfined().use { arena ->
      val callState = arena.allocate(Kernel32.CALL_STATE)
      val exitCode = arena.allocate(JAVA_INT)
      if (!Kernel32.getExitCodeProcess(callState, processHandle, exitCode)) {
        val lastError = Kernel32.lastError(callState)
        throw WindowsProcessException(Kernel32.formatWin32Error("GetExitCodeProcess", lastError), lastError)
      }
      val exited = State.Exited(exitCode.get(JAVA_INT, 0))
      state = exited
      return exited
    }
  }

  private fun throwWaitFailed(callState: MemorySegment): Nothing {
    val lastError = Kernel32.lastError(callState)
    throw WindowsProcessException(Kernel32.formatWin32Error("WaitForSingleObject", lastError), lastError)
  }

  /**
   * Builds the environment block: `K=V` and a null character for every variable, then one more null character. Windows wants the
   * names sorted, and it compares them without regard to case, so sort them the same way.
   */
  private fun environmentBlock(arena: Arena): MemorySegment {
    if (environment.isNullOrEmpty()) return MemorySegment.NULL
    val sorted = sortedMapOf<String, String>(String.CASE_INSENSITIVE_ORDER).apply { putAll(environment) }
    // `allocateFrom` appends the final null character itself.
    val text = sorted.entries.joinToString("") { (key, value) -> "$key=$value${Char.MIN_VALUE}" }
    return arena.allocateFrom(text, StandardCharsets.UTF_16LE)
  }

  /**
   * Creates an anonymous pipe, gives the parent end to [keepParentEnd] and answers the child end.
   *
   * @param parentReads `true` for a stream the child writes and the parent reads, f.i. `stdout`.
   */
  private fun createPipe(
    arena: Arena,
    callState: MemorySegment,
    childHandles: MutableList<MemorySegment>,
    parentReads: Boolean,
    name: String,
    keepParentEnd: (MemorySegment) -> Unit,
  ): MemorySegment {
    val childHandleRef = arena.allocate(ADDRESS)
    val parentEndRef = arena.allocate(ADDRESS)
    val readEnd = if (parentReads) parentEndRef else childHandleRef
    val writeEnd = if (parentReads) childHandleRef else parentEndRef

    if (!Kernel32.createPipe(callState, readEnd, writeEnd, inheritableSecurityAttributes(arena))) {
      val lastError = Kernel32.lastError(callState)
      throw WindowsProcessCreationException(Kernel32.formatWin32Error("CreatePipe for $name", lastError), lastError)
    }
    val childHandle = childHandleRef.get(ADDRESS, 0)
    childHandles.add(childHandle)
    val parentEnd = parentEndRef.get(ADDRESS, 0)

    try {
      // Both ends are inheritable so far. Duplicate the parent end into a handle the child cannot see. Otherwise the child holds
      // the parent end too, and the pipe never breaks when the child exits.
      val parentHandleRef = arena.allocate(ADDRESS)
      if (!Kernel32.duplicateHandle(callState, parentEnd, parentHandleRef)) {
        val lastError = Kernel32.lastError(callState)
        throw WindowsProcessCreationException(Kernel32.formatWin32Error("DuplicateHandle for $name", lastError), lastError)
      }
      keepParentEnd(parentHandleRef.get(ADDRESS, 0))
    }
    finally {
      closeHandle(callState, parentEnd)
    }

    return childHandle
  }

  private fun openForReading(arena: Arena, callState: MemorySegment, path: String, childHandles: MutableList<MemorySegment>): MemorySegment =
    openFile(
      arena = arena,
      callState = callState,
      path = path,
      access = Kernel32.GENERIC_READ,
      disposition = Kernel32.OPEN_EXISTING,
      childHandles = childHandles,
    )

  /** Opens a file for the child to write. An existing file is truncated, much like `ProcessBuilder.Redirect.to`. */
  private fun openForWriting(arena: Arena, callState: MemorySegment, path: String, childHandles: MutableList<MemorySegment>): MemorySegment =
    openFile(
      arena = arena,
      callState = callState,
      path = path,
      access = Kernel32.GENERIC_WRITE,
      disposition = if (path == NUL_DEVICE) Kernel32.OPEN_EXISTING else Kernel32.CREATE_ALWAYS,
      childHandles = childHandles,
    )

  private fun openFile(
    arena: Arena,
    callState: MemorySegment,
    path: String,
    access: Int,
    disposition: Int,
    childHandles: MutableList<MemorySegment>,
  ): MemorySegment {
    val handle = Kernel32.createFile(
      callState = callState,
      name = arena.allocateFrom(path, StandardCharsets.UTF_16LE),
      access = access,
      shareMode = Kernel32.FILE_SHARE_READ or Kernel32.FILE_SHARE_WRITE,
      securityAttributes = inheritableSecurityAttributes(arena),
      disposition = disposition,
      flags = Kernel32.FILE_ATTRIBUTE_NORMAL,
    )
    if (Kernel32.isInvalidHandle(handle)) {
      val lastError = Kernel32.lastError(callState)
      throw WindowsProcessCreationException(Kernel32.formatWin32Error("CreateFile for $path", lastError), lastError)
    }
    childHandles.add(handle)
    return handle
  }

  /** Static on purpose, so the [Cleaner] action never holds the [WindowsProcess] it belongs to. */
  private class CloseHandles(private val processHandle: MemorySegment, private val threadHandle: MemorySegment) : Runnable {
    override fun run() {
      Arena.ofConfined().use { arena ->
        val callState = arena.allocate(Kernel32.CALL_STATE)
        closeHandle(callState, processHandle)
        closeHandle(callState, threadHandle)
      }
    }
  }

  /** The parent ends of the pipes. A stream that is not a pipe keeps [MemorySegment.NULL]. */
  private class ParentEnds {
    var stdIn: MemorySegment = MemorySegment.NULL
    var stdOut: MemorySegment = MemorySegment.NULL
    var stdErr: MemorySegment = MemorySegment.NULL
  }

  private object NullInputStream : InputStream() {
    override fun read(): Int = -1
    override fun available(): Int = 0
  }

  private object NullOutputStream : OutputStream() {
    override fun write(b: Int): Unit = throw IOException("Stream closed")
  }

  companion object {
    private val logger = logger<WindowsProcess>()

    /** Creating a [Cleaner] spawns a thread, so share one for every process. */
    private val cleaner: Cleaner by lazy { Cleaner.create() }

    private const val NUL_DEVICE = "NUL"

    /** Answers whether the process behind [processHandle] still runs. */
    private fun isProcessAlive(processHandle: MemorySegment): Boolean {
      Arena.ofConfined().use { arena ->
        val callState = arena.allocate(Kernel32.CALL_STATE)
        return when (val result = Kernel32.waitForSingleObject(callState, processHandle, 0)) {
          Kernel32.WAIT_OBJECT_0 -> false
          Kernel32.WAIT_TIMEOUT -> true
          Kernel32.WAIT_ABANDONED -> throw IllegalStateException("WaitForSingleObject answered WAIT_ABANDONED")
          Kernel32.WAIT_FAILED -> {
            val lastError = Kernel32.lastError(callState)
            throw WindowsProcessException(Kernel32.formatWin32Error("WaitForSingleObject", lastError), lastError)
          }
          else -> throw WindowsProcessException("WaitForSingleObject answered $result", 0)
        }
      }
    }

    /** `SECURITY_ATTRIBUTES` with `bInheritHandle = TRUE`, so the child receives the handle. */
    private fun inheritableSecurityAttributes(arena: Arena): MemorySegment =
      arena.allocate(Kernel32.SECURITY_ATTRIBUTES).apply {
        set(JAVA_INT, Kernel32.SECURITY_ATTRIBUTES_LENGTH_OFFSET, Kernel32.SECURITY_ATTRIBUTES.byteSize().toInt())
        set(JAVA_INT, Kernel32.SECURITY_ATTRIBUTES_INHERIT_HANDLE_OFFSET, 1)
      }

    /** Closes [handle] unless it is `NULL` or `INVALID_HANDLE_VALUE`. A failure goes to the log, because nothing else can be done. */
    private fun closeHandle(callState: MemorySegment, handle: MemorySegment) {
      if (Kernel32.isInvalidHandle(handle)) return
      if (!Kernel32.closeHandle(callState, handle)) {
        logger.info(Kernel32.formatWin32Error("CloseHandle", Kernel32.lastError(callState)))
      }
    }
  }
}

/** `CreateProcessW`, or the preparation of its arguments, failed. [errorCode] is the Win32 code, or 0 when there is none. */
@ApiStatus.Internal
class WindowsProcessCreationException(message: String, val errorCode: Int, cause: Throwable? = null) : IOException(message, cause)

/** An operation on a running [WindowsProcess] failed. [errorCode] is the Win32 code, or 0 when there is none. */
@ApiStatus.Internal
class WindowsProcessException(message: String, val errorCode: Int, cause: Throwable? = null) : IOException(message, cause)

private val processCreationGlobalLock = Any()