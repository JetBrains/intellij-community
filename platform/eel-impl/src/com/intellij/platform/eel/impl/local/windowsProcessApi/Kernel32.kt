// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local.windowsProcessApi

import com.intellij.util.system.WindowsKernel32
import com.intellij.util.system.WindowsSystemLibraries
import org.jetbrains.annotations.ApiStatus
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemoryLayout.PathElement.groupElement
import java.lang.foreign.MemorySegment
import java.lang.foreign.StructLayout
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_CHAR
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_SHORT
import java.lang.invoke.MethodHandle

/**
 * The `kernel32.dll` downcalls of a [WindowsProcess] and its streams. Windows only: the first call loads the library.
 *
 * A `HANDLE` and every pointer travel as an address. A `BOOL` and a `DWORD` travel as an `int`. A call that can fail takes a
 * call-state segment of [CALL_STATE] first and writes its `GetLastError` code there. Read the code with [lastError].
 *
 * The layouts are for a 64-bit target: a pointer follows a 4-byte field after 4 bytes of padding. They are plain data and load on
 * every OS. The calls that more than one class needs live in [WindowsKernel32].
 */
@ApiStatus.Internal
object Kernel32 {
  /** The layout of the state that a call captures. Allocate one segment of it per call sequence. */
  val CALL_STATE: StructLayout = WindowsKernel32.CALL_STATE

  /** `SECURITY_ATTRIBUTES { DWORD nLength; LPVOID lpSecurityDescriptor; BOOL bInheritHandle; }`: 24 bytes. */
  val SECURITY_ATTRIBUTES: StructLayout = MemoryLayout.structLayout(
    JAVA_INT.withName("nLength"),
    MemoryLayout.paddingLayout(4),
    ADDRESS.withName("lpSecurityDescriptor"),
    JAVA_INT.withName("bInheritHandle"),
    MemoryLayout.paddingLayout(4),
  )
  val SECURITY_ATTRIBUTES_LENGTH_OFFSET: Long = SECURITY_ATTRIBUTES.byteOffset(groupElement("nLength"))
  val SECURITY_ATTRIBUTES_INHERIT_HANDLE_OFFSET: Long = SECURITY_ATTRIBUTES.byteOffset(groupElement("bInheritHandle"))

  /** `PROCESS_INFORMATION { HANDLE hProcess; HANDLE hThread; DWORD dwProcessId; DWORD dwThreadId; }`: 24 bytes. */
  val PROCESS_INFORMATION: StructLayout = MemoryLayout.structLayout(
    ADDRESS.withName("hProcess"),
    ADDRESS.withName("hThread"),
    JAVA_INT.withName("dwProcessId"),
    JAVA_INT.withName("dwThreadId"),
  )
  val PROCESS_INFORMATION_PROCESS_OFFSET: Long = PROCESS_INFORMATION.byteOffset(groupElement("hProcess"))
  val PROCESS_INFORMATION_THREAD_OFFSET: Long = PROCESS_INFORMATION.byteOffset(groupElement("hThread"))
  val PROCESS_INFORMATION_PROCESS_ID_OFFSET: Long = PROCESS_INFORMATION.byteOffset(groupElement("dwProcessId"))

  /**
   * `STARTUPINFOW` from
   * [processthreadsapi.h](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/ns-processthreadsapi-startupinfow):
   * 104 bytes.
   */
  val STARTUPINFOW: StructLayout = MemoryLayout.structLayout(
    JAVA_INT.withName("cb"),
    MemoryLayout.paddingLayout(4),
    ADDRESS.withName("lpReserved"),
    ADDRESS.withName("lpDesktop"),
    ADDRESS.withName("lpTitle"),
    JAVA_INT.withName("dwX"),
    JAVA_INT.withName("dwY"),
    JAVA_INT.withName("dwXSize"),
    JAVA_INT.withName("dwYSize"),
    JAVA_INT.withName("dwXCountChars"),
    JAVA_INT.withName("dwYCountChars"),
    JAVA_INT.withName("dwFillAttribute"),
    JAVA_INT.withName("dwFlags"),
    JAVA_SHORT.withName("wShowWindow"),
    JAVA_SHORT.withName("cbReserved2"),
    MemoryLayout.paddingLayout(4),
    ADDRESS.withName("lpReserved2"),
    ADDRESS.withName("hStdInput"),
    ADDRESS.withName("hStdOutput"),
    ADDRESS.withName("hStdError"),
  )
  val STARTUPINFOW_CB_OFFSET: Long = STARTUPINFOW.byteOffset(groupElement("cb"))
  val STARTUPINFOW_FLAGS_OFFSET: Long = STARTUPINFOW.byteOffset(groupElement("dwFlags"))
  val STARTUPINFOW_STD_INPUT_OFFSET: Long = STARTUPINFOW.byteOffset(groupElement("hStdInput"))
  val STARTUPINFOW_STD_OUTPUT_OFFSET: Long = STARTUPINFOW.byteOffset(groupElement("hStdOutput"))
  val STARTUPINFOW_STD_ERROR_OFFSET: Long = STARTUPINFOW.byteOffset(groupElement("hStdError"))

