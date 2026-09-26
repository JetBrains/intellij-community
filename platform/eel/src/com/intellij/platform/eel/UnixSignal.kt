// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import org.jetbrains.annotations.ApiStatus

/**
 * Signals used by Linux and BSD (Mac OS X is DarwinBSD).
 * Each signal has code, that might differ between Darwin [bsdCode] and x86/ARM Linux [linuxCode].
 * For Linux see [``signal(7)``](https://man7.org/linux/man-pages/man7/signal.7.html),
 * for Darwin see [signal.h](https://opensource.apple.com/source/xnu/xnu-7195.81.3/bsd/sys/signal.h)
 * for BSD see [`signal(3)`](https://man.freebsd.org/cgi/man.cgi?sektion=3&query=signal)
 * Note, that MIPS uses different codes, and OS X uses the same mapping as FreeBSD
 *
 * When process got killed by signal, most shells return [EXIT_CODE_OFFSET] + signal code.
 * See [``info bash``](https://www.gnu.org/software/bash/manual/html_node/Exit-Status.html)
 */
@ApiStatus.Experimental
enum class UnixSignal(private val bsdCode: Int, private val linuxCode: Int) {
  SIGHUP(1),
  SIGINT(2),
  SIGQUIT(3),
  SIGILL(4),
  SIGTRAP(5),
  SIGABRT(6),
  SIGBUS(bsdCode = 10, linuxCode = 7),
  SIGFPE(8),
  SIGKILL(9),
  SIGUSR1(bsdCode = 30, linuxCode = 10),
  SIGSEGV(11),
  SIGUSR2(bsdCode = 31, linuxCode = 12),
  SIGPIPE(13),
  SIGALRM(14),
  SIGTERM(15),
  SIGCHLD(bsdCode = 20, linuxCode = 17),
  SIGCONT(bsdCode = 19, linuxCode = 18),
  SIGSTOP(bsdCode = 17, linuxCode = 19),
  SIGTSTP(bsdCode = 18, linuxCode = 20),
  SIGTTIN(21),
  SIGTTOU(22),
  SIGURG(bsdCode = 16, linuxCode = 23),
  SIGXCPU(24),
  SIGXFSZ(25),
  SIGVTALRM(26),
  SIGPROF(27),
  SIGWINCH(28),
  SIGIO(bsdCode = 23, linuxCode = 29),
  SIGSYS(bsdCode = 12, linuxCode = 31);

  constructor(code: Int) : this(code, code)

  @ApiStatus.Internal
  companion object {

    /**
     * Code that most shells add to signal code to compute exit code.
     * Most shells do it: sh, bash, (d)ash, zsh. The only exception if ksh93 which adds 256 and can be ignored.
     */

    @ApiStatus.Internal
    const val EXIT_CODE_OFFSET: Int = 128

    /**
     * [sigName] is signal name with or without ``SIG`` prefix.
     */

    @ApiStatus.Internal
    @JvmStatic
    fun fromString(sigName: String): UnixSignal? = try {
      valueOf(if (sigName.startsWith("SIG")) sigName else "SIG$sigName")
    }
    catch (_: IllegalArgumentException) {
      null
    }

    /**
     * [platformHint] points to a platform
     */
    @JvmStatic
    @ApiStatus.Internal
    fun fromExitCode(platformHint: PlatformHint, code: Int): UnixSignal? = entries.firstOrNull { it.asExitCode(platformHint) == code }
  }

  @ApiStatus.Internal
  fun getSignalNumber(platformHint: PlatformHint): Int = if (platformHint.isBSD) bsdCode else linuxCode

  /**
   * See [EXIT_CODE_OFFSET]
   */

  @ApiStatus.Internal
  fun asExitCode(platformHint: PlatformHint): Int = getSignalNumber(platformHint) + EXIT_CODE_OFFSET

  @ApiStatus.Internal
  sealed class PlatformHint(internal val isBSD: Boolean) {


    /**
     * Tell directly if [isBSD]
     */

    @ApiStatus.Internal
    class Explicitly(isBSD: Boolean) : PlatformHint(isBSD)

    /**
     * Use [EelPlatform.Posix]
     */
    @ApiStatus.Internal
    class FromEelPlatform(platform: EelPlatform.Posix) : PlatformHint(
      when (platform) {
        is EelPlatform.Darwin, is EelPlatform.FreeBSD -> true
        // No idea if Harmony uses Linux codes, but most probably yes
        is EelPlatform.OHOS, is EelPlatform.Linux -> false
      }
    )
  }
}