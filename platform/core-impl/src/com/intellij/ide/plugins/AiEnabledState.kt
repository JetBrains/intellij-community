// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.NioFiles
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Holds the AI flag of this configuration directory.
 *
 * The flag controls only the JetBrains internal plugins that depend on [AIR_AI_MARKER_MODULE_ID]. It is not a general
 * switch of every AI feature of the IDE.
 *
 * AI is enabled until an owner disables it. The file [AI_DISABLED_FILENAME] is present while AI is off. The IDE then
 * keeps [AIR_AI_MARKER_MODULE_ID] out of the plugin set, so every content module that depends on that marker goes out
 * with it.
 */
@Internal
object AiEnabledState {
  /** The name of the empty file. The file is present while AI is off, and absent while AI is on. */
  const val AI_DISABLED_FILENAME: @NonNls String = "ai_disabled.txt"

  /** `true` or `false`. The value replaces the file for a test or a development run. */
  const val AI_ENABLED_PROPERTY: @NonNls String = "idea.ai.enabled"

  private val logger: Logger
    get() = Logger.getInstance(AiEnabledState::class.java)

  private val filePath: Path
    get() = PathManager.getConfigDir().resolve(AI_DISABLED_FILENAME)

  /** Returns the flag of [AI_ENABLED_PROPERTY], or `false` while the file is present, or `true`. */
  fun isEnabled(): Boolean {
    propertyValue()?.let { return it }
    return synchronized(this) { !Files.exists(filePath) }
  }

  /**
   * Creates the file to disable AI, or deletes the file to enable AI.
   *
   * @return `true` if the stored flag changed.
   */
  fun setEnabled(enabled: Boolean): Boolean {
    synchronized(this) {
      val path = filePath
      try {
        if (enabled) {
          if (!Files.deleteIfExists(path)) {
            return false
          }
        }
        else {
          if (Files.exists(path)) {
            return false
          }
          NioFiles.createDirectories(path.parent)
          Files.createFile(path)
        }
      }
      catch (e: IOException) {
        logger.warn("failed to write the AI flag to $path", e)
        return false
      }
      logger.info("the stored AI flag is now $enabled")
      return true
    }
  }

  private fun propertyValue(): Boolean? {
    val value = System.getProperty(AI_ENABLED_PROPERTY) ?: return null
    val parsed = value.trim().toBooleanStrictOrNull()
    if (parsed == null) {
      logger.warn("the value '$value' of $AI_ENABLED_PROPERTY is not 'true' or 'false', so the file decides")
    }
    return parsed
  }
}