  /** `STARTF_USESTDHANDLES`: the three standard handles of the `STARTUPINFOW` are in use. */
  const val STARTF_USESTDHANDLES: Int = 0x100

  /** `CREATE_UNICODE_ENVIRONMENT`: the environment block holds UTF-16 characters. */
  const val CREATE_UNICODE_ENVIRONMENT: Int = 0x400
  const val CREATE_SUSPENDED: Int = 0x4
  const val CREATE_NEW_CONSOLE: Int = 0x10
  const val CREATE_NEW_PROCESS_GROUP: Int = 0x200
  const val CREATE_NO_WINDOW: Int = 0x08000000

  const val GENERIC_READ: Int = 0x80000000.toInt()
  const val GENERIC_WRITE: Int = 0x40000000
  const val FILE_SHARE_READ: Int = 0x1
  const val FILE_SHARE_WRITE: Int = 0x2
  const val CREATE_ALWAYS: Int = 2
  const val OPEN_EXISTING: Int = 3
  const val FILE_ATTRIBUTE_NORMAL: Int = 0x80

  /** `DUPLICATE_SAME_ACCESS` from `winnt.h`: the copy gets the access of the source handle. */
  const val DUPLICATE_SAME_ACCESS: Int = 0x2

  /** `INFINITE`: `WaitForSingleObject` waits until the object signals. */
  const val INFINITE: Int = -1
  const val WAIT_OBJECT_0: Int = 0
  const val WAIT_ABANDONED: Int = 0x80
  const val WAIT_TIMEOUT: Int = 0x102
  const val WAIT_FAILED: Int = -1

  /** `ERROR_BROKEN_PIPE`: the other end of the pipe closed. */
  const val ERROR_BROKEN_PIPE: Int = 109

  private const val FORMAT_MESSAGE_FROM_SYSTEM = 0x1000
  private const val FORMAT_MESSAGE_IGNORE_INSERTS = 0x200
  private const val MESSAGE_BUFFER_CHARS = 1024L

  /** @return the `GetLastError` code that the last call wrote into [callState] */
  fun lastError(callState: MemorySegment): Int = WindowsKernel32.lastError(callState)

  /** @return `true` when [handle] is `INVALID_HANDLE_VALUE` or `NULL` */
  fun isInvalidHandle(handle: MemorySegment): Boolean =
    handle.address() == WindowsKernel32.INVALID_HANDLE_VALUE || handle.address() == 0L

  /** `HANDLE GetCurrentProcess()`: a pseudo handle of the current process. It needs no close. */
  fun currentProcess(): MemorySegment = WindowsKernel32.currentProcess()

  /** `BOOL CloseHandle(HANDLE)`. @return `true` when the call closed the handle */
  fun closeHandle(callState: MemorySegment, handle: MemorySegment): Boolean = WindowsKernel32.closeHandle(callState, handle)

  /**
   * `BOOL CreateProcessW(LPCWSTR application, LPWSTR commandLine, LPSECURITY_ATTRIBUTES, LPSECURITY_ATTRIBUTES, BOOL inheritHandles,
   * DWORD creationFlags, LPVOID environment, LPCWSTR currentDirectory, LPSTARTUPINFOW, LPPROCESS_INFORMATION)`, with no security
   * attributes for the process and the thread.
   *
   * @param commandLine a writable UTF-16LE string, because the call can change it
   * @param environment a UTF-16LE environment block, or [MemorySegment.NULL] to inherit the environment of this process
   * @param currentDirectory a UTF-16LE path, or [MemorySegment.NULL] to inherit the working directory of this process
   */
  fun createProcess(
    callState: MemorySegment,
    application: MemorySegment,
    commandLine: MemorySegment,
    inheritHandles: Boolean,
    creationFlags: Int,
    environment: MemorySegment,
    currentDirectory: MemorySegment,
    startupInfo: MemorySegment,
    processInformation: MemorySegment,
  ): Boolean =
    Handles.CREATE_PROCESS.invokeExact(
      callState, application, commandLine, MemorySegment.NULL, MemorySegment.NULL, if (inheritHandles) 1 else 0, creationFlags,
      environment, currentDirectory, startupInfo, processInformation,
    ) as Int != 0

