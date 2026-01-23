// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.limits

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.PersistentFSConstants
import com.intellij.openapi.vfs.limits.FileSizeLimit.Companion.getDefaultContentLoadLimit
import com.intellij.openapi.vfs.limits.FileSizeLimit.Companion.getDefaultIntellisenseLimit
import com.intellij.openapi.vfs.limits.FileSizeLimit.Companion.getDefaultPreviewLimit
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

/**
 * Extension point (`com.intellij.fileEditor.fileSizeChecker`) for limiting file sizes for a specific file type / extension.
 *
 * 3 kinds of limits are defined ([ExtensionSizeLimitInfo]):
 * - Content loading size limit
 * - Intellisense size limit
 * - Preview size limit
 *
 * For each kind there is a default limit: [getDefaultContentLoadLimit], [getDefaultIntellisenseLimit], [getDefaultPreviewLimit],
 * which could be enlarged on a per-file-extension basis.
 *
 * **BEWARE**: default limit could be *enlarged but not reduced*: if an extension provides a specific limit smaller than the apt
 * default value -- the value provided by the extension will be ignored, and the default value will be used instead.
 */
@Suppress("DEPRECATION")
@ApiStatus.Internal
interface FileSizeLimit {
  val acceptableExtensions: List<String>

  fun getLimits(): ExtensionSizeLimitInfo

  companion object {
    private val EP: ExtensionPointName<FileSizeLimit> = ExtensionPointName("com.intellij.fileEditor.fileSizeChecker")

    private val limitsByExtension: AtomicReference<Map<String, ExtensionSizeLimitInfo>?> = AtomicReference(null)

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
      val limitsByExtensions = getLimitsByExtension().entries
        .sortedBy { it.key }
        .map { it.key to it.value }
        .toList()
      return limitsByExtensions.hashCode()
    }

    @JvmStatic
    fun isTooLargeForContentLoading(fileSize: Long, extension: String?): Boolean {
      val fileContentLoadLimit = getContentLoadLimit(extension)
      return fileSize > fileContentLoadLimit
    }


    /**
     * It is not only the **default** limit for the file types/extensions without an explicitly defined one,
     * but also a **minimum** file size limit -- i.e., if a custom limit is defined, but it is less than
     * [getDefaultContentLoadLimit] -- it's value is ignored, and [getDefaultContentLoadLimit] is used
     * instead
     */
    //MAYBE RC: rename to getMinContentLoadLimit()?
    @JvmStatic
    fun getDefaultContentLoadLimit(): Int = FileUtilRt.LARGE_FOR_CONTENT_LOADING


    /**
     * @return [ExtensionSizeLimitInfo.content] if specific size limit is registered for an [extension],
     *         or [getDefaultContentLoadLimit] if not, or if the registered size limit is less than default
     */
    @JvmStatic
    fun getContentLoadLimit(extension: String?): Int {
      return getValue(extension, ExtensionSizeLimitInfo::content, getDefaultContentLoadLimit())
    }


    /**
     * It is not only the **default** limit for the file types/extensions without an explicitly defined one,
     * but also a **minimum** file size limit -- i.e., if a custom limit is defined, but it is less than
     * [getDefaultIntellisenseLimit] -- it's value is ignored, and [getDefaultIntellisenseLimit] is used
     * instead
     */
    //MAYBE RC: rename to getMinIntellisenseLimit()
    @JvmStatic
    fun getDefaultIntellisenseLimit(): Int = PersistentFSConstants.getMaxIntellisenseFileSize()

    @JvmStatic
    fun getIntellisenseLimit(extension: String?): Int {
      @Suppress("DEPRECATION")
      return getValue(extension, ExtensionSizeLimitInfo::intellijSense, getDefaultIntellisenseLimit())
    }

    /**
     * It is not only the **default** limit for the file types/extensions without an explicitly defined one,
     * but also a **minimum** file size limit -- i.e., if a custom limit is defined, but it is less than
     * [getDefaultPreviewLimit] -- it's value is ignored, and [getDefaultPreviewLimit] is used instead
     */
    //MAYBE RC: getMinPreviewLimit()?
    @JvmStatic
    fun getDefaultPreviewLimit(): Int = FileUtilRt.LARGE_FILE_PREVIEW_SIZE

    @JvmStatic
    fun getPreviewLimit(extension: String?): Int {
      return getValue(extension, ExtensionSizeLimitInfo::preview, getDefaultPreviewLimit())
    }


    /** @return `getter( getLimitsByExtension()[extension] )`, but no less than [minValue] */
    private fun getValue(extension: String?, getter: (ExtensionSizeLimitInfo) -> Int?, minValue: Int): Int {
      //getValue(..., minValue) always returns value >= minValue. Such semantics may look a bit unusual,
      //but it seems there is no use-case for _reducing_ file-size-limit below the default one.
      //If such a use-case arrives -- semantics could be changed, though.
      val providedValue = findApplicable(extension ?: "")?.let(getter) ?: return minValue
      return max(providedValue, minValue)
    }

    private fun getLimits(): Map<String, ExtensionSizeLimitInfo> {
      val extensions = EP.extensionsIfPointIsRegistered

      val duplicates: Map<String, Int> = extensions.flatMap { it.acceptableExtensions }.groupingBy { it }.eachCount().filter { it.value > 1 }
      duplicates.forEach { (element, count) ->
        thisLogger().warn("For file type $element $count limits are registered. Extensions: ${
          extensions
            .filter { it.acceptableExtensions.contains(element) }
            .joinToString { extension ->
              "${extension.javaClass.name}: ${extension.acceptableExtensions.joinToString()}"
            }
        }")
      }

      val newLimits = extensions.flatMap { extension -> extension.acceptableExtensions.map { it to extension.getLimits() } }.toMap()
      return newLimits
    }

    private fun findApplicable(extension: String): ExtensionSizeLimitInfo? = getLimitsByExtension()[extension]
  }
}