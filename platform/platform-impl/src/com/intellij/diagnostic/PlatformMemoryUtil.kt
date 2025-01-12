// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.system.CpuArch
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
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

  companion object {
    private val INSTANCE: PlatformMemoryUtil = try {
      when {
        SystemInfo.isLinux -> LinuxMemoryUtil()
        SystemInfo.isWin10OrNewer && CpuArch.isIntel64() -> WindowsMemoryUtil()
        SystemInfo.isMac && CpuArch.isArm64() -> MacosMemoryUtil()
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
  override fun getCurrentProcessMemoryStatsInner(): MemoryStats? {
    return null
  }
}


private class LinuxMemoryUtil : PlatformMemoryUtil() {
  private val libc: LibC = Native.load("c", LibC::class.java)

  override fun getCurrentProcessMemoryStatsInner(): MemoryStats? {
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
      libc.malloc_trim(0)
    } catch (e: UnsatisfiedLinkError) {
      // Possibly not a glibc?
      LOG.error("Failed to trim native heap", e)
    }
  }

  @Suppress("FunctionName")
  interface LibC : Library {
    /**
     * See https://man7.org/linux/man-pages/man3/malloc_trim.3.html
     */
    fun malloc_trim(pad: Long): Boolean
  }

  private companion object {
    val INTERESTING_FIELDS = listOf("VmRSS", "VmSwap", "RssAnon", "RssFile")
  }
}

private class WindowsMemoryUtil : PlatformMemoryUtil() {
  private val kernel32: Kernel32 = Kernel32.INSTANCE
  private val psapi: Psapi = Native.load("psapi", Psapi::class.java)

  override fun getCurrentProcessMemoryStatsInner(): MemoryStats? {
    val processHandle = kernel32.GetCurrentProcess().pointer
    return ProcessMemoryCountersEx2().use { memoryCounters ->
      memoryCounters.cb = memoryCounters.size()

      val success = psapi.GetProcessMemoryInfo(processHandle, memoryCounters, memoryCounters.size())
      if (!success) return@use null
      memoryCounters.read()

      WindowsMemoryStats(
        workingSetSize = memoryCounters.WorkingSetSize,
        privateWorkingSetSize = memoryCounters.PrivateWorkingSetSize,
        privateUsage = memoryCounters.PrivateUsage,
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

  @Suppress("FunctionName")
  private interface Psapi : StdCallLibrary {
    /**
     * See https://learn.microsoft.com/en-us/windows/win32/api/psapi/nf-psapi-getprocessmemoryinfo
     */
    fun GetProcessMemoryInfo(process: Pointer, counters: ProcessMemoryCountersEx2, size: Int): Boolean
  }

  /**
   * See https://learn.microsoft.com/en-us/windows/win32/api/psapi/ns-psapi-process_memory_counters_ex2
   *
   * ## Requirements
   * Minimum supported client	Windows 10 22H2 with September 2023 cumulative update or
   * Windows 11 22H2 with September 2023 cumulative update
   */
  @Suppress("PropertyName", "unused")
  @Structure.FieldOrder(
    "cb",
    "PageFaultCount",
    "PeakWorkingSetSize",
    "WorkingSetSize",
    "QuotaPeakPagedPoolUsage",
    "QuotaPagedPoolUsage",
    "QuotaPeakNonPagedPoolUsage",
    "QuotaNonPagedPoolUsage",
    "PagefileUsage",
    "PeakPagefileUsage",
    "PrivateUsage",
    "PrivateWorkingSetSize",
    "SharedCommitUsage",
  )
  class ProcessMemoryCountersEx2 : Structure(), AutoCloseable {
    @JvmField
    var cb: Int = 0

    @JvmField
    var PageFaultCount: Int = 0

    @JvmField
    var PeakWorkingSetSize: Long = 0

    @JvmField
    var WorkingSetSize: Long = 0

    @JvmField
    var QuotaPeakPagedPoolUsage: Long = 0

    @JvmField
    var QuotaPagedPoolUsage: Long = 0

    @JvmField
    var QuotaPeakNonPagedPoolUsage: Long = 0

    @JvmField
    var QuotaNonPagedPoolUsage: Long = 0

    @JvmField
    var PagefileUsage: Long = 0

    @JvmField
    var PeakPagefileUsage: Long = 0

    @JvmField
    var PrivateUsage: Long = 0

    @JvmField
    var PrivateWorkingSetSize: Long = 0

    @JvmField
    var SharedCommitUsage: Long = 0

    override fun close() {
      (pointer as? Memory)?.close()
    }
  }
}

private class MacosMemoryUtil : PlatformMemoryUtil() {
  private val libc = Native.load("System", Libc::class.java)

  override fun getCurrentProcessMemoryStatsInner(): MemoryStats? {
    return TaskVMInfo().use { info ->
      val size = IntByReference(info.size() / 4)
      val result = libc.task_info(libc.mach_task_self(), TASK_VM_INFO, info.pointer, size)
      if (result != 0) return@use null
      info.read()

      MacosMemoryStats(
        physFootprint = info.phys_footprint,
        residentSize = info.resident_size,
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

  @Suppress("FunctionName")
  interface Libc : Library {
    fun mach_task_self(): Int
    fun task_info(task: Int, flavor: Int, taskInfo: Pointer, taskInfoCount: IntByReference): Int
  }

  @Suppress("PropertyName", "unused")
  @Structure.FieldOrder(
    "virtual_size",
    "region_count",
    "page_size",
    "resident_size",
    "resident_size_peak",
    "device",
    "device_peak",
    "internal",
    "internal_peak",
    "external",
    "external_peak",
    "reusable",
    "reusable_peak",
    "purgeable_volatile_pmap",
    "purgeable_volatile_resident",
    "purgeable_volatile_virtual",
    "compressed",
    "compressed_peak",
    "compressed_lifetime",
    "phys_footprint",
    "min_address",
    "max_address",
    "ledger_phys_footprint_peak",
    "ledger_purgeable_nonvolatile",
    "ledger_purgeable_novolatile_compressed",
    "ledger_purgeable_volatile",
    "ledger_purgeable_volatile_compressed",
    "ledger_tag_network_nonvolatile",
    "ledger_tag_network_nonvolatile_compressed",
    "ledger_tag_network_volatile",
    "ledger_tag_network_volatile_compressed",
    "ledger_tag_media_footprint",
    "ledger_tag_media_footprint_compressed",
    "ledger_tag_media_nofootprint",
    "ledger_tag_media_nofootprint_compressed",
    "ledger_tag_graphics_footprint",
    "ledger_tag_graphics_footprint_compressed",
    "ledger_tag_graphics_nofootprint",
    "ledger_tag_graphics_nofootprint_compressed",
    "ledger_tag_neural_footprint",
    "ledger_tag_neural_footprint_compressed",
    "ledger_tag_neural_nofootprint",
    "ledger_tag_neural_nofootprint_compressed",
    "limit_bytes_remaining",
    "decompressions",
  )
  class TaskVMInfo : Structure(), AutoCloseable {
    @JvmField
    var virtual_size: Long = 0

    @JvmField
    var region_count: Int = 0

    @JvmField
    var page_size: Int = 0

    @JvmField
    var resident_size: Long = 0

    @JvmField
    var resident_size_peak: Long = 0

    @JvmField
    var device: Long = 0

    @JvmField
    var device_peak: Long = 0

    @JvmField
    var internal: Long = 0

    @JvmField
    var internal_peak: Long = 0

    @JvmField
    var external: Long = 0

    @JvmField
    var external_peak: Long = 0

    @JvmField
    var reusable: Long = 0

    @JvmField
    var reusable_peak: Long = 0

    @JvmField
    var purgeable_volatile_pmap: Long = 0

    @JvmField
    var purgeable_volatile_resident: Long = 0

    @JvmField
    var purgeable_volatile_virtual: Long = 0

    @JvmField
    var compressed: Long = 0

    @JvmField
    var compressed_peak: Long = 0

    @JvmField
    var compressed_lifetime: Long = 0

    @JvmField
    var phys_footprint: Long = 0

    @JvmField
    var min_address: Long = 0

    @JvmField
    var max_address: Long = 0

    @JvmField
    var ledger_phys_footprint_peak: Long = 0

    @JvmField
    var ledger_purgeable_nonvolatile: Long = 0

    @JvmField
    var ledger_purgeable_novolatile_compressed: Long = 0

    @JvmField
    var ledger_purgeable_volatile: Long = 0

    @JvmField
    var ledger_purgeable_volatile_compressed: Long = 0

    @JvmField
    var ledger_tag_network_nonvolatile: Long = 0

    @JvmField
    var ledger_tag_network_nonvolatile_compressed: Long = 0

    @JvmField
    var ledger_tag_network_volatile: Long = 0

    @JvmField
    var ledger_tag_network_volatile_compressed: Long = 0

    @JvmField
    var ledger_tag_media_footprint: Long = 0

    @JvmField
    var ledger_tag_media_footprint_compressed: Long = 0

    @JvmField
    var ledger_tag_media_nofootprint: Long = 0

    @JvmField
    var ledger_tag_media_nofootprint_compressed: Long = 0

    @JvmField
    var ledger_tag_graphics_footprint: Long = 0

    @JvmField
    var ledger_tag_graphics_footprint_compressed: Long = 0

    @JvmField
    var ledger_tag_graphics_nofootprint: Long = 0

    @JvmField
    var ledger_tag_graphics_nofootprint_compressed: Long = 0

    @JvmField
    var ledger_tag_neural_footprint: Long = 0

    @JvmField
    var ledger_tag_neural_footprint_compressed: Long = 0

    @JvmField
    var ledger_tag_neural_nofootprint: Long = 0

    @JvmField
    var ledger_tag_neural_nofootprint_compressed: Long = 0

    @JvmField
    var limit_bytes_remaining: Long = 0

    @JvmField
    var decompressions: Int = 0

    override fun close() {
      (pointer as? Memory)?.close()
    }
  }

  private companion object {
    const val TASK_VM_INFO: Int = 22
  }
}
