package com.intellij.ide.customize.transferSettings.providers.vswin.utilities

import com.jetbrains.rd.util.lifetime.Lifetime
import com.intellij.ide.customize.transferSettings.providers.vswin.parsers.VSIsolationIniParser
import com.intellij.ide.customize.transferSettings.providers.vswin.parsers.VSRegistryParserNew
import com.intellij.openapi.diagnostic.logger
import java.util.*

class VSHiveDetourFileNotFoundException : Exception()
class VSHiveDetourFileReadErrorException(t: Throwable?) : Exception(t)

private val logger = logger<VSHive>()
// Example: "15.0_a0848a47Exp", where VS version = "15.0", Instance Id = "a0848a47", Root Suffix = "Exp".
class VSHive(val version: Version2, val instanceId: String? = null, val rootSuffix: String? = null) {
  enum class Types {
    Old, New, All
  }
  val hiveString: String
    get() = "$version${if (instanceId == null) "" else "_$instanceId"}${rootSuffix ?: ""}"
  val presentationString: String
    get() = "Visual Studio ${productVersionTextRepresentation()}"

  val registry by lazy { VSRegistryParserNew.create(this) }
  val isolation by lazy { VSIsolationIniParser.create(this) }

  val isInstalled by lazy { registry?.envPath?.second.let { it != null && it.exists() } }
  val lastUsage by lazy { registry?.settingsFile?.lastModified().let { Date(it ?: 0) } }
  val edition by lazy { isolation?.edition }

  init {
    if (version.major >= 15 && instanceId?.length != 8) {
      throw IllegalArgumentException("Since Visual Studio 15 instanceId is required or bad instanceId is supplied (${instanceId})")
    }
  }

  override fun toString(): String {
    return "Visual Studio ${productVersionTextRepresentation()} ($hiveString)"
  }

  private fun productVersionTextRepresentation(): String {
    return when (version.major) {
      17 -> "2022"
      16 -> "2019"
      15 -> "2017"
      14 -> "2015"
      12 -> "2013"
      11 -> "2012"
      else -> "Ver. ${version.major}.${version.minor}"
    }
  }

  companion object {
    const val LATEST_VS_VERSION = 16

    val regex = Regex("\\b(?:(?:([0-9]{1,2}).([0-9]))(?:_([a-fA-F0-9]{8}))?([a-zA-Z0-9]*))\\b")

    fun parse(hive: String, type: Types = Types.All): VSHive? {
      logger.info("Starting $hive on type $type")

      val spl = regex.find(hive) ?: return null
      val (maj, min, instId, rtSuf) = spl.destructured

      val ver = try {
        Version2(maj.toInt(), min.toInt())
      }
      catch (e: NumberFormatException) {
        logger.warn("Bad major or minor version number ($hive)")
        return null
      }

      if (type == Types.Old && (ver.major != 11 && ver.major != 12 && ver.major != 14)) {
        logger.trace("Wanted to access only old versions, returning ($hive)")
        return null
      }

      if (type == Types.New && instId.length != 8) {
        logger.warn("Requested only new vs, but got something other ($hive)")
        return null
      }

      if (maj.toInt() < 14) { // 2015
        logger.warn("Unsupported version ($hive)")
        return null
      }

      /*val newVsProp = System.getProperty("rider.transfer.newVS")?.toBoolean()
      if (maj.toInt() > LATEST_VS_VERSION && (newVsProp == false || newVsProp == null)) {
          logger.info("New Visual Studio $maj found, but we don't know it yet. Enable them with -Drider.transfer.newVS=true")
          return null
      }
       */

      logger.info("Parsed $hive")

      return VSHive(ver, instId.ifEmpty { null }, rtSuf.ifEmpty { null }).apply {
        logger.assertTrue(hive == this.hiveString, "different hive string")
      }
    }
  }
}