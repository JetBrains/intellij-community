package com.intellij.ide.customize.transferSettings.providers.vswin.parsers

import com.jetbrains.rd.util.lifetime.Lifetime
import com.intellij.ide.customize.transferSettings.providers.vswin.utilities.VSHive
import com.intellij.ide.customize.transferSettings.utils.Ini
import com.intellij.openapi.diagnostic.logger
import java.io.File

class VSIsolationIniParser private constructor(val lifetime: Lifetime, val hive: VSHive, val file: File? = null) {
  companion object {
    private val veryBadLifetime = Lifetime.Eternal

    private val logger = logger<VSIsolationIniParser>()

    fun create(hive: VSHive): VSIsolationIniParser? {
      val file = getIsolationFile(veryBadLifetime, hive)

      if (file == null) {
        logger.info("file is null")
        return null
      }

      if (!file.exists()) {
        if (hive.instanceId != null) {
          logger.warn("Visual Studio is new, but no isolation file. This incident will be reported.")
        }
        else {
          logger.info("This Visual Studio is too old, no isolation file for you")
        }
        return null
      }

      if (!file.canRead()) {
        logger.warn("Can't read file even though it exists. VS is running?")
        return null
      }

      try {
        return VSIsolationIniParser(veryBadLifetime, hive, file)
      }
      catch (t: Throwable) {
        logger.warn("Failed to parse isolation file")
        logger.warn(t)
      }
      return null
    }

    private fun getIsolationFile(lifetime: Lifetime, hive: VSHive): File? {
      val env = hive.registry?.envPath

      val dir = env?.first?.resolve("devenv.isolation.ini")
      if (dir == null) {
        logger.info("dir null")
        return null
      }

      val file = File(dir.toString())
      if (!file.exists()) {
        logger.info("file is null")
        return null
      }
      if (!file.canRead()) {
        logger.info("cant read file")
        return null
      }

      return file
    }

  }

  private val ini: Ini

  val isPreview by lazy { get("ChannelTitle")?.lowercase() == "preview" }
  val edition: String? by lazy { vsShortIdToEdition(get("SKU")) }
  val wasSetupFinished by lazy { get("SetupFinished")?.toBoolean() == true }
  val installationVersion: String? by lazy { get("InstallationVersion") }

  init {
    val file2 = file ?: getIsolationFile(lifetime, hive)
    requireNotNull(file2)

    ini = Ini(file2)
  }

  private fun get(key: String): String? {

    return ini.get("Info", key)
  }

  private fun vsShortIdToEditionDict(shortId: String?): String? {
    return when (shortId?.lowercase()) {
      "community" -> "Community"
      "pro", "professional" -> "Professional"
      "ent", "enterprise" -> "Enterprise"
      // Some obscure ones
      "std" -> "Standard"
      "vsta" -> "Team Edition for Software Architects"
      "vstt" -> "Team Edition for Software Testers"
      "vstd" -> "Team Edition for Software Developers"
      "vsts" -> "Team Suite Edition"
      "ide" -> "Shell Edition"
      else -> null
    }
  }

  private fun vsShortIdToEdition(shortId: String?): String? {
    if (shortId == null) {
      logger.debug("shortId null")
      return null
    }
    if (vsShortIdToEditionDict(shortId) != null) {
      return vsShortIdToEditionDict(shortId)
    }
    logger.info("Unknown edition $shortId")
    return null //WordUtils.capitalizeFully(shortId)
  }
}