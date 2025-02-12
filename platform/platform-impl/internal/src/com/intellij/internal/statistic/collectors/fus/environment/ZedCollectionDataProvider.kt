// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.environment

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

private val logger = logger<ZedCollectionDataProvider>()

/**
 * Collects anonymous data about the presense of Zed editor to improve the "Import settings from external editor" feature
 * and prioritize support for popular editors.
 *
 * This includes:
 * - Detecting if Zed editor is installed across platforms (Windows, macOS, Linux).
 *
 * The data is completely anonymized and no personally identifiable information is captured.
 */
internal class ZedCollectionDataProvider : ExternalEditorCollectionDataProvider() {

  private val zedHomePath: Path? = getZedHomePath()

  fun isZedDetected(): Boolean {
    val isZedHomePathValid = zedHomePath?.exists() == true
    logger.debug { "Zed detected: $isZedHomePathValid" }
    return isZedHomePathValid
  } 

  private fun getZedHomePath(): Path? {
    if (SystemInfo.isMac || SystemInfo.isLinux) {
      val xdgConfigHomeEnvValue = try {
        System.getenv("XDG_CONFIG_HOME")
      }
      catch (e: SecurityException) {
        logger.debug(e)
        null
      }

      logger.debug { "XDG_CONFIG_HOME env var value: $xdgConfigHomeEnvValue" }

      if (xdgConfigHomeEnvValue != null) {
        return try {
          val zedHomePath = Paths.get(xdgConfigHomeEnvValue, "zed")
          logger.debug { "Zed home path: $zedHomePath" }
          zedHomePath
        }
        catch (e: InvalidPathException) {
          logger.debug(e)
          null
        }
      }

      val zedHomePath = homeDirectory?.resolve(Paths.get(".config", "zed"))
      logger.debug { "Zed home path: $zedHomePath" }
      return zedHomePath
    }

    logger.debug { "The current OS is not supported by Zed" }
    return null
  }
}