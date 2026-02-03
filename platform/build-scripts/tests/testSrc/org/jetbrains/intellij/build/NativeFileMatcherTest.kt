// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.impl.NativeFilesMatcher
import org.jetbrains.intellij.build.impl.OsFamilyDetector
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class NativeFileMatcherTest {
  companion object {
    @JvmStatic
    private fun provideTestCasesForOsDetection(): Stream<Arguments> {
      return Stream.of(
        Arguments.of("resources/com/pty4j/native/freebsd/x86-64/libpty.so", null, null),
        Arguments.of("resources/com/pty4j/native/freebsd/x86/libpty.so", null, null),
        Arguments.of("resources/com/pty4j/native/linux/arm/libpty.so", "resources/com/pty4j/native/", OsFamily.LINUX),
        Arguments.of("resources/com/pty4j/native/linux/x86-64/libpty.so", "resources/com/pty4j/native/", OsFamily.LINUX),
        Arguments.of("resources/com/pty4j/native/linux/aarch64/libpty.so", "resources/com/pty4j/native/", OsFamily.LINUX),
        Arguments.of("resources/com/pty4j/native/linux/ppc64le/libpty.so", "resources/com/pty4j/native/", OsFamily.LINUX),
        Arguments.of("resources/com/pty4j/native/linux/mips64el/libpty.so", "resources/com/pty4j/native/", OsFamily.LINUX),
        Arguments.of("resources/com/pty4j/native/linux/x86/libpty.so", "resources/com/pty4j/native/", OsFamily.LINUX),
        Arguments.of("resources/com/pty4j/native/win/x86-64/cyglaunch.exe", "resources/com/pty4j/native/", OsFamily.WINDOWS),
        Arguments.of("resources/com/pty4j/native/win/x86-64/OpenConsole.exe", "resources/com/pty4j/native/", OsFamily.WINDOWS),
        Arguments.of("resources/com/pty4j/native/win/x86-64/conpty.dll", "resources/com/pty4j/native/", OsFamily.WINDOWS),
        Arguments.of("resources/com/pty4j/native/win/x86-64/winpty-agent.exe", "resources/com/pty4j/native/", OsFamily.WINDOWS),
        Arguments.of("resources/com/pty4j/native/win/x86-64/win-helper.dll", "resources/com/pty4j/native/", OsFamily.WINDOWS),
        Arguments.of("resources/com/pty4j/native/win/x86-64/winpty.dll", "resources/com/pty4j/native/", OsFamily.WINDOWS),
        Arguments.of("resources/com/pty4j/native/win/aarch64/OpenConsole.exe", "resources/com/pty4j/native/", OsFamily.WINDOWS),
        Arguments.of("resources/com/pty4j/native/win/aarch64/conpty.dll", "resources/com/pty4j/native/", OsFamily.WINDOWS),
        Arguments.of("resources/com/pty4j/native/win/aarch64/winpty-agent.exe", "resources/com/pty4j/native/", OsFamily.WINDOWS),
        Arguments.of("resources/com/pty4j/native/win/aarch64/win-helper.dll", "resources/com/pty4j/native/", OsFamily.WINDOWS),
        Arguments.of("resources/com/pty4j/native/win/aarch64/winpty.dll", "resources/com/pty4j/native/", OsFamily.WINDOWS),
        Arguments.of("resources/com/pty4j/native/win/x86/winpty-agent.exe", "resources/com/pty4j/native/", OsFamily.WINDOWS),
        Arguments.of("resources/com/pty4j/native/win/x86/winpty.dll", "resources/com/pty4j/native/", OsFamily.WINDOWS),
        Arguments.of("resources/com/pty4j/native/darwin/libpty.dylib", "resources/com/pty4j/native/", OsFamily.MACOS),
        Arguments.of("com/sun/jna/win32-x86/jnidispatch.dll", "com/sun/jna/", OsFamily.WINDOWS),
        Arguments.of("com/sun/jna/darwin-x86-64/libjnidispatch.jnilib", "com/sun/jna/", OsFamily.MACOS),
        Arguments.of("com/sun/jna/darwin-aarch64/libjnidispatch.jnilib", "com/sun/jna/", OsFamily.MACOS),
        Arguments.of("com/sun/jna/linux-x86/libjnidispatch.so", "com/sun/jna/", OsFamily.LINUX),
        Arguments.of("com/sun/jna/linux-x86-64/libjnidispatch.so", "com/sun/jna/", OsFamily.LINUX),
        Arguments.of("com/sun/jna/linux-arm/libjnidispatch.so", "com/sun/jna/", OsFamily.LINUX),
        Arguments.of("com/sun/jna/linux-armel/libjnidispatch.so", "com/sun/jna/", OsFamily.LINUX),
        Arguments.of("com/sun/jna/linux-aarch64/libjnidispatch.so", "com/sun/jna/", OsFamily.LINUX),
        Arguments.of("com/sun/jna/linux-ppc/libjnidispatch.so", "com/sun/jna/", OsFamily.LINUX),
        Arguments.of("com/sun/jna/linux-ppc64le/libjnidispatch.so", "com/sun/jna/", OsFamily.LINUX),
        Arguments.of("com/sun/jna/linux-mips64el/libjnidispatch.so", "com/sun/jna/", OsFamily.LINUX),
        Arguments.of("com/sun/jna/linux-loongarch64/libjnidispatch.so", "com/sun/jna/", OsFamily.LINUX),
        Arguments.of("com/sun/jna/linux-s390x/libjnidispatch.so", "com/sun/jna/", OsFamily.LINUX),
        Arguments.of("com/sun/jna/linux-riscv64/libjnidispatch.so", "com/sun/jna/", OsFamily.LINUX),
        Arguments.of("com/sun/jna/sunos-x86/libjnidispatch.so", null, null),
        Arguments.of("com/sun/jna/sunos-x86-64/libjnidispatch.so", null, null),
        Arguments.of("com/sun/jna/sunos-sparc/libjnidispatch.so", null, null),
        Arguments.of("com/sun/jna/sunos-sparcv9/libjnidispatch.so", null, null),
        Arguments.of("com/sun/jna/freebsd-x86/libjnidispatch.so", null, null),
        Arguments.of("com/sun/jna/freebsd-x86-64/libjnidispatch.so", null, null),
        Arguments.of("com/sun/jna/openbsd-x86/libjnidispatch.so", null, null),
        Arguments.of("com/sun/jna/openbsd-x86-64/libjnidispatch.so", null, null),
        Arguments.of("com/sun/jna/win32-x86-64/jnidispatch.dll", "com/sun/jna/", OsFamily.WINDOWS),
        Arguments.of("com/sun/jna/win32-aarch64/jnidispatch.dll", "com/sun/jna/", OsFamily.WINDOWS),
        Arguments.of("sqlite/win-aarch64/sqliteij.dll", "sqlite/", OsFamily.WINDOWS),
        Arguments.of("sqlite/linux-x86_64/libsqliteij.so", "sqlite/", OsFamily.LINUX),
        Arguments.of("sqlite/win-x86_64/sqliteij.dll", "sqlite/", OsFamily.WINDOWS),
        Arguments.of("sqlite/linux-aarch64/libsqliteij.so", "sqlite/", OsFamily.LINUX),
        Arguments.of("sqlite/mac-x86_64/libsqliteij.jnilib", "sqlite/", OsFamily.MACOS),
        Arguments.of("sqlite/mac-aarch64/libsqliteij.jnilib", "sqlite/", OsFamily.MACOS),
        Arguments.of("binaries/linux/libasyncProfiler.so", "binaries/", OsFamily.LINUX),
        Arguments.of("binaries/linux-aarch64/libasyncProfiler.so", "binaries/", OsFamily.LINUX),
        Arguments.of("binaries/macos/libasyncProfiler.dylib", "binaries/", OsFamily.MACOS),
        Arguments.of("binaries/windows/dbghelp.dll", "binaries/", OsFamily.WINDOWS),
        Arguments.of("binaries/windows/jniSymbolsResolver.dll", "binaries/", OsFamily.WINDOWS),
        Arguments.of("binaries/windows/libasyncProfiler.dll", "binaries/", OsFamily.WINDOWS),
        Arguments.of("binaries/windows/symsrv.dll", "binaries/", OsFamily.WINDOWS),
        Arguments.of("binaries/windows-aarch64/dbghelp.dll", "binaries/", OsFamily.WINDOWS),
        Arguments.of("binaries/windows-aarch64/jniSymbolsResolver.dll", "binaries/", OsFamily.WINDOWS),
        Arguments.of("binaries/windows-aarch64/libasyncProfiler.dll", "binaries/", OsFamily.WINDOWS),
        Arguments.of("binaries/windows-aarch64/symsrv.dll", "binaries/", OsFamily.WINDOWS),
        Arguments.of("runtime/macos-aarch64/RenderDocHost", "runtime/", OsFamily.MACOS),
        Arguments.of("libskiko-linux-arm64.so", "", OsFamily.LINUX),
        Arguments.of("libskiko-linux-x64.so", "", OsFamily.LINUX),
        Arguments.of("libskiko-macos-arm64.so", "", OsFamily.MACOS),
        Arguments.of("libskiko-macos-x64.so", "", OsFamily.MACOS),
        Arguments.of("skiko-windows-arm64.dll", "", OsFamily.WINDOWS),
        Arguments.of("skiko-windows-x64.dll", "", OsFamily.WINDOWS)
      )
    }

    @JvmStatic
    private fun provideTestCasesForNativeFileMatcher(): Stream<Arguments> {
      val ptyFiles = listOf(
        "resources/com/pty4j/native/freebsd/x86-64/libpty.so",
        "resources/com/pty4j/native/freebsd/x86/libpty.so",
        "resources/com/pty4j/native/linux/arm/libpty.so",
        "resources/com/pty4j/native/linux/x86-64/libpty.so",
        "resources/com/pty4j/native/linux/aarch64/libpty.so",
        "resources/com/pty4j/native/linux/ppc64le/libpty.so",
        "resources/com/pty4j/native/linux/mips64el/libpty.so",
        "resources/com/pty4j/native/linux/x86/libpty.so",
        "resources/com/pty4j/native/win/x86-64/cyglaunch.exe",
        "resources/com/pty4j/native/win/x86-64/OpenConsole.exe",
        "resources/com/pty4j/native/win/x86-64/conpty.dll",
        "resources/com/pty4j/native/win/x86-64/winpty-agent.exe",
        "resources/com/pty4j/native/win/x86-64/win-helper.dll",
        "resources/com/pty4j/native/win/x86-64/winpty.dll",
        "resources/com/pty4j/native/win/aarch64/OpenConsole.exe",
        "resources/com/pty4j/native/win/aarch64/conpty.dll",
        "resources/com/pty4j/native/win/aarch64/winpty-agent.exe",
        "resources/com/pty4j/native/win/aarch64/win-helper.dll",
        "resources/com/pty4j/native/win/aarch64/winpty.dll",
        "resources/com/pty4j/native/win/x86/winpty-agent.exe",
        "resources/com/pty4j/native/win/x86/winpty.dll",
        "resources/com/pty4j/native/darwin/libpty.dylib"
      )
      val linuxX64PtyMatches = listOf(
        NativeFilesMatcher.Match("resources/com/pty4j/native/linux/x86-64/libpty.so", "linux/x86-64/libpty.so", OsFamily.LINUX,
                                 JvmArchitecture.x64))
      val linuxAarch64PtyMatches = listOf(
        NativeFilesMatcher.Match("resources/com/pty4j/native/linux/aarch64/libpty.so", "linux/aarch64/libpty.so", OsFamily.LINUX,
                                 JvmArchitecture.aarch64))
      val winX64PtyMatches = listOf(
        "win/x86-64/cyglaunch.exe",
        "win/x86-64/OpenConsole.exe",
        "win/x86-64/conpty.dll",
        "win/x86-64/winpty-agent.exe",
        "win/x86-64/win-helper.dll",
        "win/x86-64/winpty.dll",
      ).map { NativeFilesMatcher.Match("resources/com/pty4j/native/$it", it, OsFamily.WINDOWS, JvmArchitecture.x64) }
      val winAarch64PtyMatches = listOf(
        "win/aarch64/OpenConsole.exe",
        "win/aarch64/conpty.dll",
        "win/aarch64/winpty-agent.exe",
        "win/aarch64/win-helper.dll",
        "win/aarch64/winpty.dll",
      ).map { NativeFilesMatcher.Match("resources/com/pty4j/native/$it", it, OsFamily.WINDOWS, JvmArchitecture.aarch64) }
      val macosPtyMatches = listOf(NativeFilesMatcher.Match("resources/com/pty4j/native/darwin/libpty.dylib", "darwin/libpty.dylib", OsFamily.MACOS, null))
      val ptyPrefix = "resources/com/pty4j/native/"
      val sqliteMacosFiles = listOf("sqlite/mac-x86_64/libsqliteij.jnilib", "sqlite/mac-aarch64/libsqliteij.jnilib")
      val sqlitePrefix = "sqlite/"

      return Stream.of(
        Arguments.of(ptyFiles, listOf(OsFamily.LINUX), JvmArchitecture.x64, linuxX64PtyMatches, ptyPrefix),
        Arguments.of(ptyFiles, listOf(OsFamily.LINUX), JvmArchitecture.aarch64, linuxAarch64PtyMatches, ptyPrefix),
        Arguments.of(ptyFiles, listOf(OsFamily.WINDOWS), JvmArchitecture.x64, winX64PtyMatches, ptyPrefix),
        Arguments.of(ptyFiles, listOf(OsFamily.WINDOWS), JvmArchitecture.aarch64, winAarch64PtyMatches, ptyPrefix),
        Arguments.of(ptyFiles, listOf(OsFamily.MACOS), JvmArchitecture.x64, macosPtyMatches, ptyPrefix),
        Arguments.of(ptyFiles, listOf(OsFamily.MACOS), JvmArchitecture.aarch64, macosPtyMatches, ptyPrefix),
        Arguments.of(ptyFiles, OsFamily.ALL, JvmArchitecture.x64, (linuxX64PtyMatches.asSequence() + winX64PtyMatches + macosPtyMatches).toList(), ptyPrefix),
        Arguments.of(ptyFiles, null, JvmArchitecture.x64, (linuxX64PtyMatches.asSequence() + winX64PtyMatches + macosPtyMatches).toList(), ptyPrefix),
        Arguments.of(ptyFiles, OsFamily.ALL, JvmArchitecture.aarch64, (linuxAarch64PtyMatches.asSequence() + winAarch64PtyMatches + macosPtyMatches).toList(), ptyPrefix),
        Arguments.of(ptyFiles, null, JvmArchitecture.aarch64, (linuxAarch64PtyMatches.asSequence() + winAarch64PtyMatches + macosPtyMatches).toList(), ptyPrefix),
        Arguments.of(ptyFiles, listOf(OsFamily.LINUX), null, (linuxX64PtyMatches.asSequence() + linuxAarch64PtyMatches.asSequence()).toList(), ptyPrefix),
        Arguments.of(ptyFiles, listOf(OsFamily.WINDOWS), null, (winX64PtyMatches.asSequence() + winAarch64PtyMatches.asSequence()).toList(), ptyPrefix),
        Arguments.of(ptyFiles, listOf(OsFamily.MACOS), null, macosPtyMatches, ptyPrefix),
        Arguments.of(listOf("sqlite/win-x86_64/sqliteij.dll"), listOf(OsFamily.WINDOWS), JvmArchitecture.x64, listOf(NativeFilesMatcher.Match("sqlite/win-x86_64/sqliteij.dll", "win-x86_64/sqliteij.dll", OsFamily.WINDOWS, JvmArchitecture.x64)), sqlitePrefix),
        Arguments.of(listOf("sqlite/win-x86_64/sqliteij.dll"), listOf(OsFamily.LINUX), JvmArchitecture.x64, emptyList<NativeFilesMatcher.Match>(), sqlitePrefix),
        Arguments.of(listOf("sqlite/win-x86_64/sqliteij.dll"), listOf(OsFamily.WINDOWS), JvmArchitecture.aarch64, emptyList<NativeFilesMatcher.Match>(), sqlitePrefix),
        Arguments.of(sqliteMacosFiles, listOf(OsFamily.MACOS), JvmArchitecture.x64, listOf(NativeFilesMatcher.Match("sqlite/mac-x86_64/libsqliteij.jnilib", "mac-x86_64/libsqliteij.jnilib", OsFamily.MACOS, JvmArchitecture.x64)), sqlitePrefix),
        Arguments.of(sqliteMacosFiles, listOf(OsFamily.MACOS), JvmArchitecture.aarch64, listOf(NativeFilesMatcher.Match("sqlite/mac-aarch64/libsqliteij.jnilib", "mac-aarch64/libsqliteij.jnilib", OsFamily.MACOS, JvmArchitecture.aarch64)), sqlitePrefix),
        Arguments.of(sqliteMacosFiles, listOf(OsFamily.MACOS), null, listOf(NativeFilesMatcher.Match("sqlite/mac-x86_64/libsqliteij.jnilib", "mac-x86_64/libsqliteij.jnilib", OsFamily.MACOS, JvmArchitecture.x64), NativeFilesMatcher.Match("sqlite/mac-aarch64/libsqliteij.jnilib", "mac-aarch64/libsqliteij.jnilib", OsFamily.MACOS, JvmArchitecture.aarch64)), sqlitePrefix),
        Arguments.of(listOf("runtime/windows-x86_64/RenderDocHost.exe", "runtime/windows-x86_64/renderdoc.dll"), listOf(OsFamily.WINDOWS), JvmArchitecture.x64, listOf(NativeFilesMatcher.Match("runtime/windows-x86_64/RenderDocHost.exe", "windows-x86_64/RenderDocHost.exe", OsFamily.WINDOWS, JvmArchitecture.x64), NativeFilesMatcher.Match("runtime/windows-x86_64/renderdoc.dll", "windows-x86_64/renderdoc.dll", OsFamily.WINDOWS, JvmArchitecture.x64)), "runtime/"),
      )
    }
  }

  @ParameterizedTest
  @MethodSource("provideTestCasesForOsDetection")
  fun `os detection`(path: String, prefix: String?, osFamily: OsFamily?) {
    val osAndIndex = OsFamilyDetector.detectOsFamily(path)
    assertThat(osAndIndex?.second).isEqualTo(prefix)
    assertThat(osAndIndex?.first).isEqualTo(osFamily)
  }

  @ParameterizedTest
  @MethodSource("provideTestCasesForNativeFileMatcher")
  fun `native files matcher`(
    paths: List<String>,
    targetOs: List<OsFamily>?,
    targetArch: JvmArchitecture?,
    expectedMatches: List<NativeFilesMatcher.Match>,
    expectedPrefix: String?,
  ) {
    val matcher = NativeFilesMatcher(paths = paths, targetOs = targetOs, targetArch = targetArch)
    val matches = mutableListOf<NativeFilesMatcher.Match>()
    while (true) {
      val match = matcher.findNext() ?: break
      matches.add(match)
    }
    assertThat(matcher.commonPathPrefix).isEqualTo(expectedPrefix)
    assertThat(matches).hasSameElementsAs(expectedMatches)
  }
}