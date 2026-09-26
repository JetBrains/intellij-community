// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.concurrent.CancellationException
import kotlin.io.path.exists
import kotlin.io.path.useLines

/**
 * OS-specific memory utilities
 */
@ApiStatus.Internal
abstract class PlatformMemoryUtil {
  private var memoryStatsApiIsBroken: Boolean = false

  /**
   * Returns OS-provided memory metrics for the current process. See [MemoryStats] fields
   */
  fun getCurrentProcessMemoryStats(): MemoryStats? {
    return newMemoryStatsProvider().use { it.getCurrentProcessMemoryStats() }
  }

  /**
   * [MemoryStatsProvider] is optimized for frequent [MemoryStatsProvider.getCurrentProcessMemoryStats] invocations within single thread
   */
  abstract fun newMemoryStatsProvider(): MemoryStatsProvider

  /**
   * Releases unused memory from the native allocator (`malloc`/`free`) back to the operating system.
   * It's a no-op on an OS other than Linux.
   *
   * Equivalent to executing `jcmd <pid> System.trim_native_heap`.
   * See https://bugs.openjdk.org/browse/JDK-8293114
   */
  open fun trimLinuxNativeHeap() {}

  @ApiStatus.Internal
  class MemoryStats(
    /**
     * Physical RAM usage bytes. Includes file mappings. Excludes swap/compressed memory usages.
     *
     * Aka "Resident Set" (RSS) on Linux, "Resident memory" on macOS, "WorkingSet" on Windows.
     *
     * Avoid using this as a target metric. It's the worst choice since it mixes file mappings with normally allocated memory,
     * but excludes swap. It can be OK in test environment, though.
     */
    val ram: Long,

    /**
     * Physical RAM usage bytes excluding file mappings. Does not include swap (it's not a physical RAM)
     * or compressed memory (it's a kind of swap).
     *
     * Exposed to users:
     * * On Windows: the "Memory" column in the Task Manager.
     * * On Linux: the "Memory" column in the Gnome System Monitor.
     *
     * Aka "RssAnon" on Linux, "internal memory" on macOS, PrivateWorkingSetSize on Windows.
     *
     * On Windows it works only since Windows 10 22H2 with September 2023 cumulative update or
     * Windows 11 22H2 with September 2023 cumulative update.
     * In older versions it is always `0`.
     */
    val ramMinusFileMappings: Long,

    /**
     * "Total" memory usage bytes, including physical RAM (excluding file mappings), swap and
     * compressed memory (counted before compression).
     *
     * The value of [ramMinusFileMappings] + `swap` usage.
     *
     * Exposed to users:
     * * On macOS: the "Memory" column in the Activity Monitor
     *
     * Aka `VmSwap + RssAnon` on Linux, "physFootprint" on macOS, "Private memory" on Windows.
     */
    val ramPlusSwapMinusFileMappings: Long,

    /**
     * A fraction of *file mappings* bytes that currently resides in physical RAM.
     *
     * Aka "RssFile" on Linux, "external memory" on macOS, `WorkingSetSize - PrivateWorkingSetSize` on Windows.
     *
     * @see java.nio.channels.FileChannel.map
     */
    val fileMappingsRam: Long,
  )

  /**
   * The class is not thread safe; its methods should not be invoked from multiple threads simultaneously
   */
  abstract inner class MemoryStatsProvider: AutoCloseable {
    /**
     * Returns OS-provided memory metrics for the current process. See [MemoryStats] fields
     */
    fun getCurrentProcessMemoryStats(): MemoryStats? {
      if (memoryStatsApiIsBroken) return null

      return try {
        getCurrentProcessMemoryStatsInner()
      }
      catch (t: Throwable) {
        if (t is OutOfMemoryError || t is StackOverflowError || t is CancellationException) {
          throw t
        }
        LOG.error("Failed to get current process memory stats", t)
        memoryStatsApiIsBroken = true
        null
      }
    }

    protected abstract fun getCurrentProcessMemoryStatsInner(): MemoryStats?
    override fun close() {}
  }

  companion object {
    private val INSTANCE: PlatformMemoryUtil = try {
      when {
        SystemInfo.isLinux -> LinuxMemoryUtil()
        SystemInfo.isWin10OrNewer -> WindowsMemoryUtil()
        SystemInfo.isMac -> MacosMemoryUtil()
        else -> DummyMemoryUtil()
      }
    }
    catch (t: Throwable) {
      LOG.error("Failed to set up PlatformMemoryUtil", t)
      DummyMemoryUtil()
    }

    @JvmStatic
    fun getInstance(): PlatformMemoryUtil = INSTANCE
  }
}

private val LOG: Logger = logger<PlatformMemoryUtil>()

private class DummyMemoryUtil : PlatformMemoryUtil() {
  override fun newMemoryStatsProvider(): MemoryStatsProvider {
    return object : MemoryStatsProvider() {
      override fun getCurrentProcessMemoryStatsInner(): MemoryStats? = null
    }
  }
}


private class LinuxMemoryUtil : PlatformMemoryUtil() {
  override fun newMemoryStatsProvider(): MemoryStatsProvider {
    return object : MemoryStatsProvider() {
      override fun getCurrentProcessMemoryStatsInner(): MemoryStats? = getCurrentProcessMemoryStatsLinux()
    }
  }

