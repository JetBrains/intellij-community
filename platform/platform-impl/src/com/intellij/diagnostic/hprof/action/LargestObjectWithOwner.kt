/*
 * TODO: license header
 */
package com.intellij.diagnostic.hprof.action

import com.intellij.ide.plugins.PluginUtils
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.util.application
import org.jetbrains.diogen.analysis.oom.HeapObject
import org.jetbrains.diogen.analysis.oom.OomReportAnalyzer
import java.nio.CharBuffer

// Size threshold for reporting the largest object
private const val OBJECT_SIZE_THRESHOLD = 500_000_000L

internal data class LargestObjectWithOwner(
  val largestObject: HeapObject,
  val descriptor: PluginDescriptor?,
) {
  companion object {
    fun isOomReport(reportText: String): Boolean {
      return reportText.lineSequence().firstOrNull()?.contains("= HISTOGRAM =") == true
    }

    fun find(oomReport: String): LargestObjectWithOwner? {
      require(isOomReport(oomReport)) { "The provided text is not a full OOM report." }

      val largestObject = OomReportAnalyzer.getLargestObject(CharBuffer.wrap(oomReport)) ?: return null
      return findLargestObjectOwner(largestObject)
    }

    private fun findLargestObjectOwner(largestObject: HeapObject): LargestObjectWithOwner {
      return LargestObjectWithOwner(
        largestObject = largestObject,
        descriptor = PluginUtils.getPluginDescriptorOrPlatformByClassName(largestObject.className))
    }
  }

  // Implementation based on com.intellij.performanceTesting.freezes.PluginFreezeWatcher
  fun isWorthReportingToUser(): Boolean {
    if (application.isInternal || application.isEAP) return true // report everything for internal & EAP builds

    // TODO: is Registry key needed?
    //if (Registry.`is`("ide.diagnostics.notification.oom.in.bundled.plugins")) return true // report everything with resolved plugin if flag set

    if (descriptor == null) return false // don't report unresolved plugins

    // TODO: could be based on the max heap size (sizeInBytes < maxHeap * 0.35)
    if (largestObject.sizeInBytes < OBJECT_SIZE_THRESHOLD) return false // don't report non-significant objects

    return !descriptor.isBundled
           && !descriptor.isImplementationDetail
           && !ApplicationInfoImpl.getShadowInstance().isEssentialPlugin(descriptor.pluginId)
  }
}
