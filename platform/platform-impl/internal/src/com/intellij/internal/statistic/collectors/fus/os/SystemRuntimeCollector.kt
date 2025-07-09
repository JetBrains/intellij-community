// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.os

import com.intellij.diagnostic.VMOptions
import com.intellij.internal.DebugAttachDetector
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.Boolean
import com.intellij.internal.statistic.eventLog.events.EventFields.Int
import com.intellij.internal.statistic.eventLog.events.EventFields.Long
import com.intellij.internal.statistic.eventLog.events.EventFields.String
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.utils.StatisticsUtil
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.Version
import com.intellij.util.currentJavaVersion
import com.intellij.util.system.CpuArch
import com.intellij.util.ui.UIUtil
import com.sun.management.OperatingSystemMXBean
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.util.*
import kotlin.math.min
import kotlin.math.roundToInt

@ApiStatus.Internal
class SystemRuntimeCollector : ApplicationUsagesCollector() {
  private val GROUP = EventLogGroup("system.runtime", 21)

  private val COLLECTORS = listOf("Serial", "Parallel", "CMS", "G1", "Z", "Shenandoah", "Epsilon", "Other")
  private val ARCHITECTURES = listOf("x86", "x86_64", "arm64", "other", "unknown")
  private val VENDORS = listOf("JetBrains", "Apple", "Oracle", "Sun", "IBM", "Azul", "Other")
  private val VM_OPTIONS = listOf("Xmx", "Xms", "SoftRefLRUPolicyMSPerMB", "ReservedCodeCacheSize")
  private val SYSTEM_PROPERTIES = listOf("splash", "nosplash")
  private val RENDERING_PIPELINES = listOf("Metal", "OpenGL")
  @Suppress("SpellCheckingInspection")
  private val OS_VMS = listOf("none", "xen", "kvm", "vmware", "hyperv", "other", "unknown")

  private val CORES = GROUP.registerEvent("cores", EventFields.BoundedInt("value", intArrayOf(1, 2, 4, 6, 8, 12, 16, 20, 24, 32, 64)))
  private val MEMORY_SIZE =
    GROUP.registerEvent("memory.size", EventFields.BoundedInt("gigabytes", intArrayOf(1, 2, 4, 8, 12, 16, 24, 32, 48, 64, 128, 256)))
  private val SWAP_SIZE = GROUP.registerEvent("swap.size", Int("gigabytes"))
  private val DISK_SIZE = GROUP.registerEvent("disk.size", Int("index_partition_size"), Int("index_partition_free"))
  private val GC = GROUP.registerEvent("garbage.collector", String("name", COLLECTORS))
  private val JVM = GROUP.registerEvent("jvm", EventFields.VersionByObject, String("arch", ARCHITECTURES), String("vendor", VENDORS))
  private val JVM_OPTION = GROUP.registerEvent("jvm.option", String("name", VM_OPTIONS), Long("value"))
  private val SYSTEM_PROPERTY = GROUP.registerEvent("jvm.client.properties", String("name", SYSTEM_PROPERTIES), Boolean("value"))
  private val DEBUG_AGENT = GROUP.registerEvent("debug.agent", EventFields.Enabled)
  private val AGENTS_COUNT = GROUP.registerEvent("agents.count", Int("java_agents"), Int("native_agents"))
  private val RENDERING = GROUP.registerEvent("rendering.pipeline", String("name", RENDERING_PIPELINES))
  private val OS_VM = GROUP.registerEvent("os.vm", String("name", OS_VMS))

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> {
    val result = mutableSetOf<MetricEvent>()

    result += CORES.metric(Runtime.getRuntime().availableProcessors())

    val physicalMemoryData = getPhysicalMemoryAndSwapSize()
    if (physicalMemoryData != null) {
      val (physicalMemory, swapSize) = physicalMemoryData
      result += MEMORY_SIZE.metric(physicalMemory)
      result += SWAP_SIZE.metric(swapSize)
    }

    val indexVolumeData = getIndexVolumeSizeAndFreeSpace()
    if (indexVolumeData != null) {
      val (size, freeSpace) = indexVolumeData
      result += DISK_SIZE.metric(size, freeSpace)
    }

    result += GC.metric(getGcName())

    result += JVM.metric(
      Version(1, currentJavaVersion().feature, 0),
      CpuArch.CURRENT.name.lowercase(Locale.ENGLISH),
      getJavaVendor())

    for (option in collectJvmOptions()) {
      result += JVM_OPTION.metric(option.key, option.value)
    }

    for (property in collectSystemProperties()) {
      result += SYSTEM_PROPERTY.metric(property.key, property.value.toBoolean())
    }

    result += DEBUG_AGENT.metric(DebugAttachDetector.isDebugEnabled())

    val (javaAgents, nativeAgents) = countAgents()
    result += AGENTS_COUNT.metric(javaAgents, nativeAgents)

    // proper detection is implemented only for macOS
    if (SystemInfo.isMac) result += RENDERING.metric(getRenderingPipelineName())

    result += OS_VM.metric(getOsVirtualization())

    return result
  }