  private fun getCurrentProcessMemoryStatsLinux(): MemoryStats? {
    val statusFile = Path.of("/proc/self/status")
    if (!statusFile.exists()) {
      return null
    }

    val fields = statusFile.useLines { lines ->
      lines.mapNotNull { line ->
        val name = line.substringBefore(":")
        if (name !in INTERESTING_FIELDS) return@mapNotNull null
        val value = line.substringAfter(":").substringBefore("kB").trim().toLongOrNull()
                    ?: return@mapNotNull null
        name to value
      }.toMap()
    }

    return LinuxMemoryStats(
      rss = fields["VmRSS"]?.let { it * 1024 } ?: return null,
      rssAnon = fields["RssAnon"]?.let { it * 1024 } ?: return null,
      swap = fields["VmSwap"]?.let { it * 1024 } ?: return null,
      rssFile = fields["RssFile"]?.let { it * 1024 } ?: return null,
    ).toMemoryStats()
  }

  private class LinuxMemoryStats(
    val rss: Long,
    val rssAnon: Long,
    val swap: Long,
    val rssFile: Long,
  ) {
    fun toMemoryStats(): MemoryStats = MemoryStats(
      ram = rss,
      ramMinusFileMappings = rssAnon,
      ramPlusSwapMinusFileMappings = rssAnon + swap,
      fileMappingsRam = rssFile,
    )
  }

  override fun trimLinuxNativeHeap() {
    try {
      // See https://github.com/openjdk/jdk/blob/3145278847428ad3a855a3e2c605b77f74ebe113/src/hotspot/os/linux/os_linux.cpp#L5484
      LinuxMalloc.trim()
    } catch (e: UnsatisfiedLinkError) {
      // Possibly not a glibc?
      LOG.error("Failed to trim native heap", e)
    }
  }

  private companion object {
    val INTERESTING_FIELDS = listOf("VmRSS", "VmSwap", "RssAnon", "RssFile")
  }
}

private class WindowsMemoryUtil : PlatformMemoryUtil() {
  override fun newMemoryStatsProvider(): MemoryStatsProvider = WindowsMemoryStatsProvider()

  private inner class WindowsMemoryStatsProvider : MemoryStatsProvider() {
    override fun getCurrentProcessMemoryStatsInner(): MemoryStats? {
      val counters = WindowsProcessMemory.read() ?: return null
      return WindowsMemoryStats(
        workingSetSize = counters.workingSetSize,
        privateWorkingSetSize = counters.privateWorkingSetSize,
        privateUsage = counters.privateUsage,
      ).toMemoryStats()
    }
  }

  private class WindowsMemoryStats(
    /**
     * The size of process' memory residing in physical RAM.
     * The "Total WS" column in VMMap.
     *
     * Analogue of `RSS` in Linux
     */
    val workingSetSize: Long,

    /**
     * The "Memory" column in the Task Manager!
     * The "Private" column in the Resource Monitor.
     * The "Private WS" column in VMMap.
     *
     * `workingSetSize - file_mappings`.
     *
     * Works since Windows 10 22H2 with September 2023 cumulative update or Windows 11 22H2 with September 2023 cumulative update.
     * In older versions it is always `0`.
     */
    val privateWorkingSetSize: Long,

    /**
     * The size of virtual memory "reserved" by the process (excluding file mappings).
     * The "Private" column in VMMap.
     *
     * This value is important on Windows because if the sum of [privateUsage] of all processes
     * exceeds the `RAM + total_swap` value, further memory allocations fail.
     *
     * It seems equal to [com.sun.management.OperatingSystemMXBean.getCommittedVirtualMemorySize].
     *
     * `privateWorkingSetSize + swap + reserved`
     */
    val privateUsage: Long,
  ) {
    fun toMemoryStats(): MemoryStats = MemoryStats(
      ram = workingSetSize,
      ramMinusFileMappings = privateWorkingSetSize,

      // Actually [privateUsage] is greater than just `privateWorkingSetSize + swap`. It also includes
      // `reserved` space - a memory that was marked as intended to be used but has never been actually allocated.
      // Nevertheless, it's good to take it into account in the case of Windows because reserved memory still
      // consumes the swap limit. That is, if the sum of [privateUsage] of all processes exceeds the
      // `RAM + total_swap` value, further memory allocations fail.
      ramPlusSwapMinusFileMappings = privateUsage,

      // Actually, the `workingSetSize - privateWorkingSetSize` value can be a bit smaller than the actual size
      // of file mappings. This is because some pages in mappings of executable files actually contribute to
      // [privateWorkingSetSize]. But I can measure the difference as a couple of megabytes at most, so it's
      // precise enough, I believe.
      fileMappingsRam = workingSetSize - privateWorkingSetSize,
    )
  }
}

private class MacosMemoryUtil : PlatformMemoryUtil() {
  override fun newMemoryStatsProvider(): MemoryStatsProvider = MacosMemoryStatsProvider()

  private inner class MacosMemoryStatsProvider : MemoryStatsProvider() {
    override fun getCurrentProcessMemoryStatsInner(): MemoryStats? {
      val info = MacTaskMemory.read() ?: return null
      return MacosMemoryStats(
        physFootprint = info.physFootprint,
        residentSize = info.residentSize,
        internal = info.internal,
        external = info.external,
      ).toMemoryStats()
    }
  }

  private class MacosMemoryStats(
    /**
     * The "Memory" column in the macOS Activity Monitor
     */
    val physFootprint: Long,

    /**
     * The size of process' memory residing in physical RAM.
     * Analogue of `RSS` in Linux.
     *
     * = [internal] + [external] + reusable
     */
    val residentSize: Long,

    val internal: Long,

    /** File mappings */
    val external: Long,
  ) {
    fun toMemoryStats(): MemoryStats = MemoryStats(
      ram = residentSize,
      ramMinusFileMappings = internal,
      ramPlusSwapMinusFileMappings = physFootprint,
      fileMappingsRam = external,
    )
  }
}
