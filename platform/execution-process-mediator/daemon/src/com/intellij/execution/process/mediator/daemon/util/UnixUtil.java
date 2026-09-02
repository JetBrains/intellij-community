// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process.mediator.daemon.util;

import com.intellij.execution.process.mediator.daemon.util.NativeCall.NativeCallException;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public final class UnixUtil {
  private static final boolean IS_UNIX = !System.getProperty("os.name").startsWith("Windows");

  private UnixUtil() {}

  public static boolean isUnix() {
    return IS_UNIX;
  }

  public static void setup(boolean daemonize) {
    if (!IS_UNIX) {
      throw new IllegalStateException("Not a Unix system");
    }
    NativeCall.tryRun(UnixUtil::setupSignals, "Failed to setup signals");
    if (daemonize) {
      NativeCall.tryRun(UnixUtil::leadSession, "Failed to make session leader");
    }
  }

  private static void leadSession() throws NativeCallException {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment errno = LibC.newErrnoState(arena);
      int sid = setsid(errno);
      if (sid == -1) {
        throw libcCallError("setsid", errno);
      }
    }
  }

  private static void setupSignals() throws NativeCallException {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment errno = LibC.newErrnoState(arena);
      MemorySegment sigset = arena.allocate(LibCConstants.MAX_SIZEOF_SIGSET_T);
      if (sigfillset(errno, sigset) == -1) {
        throw libcCallError("sigfillset", errno);
      }
      if (unblockSignals(errno, sigset) == -1) {
        throw libcCallError("sigprocmask", errno);
      }
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
    NativeCall.tryRun(() -> resetSignal(signo), "Failed to reset " + signalName);
  }

  private static void resetSignal(int signo) throws NativeCallException {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment errno = LibC.newErrnoState(arena);
      MemorySegment sa = arena.allocate(LibCConstants.MAX_SIZEOF_STRUCT_SIGACTION);
      if (readSigaction(errno, signo, sa) == -1) {
        throw libcCallError("sigaction(" + signo + ")", errno);
      }
      // The handler is the first field of `struct sigaction` on Linux and on Darwin.
      if (sa.get(ADDRESS, 0).address() != LibCConstants.SIG_IGN) {
        // It's SIG_DFL, or there's a handler installed by the JVM, which is the reason we need this check.
        // Otherwise we could end up resetting, for example, a handler for SIGSEGV (used by the JVM extensively for its own purposes)
        // to the default action for that signal - termination, and the VM would die horribly killed by the OS with no mercy whatsoever.
        //
        // In either case, be it SIG_DFL or a handler, the signal action will reset to SIG_DFL upon exec() from a forked child.
        return;
      }

      if (signal(errno, signo, MemorySegment.ofAddress(LibCConstants.SIG_DFL)).address() == LibCConstants.SIG_ERR) {
        throw libcCallError("signal(" + signo + ")", errno);
      }
      System.err.println("Restored ignored signal " + signo + " handler to default");
    }
  }

  private static @NotNull NativeCallException libcCallError(@NotNull String message, @NotNull MemorySegment errnoState) {
    int lastError = LibC.errno(errnoState);
    if (lastError != 0) {
      message += ": " + strerror(lastError);
    }
    return new NativeCallException(message);
  }

  private static int setsid(MemorySegment errno) {
    try {
      return (int)LibC.SETSID.invokeExact(errno);
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  private static int sigfillset(MemorySegment errno, MemorySegment set) {
    try {
      return (int)LibC.SIGFILLSET.invokeExact(errno, set);
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** {@code sigprocmask(SIG_UNBLOCK, set, NULL)}. */
  private static int unblockSignals(MemorySegment errno, MemorySegment set) {
    try {
      return (int)LibC.SIGPROCMASK.invokeExact(errno, LibCConstants.SIG_UNBLOCK, set, MemorySegment.NULL);
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** {@code sigaction(signo, NULL, oldAct)}: reads the current action and changes nothing. */
  private static int readSigaction(MemorySegment errno, int signo, MemorySegment oldAct) {
    try {
      return (int)LibC.SIGACTION.invokeExact(errno, signo, MemorySegment.NULL, oldAct);
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  private static MemorySegment signal(MemorySegment errno, int signo, MemorySegment handler) {
    try {
      return (MemorySegment)LibC.SIGNAL.invokeExact(errno, signo, handler);
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  private static String strerror(int errno) {
    try {
      MemorySegment text = (MemorySegment)LibC.STRERROR.invokeExact(errno);
      return text.reinterpret(LibCConstants.MAX_STRERROR_LENGTH).getString(0);
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /**
   * Downcalls into the C library through the default lookup.
   * Every handle except {@link #STRERROR} takes a leading capture-state segment that receives {@code errno}.
   * A lookup failure surfaces as a {@link LinkageError}, which {@link NativeCall#tryRun} reports.
   */
  private static final class LibC {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP = LINKER.defaultLookup();
    private static final Linker.Option CAPTURE_ERRNO = Linker.Option.captureCallState("errno");
    private static final StructLayout CAPTURE_STATE = Linker.Option.captureStateLayout();
    private static final VarHandle ERRNO = CAPTURE_STATE.varHandle(MemoryLayout.PathElement.groupElement("errno"));

    static final MethodHandle SETSID = downcallWithErrno("setsid", FunctionDescriptor.of(JAVA_INT));
    static final MethodHandle SIGFILLSET = downcallWithErrno("sigfillset", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    static final MethodHandle SIGPROCMASK = downcallWithErrno("sigprocmask", FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
    static final MethodHandle SIGACTION = downcallWithErrno("sigaction", FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
    static final MethodHandle SIGNAL = downcallWithErrno("signal", FunctionDescriptor.of(ADDRESS, JAVA_INT, ADDRESS));
    static final MethodHandle STRERROR = LINKER.downcallHandle(LOOKUP.findOrThrow("strerror"), FunctionDescriptor.of(ADDRESS, JAVA_INT));

    static MemorySegment newErrnoState(Arena arena) {
      return arena.allocate(CAPTURE_STATE);
    }

    static int errno(MemorySegment state) {
      return (int)ERRNO.get(state, 0L);
    }

    private static MethodHandle downcallWithErrno(String name, FunctionDescriptor descriptor) {
      return LINKER.downcallHandle(LOOKUP.findOrThrow(name), descriptor, CAPTURE_ERRNO);
    }
  }

  private interface LibCConstants {
    int MAX_SIGNAL_NR = 2048;  // that's more than 1024 signals available on Linux
    int MAX_SIZEOF_SIGSET_T = MAX_SIGNAL_NR / Byte.SIZE;  // sizeof(sigset_t): 128 on Linux, 4 on Darwin, 16 on FreeBSD
    int MAX_SIZEOF_STRUCT_SIGACTION = MAX_SIZEOF_SIGSET_T + 64;  // struct sigaction: sa_mask + (sa_handler + sa_flags + sa_restorer)
    int MAX_STRERROR_LENGTH = 1024;

    int SIG_UNBLOCK = System.getProperty("os.name").startsWith("Linux") ? 1 : 2;

    long SIG_DFL = 0L;
    long SIG_IGN = 1L;
    long SIG_ERR = -1L;

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