  /** `BOOL CreatePipe(PHANDLE readPipe, PHANDLE writePipe, LPSECURITY_ATTRIBUTES, DWORD size)`, with the default buffer size. */
  fun createPipe(callState: MemorySegment, readPipe: MemorySegment, writePipe: MemorySegment, pipeAttributes: MemorySegment): Boolean =
    Handles.CREATE_PIPE.invokeExact(callState, readPipe, writePipe, pipeAttributes, 0) as Int != 0

  /**
   * `BOOL DuplicateHandle(HANDLE sourceProcess, HANDLE source, HANDLE targetProcess, LPHANDLE target, DWORD access, BOOL inherit,
   * DWORD options)`, inside the current process, with `DUPLICATE_SAME_ACCESS` and no inheritance.
   */
  fun duplicateHandle(callState: MemorySegment, source: MemorySegment, target: MemorySegment): Boolean {
    val currentProcess = currentProcess()
    return Handles.DUPLICATE_HANDLE.invokeExact(
      callState, currentProcess, source, currentProcess, target, 0, 0, DUPLICATE_SAME_ACCESS,
    ) as Int != 0
  }

  /**
   * `HANDLE CreateFileW(LPCWSTR name, DWORD access, DWORD shareMode, LPSECURITY_ATTRIBUTES, DWORD disposition, DWORD flags,
   * HANDLE template)`, with no template file.
   *
   * @return the handle, or a handle that [isInvalidHandle] accepts
   */
  fun createFile(
    callState: MemorySegment,
    name: MemorySegment,
    access: Int,
    shareMode: Int,
    securityAttributes: MemorySegment,
    disposition: Int,
    flags: Int,
  ): MemorySegment =
    Handles.CREATE_FILE.invokeExact(
      callState, name, access, shareMode, securityAttributes, disposition, flags, MemorySegment.NULL,
    ) as MemorySegment

  /** `BOOL ReadFile(HANDLE, LPVOID buffer, DWORD bytesToRead, LPDWORD bytesRead, LPOVERLAPPED)`, without an overlapped operation. */
  fun readFile(callState: MemorySegment, handle: MemorySegment, buffer: MemorySegment, bytesToRead: Int, bytesRead: MemorySegment): Boolean =
    Handles.READ_FILE.invokeExact(callState, handle, buffer, bytesToRead, bytesRead, MemorySegment.NULL) as Int != 0

  /** `BOOL WriteFile(HANDLE, LPCVOID buffer, DWORD bytesToWrite, LPDWORD bytesWritten, LPOVERLAPPED)`, without an overlapped operation. */
  fun writeFile(callState: MemorySegment, handle: MemorySegment, buffer: MemorySegment, bytesToWrite: Int, bytesWritten: MemorySegment): Boolean =
    Handles.WRITE_FILE.invokeExact(callState, handle, buffer, bytesToWrite, bytesWritten, MemorySegment.NULL) as Int != 0

  /**
   * `BOOL PeekNamedPipe(HANDLE, LPVOID buffer, DWORD size, LPDWORD bytesRead, LPDWORD bytesAvailable, LPDWORD bytesLeft)`, which
   * reads nothing and reports only the number of bytes that wait in the pipe.
   */
  fun peekNamedPipe(callState: MemorySegment, handle: MemorySegment, bytesAvailable: MemorySegment): Boolean =
    Handles.PEEK_NAMED_PIPE.invokeExact(
      callState, handle, MemorySegment.NULL, 0, MemorySegment.NULL, bytesAvailable, MemorySegment.NULL,
    ) as Int != 0

  /**
   * `DWORD WaitForSingleObject(HANDLE, DWORD millis)`
   *
   * @return [WAIT_OBJECT_0], [WAIT_TIMEOUT], [WAIT_ABANDONED] or [WAIT_FAILED]
   */
  fun waitForSingleObject(callState: MemorySegment, handle: MemorySegment, millis: Int): Int =
    Handles.WAIT_FOR_SINGLE_OBJECT.invokeExact(callState, handle, millis) as Int

  /** `BOOL GetExitCodeProcess(HANDLE process, LPDWORD exitCode)` */
  fun getExitCodeProcess(callState: MemorySegment, process: MemorySegment, exitCode: MemorySegment): Boolean =
    Handles.GET_EXIT_CODE_PROCESS.invokeExact(callState, process, exitCode) as Int != 0