  private fun getPhysicalMemoryAndSwapSize(): Pair<Int, Int>? {
    try {
      @Suppress("FunctionName") fun GiB(bytes: Long) = (bytes.toDouble() / (1 shl 30)).roundToInt()
      val bean = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
      val physicalMemory = GiB(bean.totalMemorySize)
      val swapSize = StatisticsUtil.roundToPowerOfTwo(min(GiB(bean.totalSwapSpaceSize), 256))
      return physicalMemory to swapSize
    }
    catch (_: Exception) { }  // ignoring internal errors in JRE code
    return null
  }

  private fun getIndexVolumeSizeAndFreeSpace(): Pair<Int, Int>? {
    try {
      val fileStore = Files.getFileStore(PathManager.getIndexRoot())
      val totalSpace = fileStore.totalSpace
      if (totalSpace > 0L) {
        val size = min(1 shl 14, StatisticsUtil.roundToPowerOfTwo((totalSpace shr 30).toInt()))  // ATM, the biggest popular consumer HDDs are ~16 TB
        val freeSpace = (fileStore.usableSpace * 100.0 / totalSpace).toInt()
        return size to freeSpace
      }
    }
    catch (_: IOException) { }  // missing directory or something
    catch (_: UnsupportedOperationException) { }  // some non-standard FS
    catch (_: SecurityException) { }  // the security manager denies reading of FS attributes
    return null
  }

  private fun getGcName(): String {
    for (gc in ManagementFactory.getGarbageCollectorMXBeans()) {
      if (gc.name == "MarkSweepCompact" || gc.name == "Copy") return "Serial"       // -XX:+UseSerialGC
      if (gc.name == "PS MarkSweep" || gc.name == "PS Scavenge") return "Parallel"  // -XX:+UseParallelGC
      if (gc.name == "ConcurrentMarkSweep" || gc.name == "ParNew") return "CMS"     // -XX:+UseConcMarkSweepGC
      if (gc.name.startsWith("G1 ")) return "G1"                                    // -XX:+UseG1GC
      if (gc.name.startsWith("ZGC ")) return "Z"                                    // -XX:+UseZGC
      if (gc.name.startsWith("Shenandoah ")) return "Shenandoah"                    // -XX:+UseShenandoahGC
      if (gc.name.startsWith("Epsilon ")) return "Epsilon"                          // -XX:+UseEpsilonGC
    }
    return "Other"
  }

  private fun getRenderingPipelineName() = if (UIUtil.isMetalRendering()) "Metal" else "OpenGL"

  private fun getJavaVendor(): String =
    when {
      SystemInfo.isJetBrainsJvm -> "JetBrains"
      SystemInfo.isOracleJvm -> "Oracle"
      SystemInfo.isIbmJvm -> "IBM"
      SystemInfo.isAzulJvm -> "Azul"
      else -> "Other"
    }

  private fun collectJvmOptions(): Map<String, Long> =
    ManagementFactory.getRuntimeMXBean().inputArguments.asSequence()
      .map { arg ->
        try {
          fun parse(arg: String, start: Int) = VMOptions.parseMemoryOption(arg.substring(start)) shr 20
          fun roundDown(value: Long, vararg steps: Long) = steps.findLast { it <= value } ?: 0
          when {
            arg.startsWith("-Xms") -> "Xms" to roundDown(parse(arg, 4), 64, 128, 256, 512)
            arg.startsWith("-Xmx") -> "Xmx" to roundDown(parse(arg, 4), 512, 750, 1000, 1024, 1500, 2000, 2048, 3000, 4000, 4096, 6000, 8000)
            arg.startsWith("-XX:SoftRefLRUPolicyMSPerMB=") -> "SoftRefLRUPolicyMSPerMB" to roundDown(parse(arg, 28), 50, 100)
            arg.startsWith("-XX:ReservedCodeCacheSize=") -> "ReservedCodeCacheSize" to roundDown(parse(arg, 26), 240, 300, 400, 500)
            else -> null
          }
        }
        catch (_: IllegalArgumentException) {
          null
        }
      }
      .filterNotNull()
      .toMap()

  private fun collectSystemProperties(): Map<String, String> =
    SYSTEM_PROPERTIES.asSequence()
      .map { it to System.getProperty(it) }
      .filter { it.second != null }
      .toMap()

  private fun countAgents(): Pair<Int, Int> {
    val args = ManagementFactory.getRuntimeMXBean().inputArguments
    val javaAgents = args.count { arg -> arg.startsWith("-javaagent:") }
    val nativeAgents = args.count { arg -> arg.startsWith("-agentlib:") || arg.startsWith("-agentpath:") }
    return javaAgents to nativeAgents
  }

  /**
   * Trying to detect if we are running inside a virtual machine; supported only in JBR (JBR-6769).
   */
  private fun getOsVirtualization(): String {
    val vm = System.getProperty("intellij.os.virtualization")?.lowercase() ?: "unknown"
    return if (vm in OS_VMS) vm else "other"
  }
}
