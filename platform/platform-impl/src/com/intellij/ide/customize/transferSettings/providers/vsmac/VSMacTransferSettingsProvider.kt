package com.intellij.ide.customize.transferSettings.providers.vsmac

import com.intellij.icons.AllIcons
import com.intellij.ide.customize.transferSettings.models.IdeVersion
import com.intellij.ide.customize.transferSettings.providers.DefaultImportPerformer
import com.intellij.ide.customize.transferSettings.providers.TransferSettingsProvider
import com.intellij.ide.customize.transferSettings.providers.vsmac.VSMacSettingsProcessor.Companion.getGeneralSettingsFile
import com.intellij.ide.customize.transferSettings.providers.vsmac.VSMacSettingsProcessor.Companion.vsPreferences
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.SmartList
import com.jetbrains.rd.util.Date
import java.nio.file.Files
import java.nio.file.Paths

private val logger = logger<VSMacTransferSettingsProvider>()

class VSMacTransferSettingsProvider : TransferSettingsProvider {
  override val name = "Visual Studio for Mac"

  override fun isAvailable() = SystemInfoRt.isMac

  override fun getIdeVersions(skipIds: List<String>) = when (val version = detectVSForMacVersion()) {
    null -> SmartList()
    else -> SmartList(getIdeVersion(version))
  }

  override fun getImportPerformer(ideVersion: IdeVersion) = DefaultImportPerformer()

  private fun getIdeVersion(version: String) = IdeVersion(
    name = "Visual Studio for Mac",
    id = "VSMAC",
    icon = AllIcons.Idea_logo_welcome,
    lastUsed = getLastUsed(version),
    settings = VSMacSettingsProcessor().getProcessedSettings(version),
    provider = this
  )

  private fun detectVSForMacVersion(): String? {
    val pathToDir = Paths.get(vsPreferences)

    if (!Files.isDirectory(pathToDir)) {
      return null
    }

    var max = 0L
    var lastUsedVersion: String? = null
    Files.list(pathToDir).use { files ->
      for (path in files) {
        if (!Files.isDirectory(path) && Files.isHidden(path)) continue

        val maybeVersion = path.fileName.toString()
        val recentlyUsedFile = getGeneralSettingsFile(maybeVersion)

        if (recentlyUsedFile.exists()) {
          val lastModificationTime = recentlyUsedFile.lastModified()
          if (max < lastModificationTime) {
            max = lastModificationTime
            lastUsedVersion = maybeVersion
          }
        }
      }
    }

    return lastUsedVersion
  }

  private fun getLastUsed(version: String): Date? {
    val recentlyUsedFile = getGeneralSettingsFile(version)

    return try {
      Date(recentlyUsedFile.lastModified())
    }
    catch (t: Throwable) {
      logger.warn(t)
      null
    }
  }

}