  /** `BOOL TerminateProcess(HANDLE process, UINT exitCode)` */
  fun terminateProcess(callState: MemorySegment, process: MemorySegment, exitCode: Int): Boolean =
    Handles.TERMINATE_PROCESS.invokeExact(callState, process, exitCode) as Int != 0

  /** `DWORD ResumeThread(HANDLE thread)`. @return the previous suspend count, or -1 on failure */
  fun resumeThread(callState: MemorySegment, thread: MemorySegment): Int =
    Handles.RESUME_THREAD.invokeExact(callState, thread) as Int

  /**
   * Renders a Win32 error code the way the OS describes it.
   *
   * Read the code with [lastError] right after the call that failed, because any later call overwrites it.
   */
  fun formatWin32Error(function: String, errorCode: Int): String =
    "$function failed. Error code: $errorCode. Error message: ${formatMessage(errorCode)}"

  /** `DWORD FormatMessageW(DWORD flags, LPCVOID source, DWORD messageId, DWORD languageId, LPWSTR buffer, DWORD size, va_list*)` */
  private fun formatMessage(errorCode: Int): String {
    Arena.ofConfined().use { arena ->
      val buffer = arena.allocate(JAVA_CHAR, MESSAGE_BUFFER_CHARS)
      val length = Handles.FORMAT_MESSAGE.invokeExact(
        FORMAT_MESSAGE_FROM_SYSTEM or FORMAT_MESSAGE_IGNORE_INSERTS, MemorySegment.NULL, errorCode, 0, buffer,
        MESSAGE_BUFFER_CHARS.toInt(), MemorySegment.NULL,
      ) as Int
      if (length == 0) return "Unknown error"
      return String(buffer.toArray(JAVA_CHAR), 0, length).trimEnd()
    }
  }

  /** The library and the handles load on the first call, so the layouts above stay readable on another operating system. */
  private object Handles {
    private val LINKER: Linker = Linker.nativeLinker()
    private val KERNEL32: SymbolLookup = WindowsSystemLibraries.kernel32()
    private val CAPTURE_LAST_ERROR: Linker.Option = WindowsKernel32.captureLastError()

    val CREATE_PROCESS: MethodHandle = LINKER.downcallHandle(
      KERNEL32.findOrThrow("CreateProcessW"),
      FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS),
      CAPTURE_LAST_ERROR,
    )

    val CREATE_PIPE: MethodHandle = LINKER.downcallHandle(
      KERNEL32.findOrThrow("CreatePipe"), FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT), CAPTURE_LAST_ERROR)

    val DUPLICATE_HANDLE: MethodHandle = LINKER.downcallHandle(
      KERNEL32.findOrThrow("DuplicateHandle"),
      FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT),
      CAPTURE_LAST_ERROR,
    )

    val CREATE_FILE: MethodHandle = LINKER.downcallHandle(
      KERNEL32.findOrThrow("CreateFileW"),
      FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS),
      CAPTURE_LAST_ERROR,
    )

    val READ_FILE: MethodHandle = LINKER.downcallHandle(
      KERNEL32.findOrThrow("ReadFile"), FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS), CAPTURE_LAST_ERROR)

    val WRITE_FILE: MethodHandle = LINKER.downcallHandle(
      KERNEL32.findOrThrow("WriteFile"), FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS), CAPTURE_LAST_ERROR)

    val PEEK_NAMED_PIPE: MethodHandle = LINKER.downcallHandle(
      KERNEL32.findOrThrow("PeekNamedPipe"),
      FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS),
      CAPTURE_LAST_ERROR,
    )

    val WAIT_FOR_SINGLE_OBJECT: MethodHandle = LINKER.downcallHandle(
      KERNEL32.findOrThrow("WaitForSingleObject"), FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT), CAPTURE_LAST_ERROR)

    val GET_EXIT_CODE_PROCESS: MethodHandle = LINKER.downcallHandle(
      KERNEL32.findOrThrow("GetExitCodeProcess"), FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS), CAPTURE_LAST_ERROR)

    val TERMINATE_PROCESS: MethodHandle = LINKER.downcallHandle(
      KERNEL32.findOrThrow("TerminateProcess"), FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT), CAPTURE_LAST_ERROR)

    val RESUME_THREAD: MethodHandle = LINKER.downcallHandle(
      KERNEL32.findOrThrow("ResumeThread"), FunctionDescriptor.of(JAVA_INT, ADDRESS), CAPTURE_LAST_ERROR)

    val FORMAT_MESSAGE: MethodHandle = LINKER.downcallHandle(
      KERNEL32.findOrThrow("FormatMessageW"),
      FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS),
    )
  }
}
