// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.limits

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.PersistentFSConstants
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicReference

@Suppress("DEPRECATION")
@ApiStatus.Internal
interface FileSizeLimit {
  val acceptableExtensions: List<String>

  fun getLimits(): ExtensionSizeLimitInfo

  companion object {
    private val EP: ExtensionPointName<FileSizeLimit> = ExtensionPointName("com.intellij.fileEditor.fileSizeChecker")

    private val limitsByExtension: AtomicReference<Map<String, ExtensionSizeLimitInfo>?> = AtomicReference(null)

    @JvmStatic
    @ApiStatus.Internal
    fun getFileLengthToCacheThreshold(): Int = FileUtilRt.LARGE_FOR_CONTENT_LOADING

    private fun getLimitsByExtension(): Map<String, ExtensionSizeLimitInfo> {
      return limitsByExtension.get() ?: limitsByExtension.updateAndGet { getLimits() }!!
    }

    init {
      val extensionPoint = ApplicationManager.getApplication().extensionArea.getExtensionPointIfRegistered<FileSizeLimit>(EP.name)
      extensionPoint?.addChangeListener({ limitsByExtension.set(null) }, null)
    }

    /**
     * Fingerprint of the current state (to contribute to indexing fingerprint)
     */
    fun getFingerprint(): Int {
      return getLimitsByExtension().hashCode()
    }

    @JvmStatic
    fun isTooLarge(fileSize: Long, extension: String? = ""): Boolean {
      val fileContentLoadLimit = getContentLoadLimit(extension)
      return fileSize > fileContentLoadLimit
    }


    @JvmStatic
    fun getContentLoadLimit(extension: String?): Int {
      @Suppress("DEPRECATION")
      val limit = findApplicable(extension ?: "")?.content ?: FileUtilRt.LARGE_FOR_CONTENT_LOADING
      return limit
    }

    @JvmStatic
    fun getContentLoadLimit(): Int = getContentLoadLimit(null)

    @JvmStatic
    fun getIntellisenseLimit(): Int = getIntellisenseLimit(null)

    @JvmStatic
    fun getIntellisenseLimit(extension: String?): Int {
      @Suppress("DEPRECATION")
      val limit = findApplicable(extension ?: "")?.intellijSense ?: PersistentFSConstants.getMaxIntellisenseFileSize()
      return limit
    }

    @JvmStatic
    fun getPreviewLimit(extension: String? = ""): Int {
      @Suppress("DEPRECATION")
      val limit = findApplicable(extension?:"")?.preview ?: FileUtilRt.LARGE_FILE_PREVIEW_SIZE
      return limit
    }

    private fun getLimits(): Map<String, ExtensionSizeLimitInfo> {
      val extensions = EP.extensionsIfPointIsRegistered

      val duplicates: Map<String, Int> = extensions.flatMap { it.acceptableExtensions }.groupingBy { it }.eachCount().filter { it.value > 1 }
      duplicates.forEach {(element, count) ->
          thisLogger().warn("For file type $element $count limits are registered. Extensions: ${extensions.joinToString { 
            "${it.javaClass.name}: ${it.acceptableExtensions.joinToString()}" }}")
        }

      val newLimits = extensions.flatMap { extension -> extension.acceptableExtensions.map { it to extension.getLimits() } }.toMap()
      return newLimits
    }

    private fun findApplicable(extension: String): ExtensionSizeLimitInfo? = getLimitsByExtension()[extension]
  }
}