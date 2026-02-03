// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap;

import com.intellij.jna.JnaLoader;
import com.intellij.util.system.OS;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.platform.win32.Kernel32;

import javax.swing.*;
import java.io.PrintWriter;
import java.io.StringWriter;

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
    int nativePriorityBefore = debug && JnaLoader.isLoaded() ? Kernel32.INSTANCE.GetThreadPriority(Kernel32.INSTANCE.GetCurrentThread()) : -1;

    currentThread.setPriority(Thread.MAX_PRIORITY);  // the actual work

    if (debug) {
      int nativeThreadId = JnaLoader.isLoaded() ? Kernel32.INSTANCE.GetCurrentThreadId() : -1;
      int nativePriorityAfter = JnaLoader.isLoaded() ? Kernel32.INSTANCE.GetThreadPriority(Kernel32.INSTANCE.GetCurrentThread()) : -1;
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

    static native int pthread_set_qos_class_self_np(int qosClass, int relPriority);
  }

  private static void setUserInteractiveQosClassForCurrentThread() {
    if (!JnaLoader.isLoaded()) return;

    var libc = NativeLibrary.getInstance(null);
    Native.register(DarwinPThread.class, libc);
    var ret = DarwinPThread.pthread_set_qos_class_self_np(DarwinPThread.QOS_CLASS_USER_INTERACTIVE, 0);
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
