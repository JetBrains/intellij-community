// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process

/**
 * Signals used by Linux and DarwinBSD (Mac OS X).
 * Each signal has code, that might differ between Darwin [darwinCode] and x86/ARM Linux [linuxCode].
 * For Linux see [``signal(7)``](https://man7.org/linux/man-pages/man7/signal.7.html),
 * for Darwin see [signal.h](https://opensource.apple.com/source/xnu/xnu-7195.81.3/bsd/sys/signal.h)
 * Note, that MIPS uses different codes
 *
 * When process got killed by signal, most shells return [EXIT_CODE_OFFSET] + signal code.
 * See [``info bash``](https://www.gnu.org/software/bash/manual/html_node/Exit-Status.html)
 */
enum class UnixSignal(val darwinCode: Int, val linuxCode: Int) {
  SIGHUP(1),
  SIGINT(2),
  SIGQUIT(3),
  SIGILL(4),
  SIGTRAP(5),
  SIGABRT(6),
  SIGBUS(darwinCode = 10, linuxCode = 7),
  SIGFPE(8),
  SIGKILL(9),
  SIGUSR1(darwinCode = 30, linuxCode = 10),
  SIGSEGV(11),
  SIGUSR2(darwinCode = 31, linuxCode = 12),
  SIGPIPE(13),
  SIGALRM(14),
  SIGTERM(15),
  SIGCHLD(darwinCode = 20, linuxCode = 17),
  SIGCONT(darwinCode = 19, linuxCode = 18),
  SIGSTOP(darwinCode = 17, linuxCode = 19),
  SIGTSTP(darwinCode = 18, linuxCode = 20),
  SIGTTIN(21),
  SIGTTOU(22),
  SIGURG(darwinCode = 16, linuxCode = 23),
  SIGXCPU(24),
  SIGXFSZ(25),
  SIGVTALRM(26),
  SIGPROF(27),
  SIGWINCH(28),
  SIGIO(darwinCode = 23, linuxCode = 29),
  SIGSYS(darwinCode = 12, linuxCode = 31);

  constructor(code: Int) : this(code, code)

  companion object {

    /**
     * Code that shell adds to signal code to compute exit code
     */
    const val EXIT_CODE_OFFSET: Int = 128

    /**
     * [sigName] is signal name with or without ``SIG`` prefix.
     */
    @JvmStatic
    fun fromString(sigName: String): UnixSignal? = try {
      UnixSignal.valueOf(if (sigName.startsWith("SIG")) sigName else "SIG$sigName")
    }
    catch (_: IllegalArgumentException) {
      null
    }

    /**
     * [isDarwin] (Linux otherwise)
     */
    @JvmStatic
    fun fromExitCode(isDarwin: Boolean, code: Int): UnixSignal? = UnixSignal.values().firstOrNull { it.asExitCode(isDarwin) == code }
  }

  fun getSignalNumber(isDarwin: Boolean): Int = if (isDarwin) darwinCode else linuxCode
  fun asExitCode(isDarwin: Boolean): Int = getSignalNumber(isDarwin) + EXIT_CODE_OFFSET

}