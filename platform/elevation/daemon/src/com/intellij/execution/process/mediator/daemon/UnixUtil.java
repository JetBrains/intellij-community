// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.daemon;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.NativeLongByReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;

@SuppressWarnings("SpellCheckingInspection")
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

    Pointer sigsetPtr = new NativeLongByReference().getPointer();
    if (libc.sigfillset(sigsetPtr) == -1) {
      throw new IllegalStateException("sigfillset: " + getLastErrorString());
    }
    if (libc.sigprocmask(LibCConstants.SIG_UNBLOCK, sigsetPtr, Pointer.NULL) == -1) {
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
    if (libc.signal(signo, LibCConstants.SIG_DFL) == LibCConstants.SIG_ERR) {
      throw new IllegalStateException("signal(" + signo + "): " + getLastErrorString());
    }
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
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

    Pointer signal(int sig, Pointer handler);

    String strerror(int errno);
  }

  private interface LibCConstants {
    int SIG_UNBLOCK = 2;
    Pointer SIG_DFL = new Pointer(0);
    Pointer SIG_ERR = new Pointer(-1);

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
