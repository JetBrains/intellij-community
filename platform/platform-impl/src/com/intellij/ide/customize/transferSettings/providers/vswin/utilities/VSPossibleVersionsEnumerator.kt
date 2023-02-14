package com.intellij.ide.customize.transferSettings.providers.vswin.utilities

import com.intellij.ide.customize.transferSettings.db.WindowsEnvVariables
import com.intellij.openapi.diagnostic.logger
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.io.File

class VSPossibleVersionsEnumerator {
  private val logger = logger<VSPossibleVersionsEnumerator>()

  fun get(): List<VSHive> {
    return (enumOldPossibleVersions() + enumNewPossibleVersions()).apply {
      logger.info("List of potential ides: ${this.joinToString(",") { it.hiveString }}")
    }
  }

  private fun enumOldPossibleVersions(): List<VSHive> {
    val registry = try {
      Advapi32Util.registryGetKeys(WinReg.HKEY_CURRENT_USER, "SOFTWARE\\Microsoft\\VisualStudio")
    }
    catch (t: Throwable) {
      logger.warn(t)
      return emptyList()
    }

    if (registry == null) {
      logger.info("No old vs found (no registry keys)")
      return emptyList()
    }

    return registry.mapNotNull { VSHive.parse(it, VSHive.Types.Old) }
  }

  private fun enumNewPossibleVersions(): List<VSHive> {
    val dir = File("${WindowsEnvVariables.localApplicationData}\\Microsoft\\VisualStudio")

    if (!dir.exists()) {
      logger.warn("visual studio dir does not exist")
      return emptyList()
    }

    return try {
      dir.list()?.mapNotNull { VSHive.parse(it, VSHive.Types.New) }.let {
        if (it == null) {
          logger.warn("list was null")
          emptyList()
        }
        else it
      }
    }
    catch (t: Throwable) {
      logger.warn("Failed to read ${dir.absolutePath}")
      logger.warn(t)
      emptyList()
    }

  }
}