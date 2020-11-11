// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.daemon;

import com.sun.jna.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;

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

  private static @NotNull LibC checkLibc() {
    if (!IS_UNIX) {
      throw new IllegalStateException("Not a Unix system");
    }
    if (LIBC == null) {
      throw new IllegalStateException("Unable to load libc");
    }
    return LIBC;
  }

  public static boolean isUnix() {
    return IS_UNIX;
  }

  public static void daemonize() {
    LibC libc = checkLibc();
    int sid = libc.setsid();
    if (sid == -1) {
      throw new IllegalStateException("setsid: " + getLastErrorString());
    }
  }

  public static void setupSignals() {
    LibC libc = checkLibc();

    Memory sigset = new Memory(LibCConstants.MAX_SIZEOF_SIGSET_T);
    if (libc.sigfillset(sigset) == -1) {
      throw new IllegalStateException("sigfillset: " + getLastErrorString());
    }
    if (libc.sigprocmask(LibCConstants.SIG_UNBLOCK, sigset, Pointer.NULL) == -1) {
      throw new IllegalStateException("sigprocmask: " + getLastErrorString());
    }

    resetSignal(LibCConstants.SIGHUP);
    resetSignal(LibCConstants.SIGINT);
    resetSignal(LibCConstants.SIGQUIT);
    resetSignal(LibCConstants.SIGILL);
    resetSignal(LibCConstants.SIGTRAP);
    resetSignal(LibCConstants.SIGABRT);
    resetSignal(LibCConstants.SIGFPE);
    resetSignal(LibCConstants.SIGSEGV);
    resetSignal(LibCConstants.SIGPIPE);
    resetSignal(LibCConstants.SIGALRM);
    resetSignal(LibCConstants.SIGTERM);
  }

  private static void resetSignal(int signo) {
    LibC libc = checkLibc();

    Memory sa = new Memory(LibCConstants.MAX_SIZEOF_STRUCT_SIGACTION);
    if (libc.sigaction(signo, Pointer.NULL, sa) == -1) {
      throw new IllegalStateException("sigaction(" + signo + "): " + getLastErrorString());
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
      throw new IllegalStateException("signal(" + signo + "): " + getLastErrorString());
    }
    System.err.println("Restored ignored signal " + signo + " handler to default");
  }

  public static void closeStdStreams() {
    try {
      System.in.close();
    }
    catch (IOException e) {
      System.err.println("Unable to close daemon stdin: " + e.getMessage());
    }
    System.out.close();
    System.err.close();
  }

  private static String getLastErrorString() {
    return Objects.requireNonNull(checkLibc()).strerror(Native.getLastError());
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
