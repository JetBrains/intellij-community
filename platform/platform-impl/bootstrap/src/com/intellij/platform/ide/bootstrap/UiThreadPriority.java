// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap;

import com.intellij.util.system.OS;
import com.intellij.util.system.WindowsSystemLibraries;

import javax.swing.SwingUtilities;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

final class UiThreadPriority {
  @SuppressWarnings("EnumSwitchStatementWhichMissesCases")
  static void adjust() {
    var os = OS.CURRENT;
    SwingUtilities.invokeLater(() -> {
      try {
        switch (os) {
          case Windows -> setWindowsThreadPriority();
          case macOS -> setUserInteractiveQosClassForCurrentThread();
          // Linux: raising thread priority is generally restricted by process limits; see https://man7.org/linux/man-pages/man7/sched.7.html
        }
      }
      catch (Throwable t) {
        var buf = new StringWriter();
        t.printStackTrace(new PrintWriter(buf));
        logError(buf.toString());
      }
    });
  }

  /**
   * References:
   * <a href="https://learn.microsoft.com/en-us/windows/win32/procthread/scheduling-priorities">Scheduling Priorities</a>,
   * <a href="https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-setthreadpriority">SetThreadPriority</a>.
   */
  private static void setWindowsThreadPriority() {
    var debug = Boolean.getBoolean("ide.set.qos.for.edt.debug");
    var currentThread = Thread.currentThread();

    int jvmPriorityBefore = currentThread.getPriority();
    int nativePriorityBefore = debug ? WindowsThread.currentPriority() : -1;

    currentThread.setPriority(Thread.MAX_PRIORITY);  // the actual work

    if (debug) {
      int nativeThreadId = WindowsThread.currentId();
      int nativePriorityAfter = WindowsThread.currentPriority();
      int jvmPriorityAfter = currentThread.getPriority();

      /*
       * Expected output:
       *
       * EDT JVM ID = 66, Native ID = 1234, Name = AWT-EventQueue-0
       *   Before: JVM Priority = 6, Native Priority = 0
       *   After: JVM Priority = 10, Native Priority = 2
       */
      logDebug(
        "EDT JVM ID = " + currentThread.getId() + ", Native ID = " + nativeThreadId + ", Name = " + currentThread.getName() +
        "\n  Before: JVM Priority = " + jvmPriorityBefore + ", Native Priority = " + nativePriorityBefore +
        "\n  After: JVM Priority = " + jvmPriorityAfter + ", Native Priority = " + nativePriorityAfter
      );
    }
  }

  /**
   * Sets the QoS class for a current thread.
   * References:
   * <a href="https://developer.apple.com/library/archive/documentation/Performance/Conceptual/power_efficiency_guidelines_osx/PrioritizeWorkAtTheTaskLevel.html">documentation archive</a>,
   * <a href="https://github.com/apple-oss-distributions/libpthread/blob/c032e0b076700a0a47db75528a282b8d3a06531a/include/pthread/qos.h#L118-L156">Darwin source code</a>.
   */
  private static final class DarwinPThread {
    static final int QOS_CLASS_USER_INTERACTIVE = 0x21;

    private static final Linker LINKER = Linker.nativeLinker();

    /** {@code int pthread_set_qos_class_self_np(qos_class_t qosClass, int relativePriority)}; {@code qos_class_t} is an {@code unsigned int} enum */
    static final MethodHandle PTHREAD_SET_QOS_CLASS_SELF_NP = LINKER.downcallHandle(
      LINKER.defaultLookup().findOrThrow("pthread_set_qos_class_self_np"), FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT));

    /** @return 0, or the {@code errno} value the call returns */
    static int setUserInteractiveQosClassSelf() {
      try {
        return (int)PTHREAD_SET_QOS_CLASS_SELF_NP.invokeExact(QOS_CLASS_USER_INTERACTIVE, 0);
      }
      catch (Throwable t) {
        throw new IllegalStateException(t);
      }
    }
  }

  /** {@code kernel32.dll} downcalls for the debug output; {@code HANDLE} is an address. */
  private static final class WindowsThread {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup KERNEL32 = WindowsSystemLibraries.lookup("kernel32.dll");

    /** {@code HANDLE GetCurrentThread()}, a pseudo handle that needs no close */
    static final MethodHandle GET_CURRENT_THREAD = LINKER.downcallHandle(KERNEL32.findOrThrow("GetCurrentThread"), FunctionDescriptor.of(ADDRESS));
    /** {@code DWORD GetCurrentThreadId()} */
    static final MethodHandle GET_CURRENT_THREAD_ID = LINKER.downcallHandle(KERNEL32.findOrThrow("GetCurrentThreadId"), FunctionDescriptor.of(JAVA_INT));
    /** {@code int GetThreadPriority(HANDLE)} */
    static final MethodHandle GET_THREAD_PRIORITY = LINKER.downcallHandle(KERNEL32.findOrThrow("GetThreadPriority"), FunctionDescriptor.of(JAVA_INT, ADDRESS));

    static int currentPriority() {
      try {
        MemorySegment thread = (MemorySegment)GET_CURRENT_THREAD.invokeExact();
        return (int)GET_THREAD_PRIORITY.invokeExact(thread);
      }
      catch (Throwable t) {
        throw new IllegalStateException(t);
      }
    }

    static int currentId() {
      try {
        return (int)GET_CURRENT_THREAD_ID.invokeExact();
      }
      catch (Throwable t) {
        throw new IllegalStateException(t);
      }
    }
  }

  private static void setUserInteractiveQosClassForCurrentThread() {
    var ret = DarwinPThread.setUserInteractiveQosClassSelf();
    if (ret != 0) {
      var currentThread = Thread.currentThread();
      logError("Unable to set QoS class for thread #" + currentThread.getId() + " (" + currentThread.getName() + "): " + ret);
    }
  }

  // loggers are not yet initialized
  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void logDebug(String message) {
    System.out.println(UiThreadPriority.class.getSimpleName() + ": " + message);
  }

  // loggers are not yet initialized
  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void logError(String message) {
    System.err.println(UiThreadPriority.class.getSimpleName() + ": " + message);
  }
}
