// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.util;

import com.sun.jna.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.execution.process.mediator.util.NativeCall.NativeCallException;
import static com.intellij.execution.process.mediator.util.NativeCall.tryRun;

@SuppressWarnings({"SpellCheckingInspection", "UseOfSystemOutOrSystemErr"})
public final class UnixUtil {
  private static final boolean IS_UNIX = !System.getProperty("os.name").startsWith("Windows");
  private static final @Nullable LibC LIBC = IS_UNIX ? tryLoadLibC() : null;

  private UnixUtil() {}

  private static @Nullable LibC tryLoadLibC() {
    try {
      return Native.load("c", LibC.class);
    }
    catch (UnsatisfiedLinkError e) {
      return null;
    }
  }

  public static boolean isUnix() {
    return IS_UNIX;
  }

  public static void setup(boolean daemonize) {
    checkLibc();
    tryRun(UnixUtil::setupSignals, "Failed to setup signals");
    if (daemonize) {
      tryRun(UnixUtil::leadSession, "Failed to make session leader");
    }
  }

  private static @NotNull LibC checkLibc() {
    if (!IS_UNIX) {
      throw new IllegalStateException("Not a Unix system");
    }
    if (LIBC == null) {
      throw new IllegalStateException("Unable to load libc");
    }
    return LIBC;
  }

  private static void leadSession() throws NativeCallException {
    LibC libc = checkLibc();
    int sid = libc.setsid();
    if (sid == -1) {
      throw libcCallError("setsid");
    }
  }

  private static void setupSignals() throws NativeCallException {
    LibC libc = checkLibc();

    Memory sigset = new Memory(LibCConstants.MAX_SIZEOF_SIGSET_T);
    if (libc.sigfillset(sigset) == -1) {
      throw libcCallError("sigfillset");
    }
    if (libc.sigprocmask(LibCConstants.SIG_UNBLOCK, sigset, Pointer.NULL) == -1) {
      throw libcCallError("sigprocmask");
    }

    tryResetSignal(LibCConstants.SIGHUP, "SIGHUP");
    tryResetSignal(LibCConstants.SIGINT, "SIGINT");
    tryResetSignal(LibCConstants.SIGQUIT, "SIGQUIT");
    tryResetSignal(LibCConstants.SIGILL, "SIGILL");
    tryResetSignal(LibCConstants.SIGTRAP, "SIGTRAP");
    tryResetSignal(LibCConstants.SIGABRT, "SIGABRT");
    tryResetSignal(LibCConstants.SIGFPE, "SIGFPE");
    tryResetSignal(LibCConstants.SIGSEGV, "SIGSEGV");
    tryResetSignal(LibCConstants.SIGPIPE, "SIGPIPE");
    tryResetSignal(LibCConstants.SIGALRM, "SIGALRM");
    tryResetSignal(LibCConstants.SIGTERM, "SIGTERM");
  }

  private static void tryResetSignal(int signo, @NotNull String signalName) {
    tryRun(() -> resetSignal(signo), "Failed to reset " + signalName);
  }

  private static void resetSignal(int signo) throws NativeCallException {
    LibC libc = checkLibc();

    Memory sa = new Memory(LibCConstants.MAX_SIZEOF_STRUCT_SIGACTION);
    if (libc.sigaction(signo, Pointer.NULL, sa) == -1) {
      throw libcCallError("sigaction(" + signo + ")");
    }
    if (!LibCConstants.SIG_IGN.equals(sa.getPointer(0))) {
      // It's SIG_DFL, or there's a handler installed by the JVM, which is the reason we need this check.
      // Otherwise we could end up resetting, for example, a handler for SIGSEGV (used by the JVM extensively for its own purposes)
      // to the default action for that signal - termination, and the VM would die horribly killed by the OS with no mercy whatsoever.
      //
      // In either case, be it SIG_DFL or a handler, the signal action will reset to SIG_DFL upon exec() from a forked child.
      return;
    }

    if (libc.signal(signo, LibCConstants.SIG_DFL) == LibCConstants.SIG_ERR) {
      throw libcCallError("signal(" + signo + ")");
    }
    System.err.println("Restored ignored signal " + signo + " handler to default");
  }

  private static @NotNull NativeCallException libcCallError(@NotNull String message) {
    if (LIBC != null) {
      int lastError = Native.getLastError();
      if (lastError != 0) {
        message += ": " + LIBC.strerror(lastError);
      }
    }
    return new NativeCallException(message);
  }

  private interface LibC extends Library {
    int setsid();

    int sigfillset(Pointer set);

    int sigprocmask(int how, Pointer set, Pointer oldset);

    int sigaction(int sig, Pointer act, Pointer oldact);

    Pointer signal(int sig, Pointer handler);

    String strerror(int errno);
  }

  private interface LibCConstants {
    int MAX_SIGNAL_NR = 2048;  // that's more than 1024 signals available on Linux
    int MAX_SIZEOF_SIGSET_T = MAX_SIGNAL_NR / Byte.SIZE;  // sizeof(sigset_t): 128 on Linux, 4 on Darwin, 16 on FreeBSD
    int MAX_SIZEOF_STRUCT_SIGACTION = MAX_SIZEOF_SIGSET_T + 64;  // struct sigaction: sa_mask + (sa_handler + sa_flags + sa_restorer)

    int SIG_UNBLOCK = Platform.isLinux() ? 1 : 2;

    Pointer SIG_DFL = new Pointer(0L);
    Pointer SIG_IGN = new Pointer(1L);
    Pointer SIG_ERR = new Pointer(-1L);

    int SIGHUP = 1;
    int SIGINT = 2;
    int SIGQUIT = 3;
    int SIGILL = 4;
    int SIGTRAP = 5;
    int SIGABRT = 6;
    int SIGFPE = 8;
    int SIGSEGV = 11;
    int SIGPIPE = 13;
    int SIGALRM = 14;
    int SIGTERM = 15;
  }
}
