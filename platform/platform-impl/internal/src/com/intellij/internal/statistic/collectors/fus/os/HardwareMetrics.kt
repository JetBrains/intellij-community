// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.os

import com.intellij.util.system.GraphicsCardInfo
import com.intellij.util.system.MemoryModuleInfo
import org.jetbrains.annotations.ApiStatus
import java.util.Locale

/** Maps the raw hardware data of the OS to the closed value sets that [SystemRuntimeCollector] reports. */
@ApiStatus.Internal
object HardwareMetrics {
  val GPU_VENDORS: List<String> = listOf("nvidia", "amd", "intel", "apple", "other")
  val GPU_TYPES: List<String> = listOf("integrated", "discrete", "virtual", "software", "unknown")
  val MEMORY_TYPES: List<String> = listOf(
    "ddr3", "ddr4", "ddr5",
    "lpddr3", "lpddr4", "lpddr4x", "lpddr5", "lpddr5x",
    "mixed", "unknown",
  )

  private val SOFTWARE_GPU_MARKS = listOf("basic display", "basic render", "llvmpipe", "swiftshader", "softpipe")
  private val VIRTUAL_GPU_MARKS = listOf(
    "virtual", "vmware", "virtio", "qxl", "qemu", "bochs", "parallels", "hyper-v", "red hat", "remote display",
  )

  /** `Arc A770`, `Arc B580` and `Arc Pro A60` are cards. `Arc Graphics`, `Arc 140V` and `Arc B390` are part of a CPU. */
  private val INTEL_DISCRETE = Regex("\\barc (pro [ab]\\d{2}|a\\d{3}|b[57]\\d{2})|\\bdg[12]\\b|iris xe max")
  /** `Vega 56` and `Vega 64` are cards. `Vega 3` to `Vega 11` are part of a CPU. */
  private val AMD_DISCRETE = Regex("\\brx \\d{3,4}|\\bvega (56|64)\\b|radeon pro|firepro")
  private val AMD_INTEGRATED = Regex("radeon graphics|\\bvega \\d{1,2}\\b|vega series|\\b\\d{3}m\\b")

  /** @param raw the vendor as [GraphicsCardInfo.vendor] holds it */
  fun gpuVendor(raw: String): String {
    val lower = raw.lowercase(Locale.ROOT)
    return when {
      lower.contains("nvidia") -> "nvidia"
      lower.contains("advanced micro devices") || lower.contains("amd") -> "amd"
      lower.contains("intel") -> "intel"
      lower.contains("apple") -> "apple"
      else -> "other"
    }
  }

  /** Reads the type from the vendor and the marketing name. A name without a known pattern gives `unknown`. */
  fun gpuType(card: GraphicsCardInfo): String {
    val name = card.name.lowercase(Locale.ROOT).replace("(r)", "").replace("(tm)", "")
    val vendor = card.vendor.lowercase(Locale.ROOT)
    return when {
      SOFTWARE_GPU_MARKS.any { name.contains(it) } -> "software"
      VIRTUAL_GPU_MARKS.any { name.contains(it) || vendor.contains(it) } -> "virtual"
      else -> when (gpuVendor(card.vendor)) {
        "apple" -> "integrated"
        "nvidia" -> "discrete"
        "intel" -> if (INTEL_DISCRETE.containsMatchIn(name)) "discrete" else "integrated"
        "amd" -> when {
          AMD_DISCRETE.containsMatchIn(name) -> "discrete"
          AMD_INTEGRATED.containsMatchIn(name) -> "integrated"
          else -> "unknown"
        }
        else -> "unknown"
      }
    }
  }

  /** @return the type that all modules share, `mixed` for modules of different types, `unknown` without a known type */
  fun memoryType(modules: List<MemoryModuleInfo>): String {
    val types = modules.mapNotNull { module ->
      val type = module.type.trim().lowercase(Locale.ROOT)
      type.takeIf { it in MEMORY_TYPES }
    }.distinct()
    return when (types.size) {
      0 -> "unknown"
      1 -> types.single()
      else -> "mixed"
    }
  }

  /** @return the data rate of the fastest module in megatransfers per second, or `null` when the OS reports none */
  fun memorySpeedMts(modules: List<MemoryModuleInfo>): Long? = modules.mapNotNull { it.speedMts }.filter { it > 0 }.maxOrNull()
}
