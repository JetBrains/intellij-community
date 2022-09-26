package com.intellij.ide.customize.transferSettings.providers.vswin.parsers

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.ide.RecentProjectMetaInfo
import com.intellij.ide.customize.transferSettings.db.WindowsEnvVariables
import com.intellij.ide.customize.transferSettings.models.FeatureInfo
import com.intellij.ide.customize.transferSettings.models.ILookAndFeel
import com.intellij.ide.customize.transferSettings.models.RecentPathInfo
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.util.io.exists
import com.jetbrains.rd.util.lifetime.Lifetime
import com.intellij.ide.customize.transferSettings.providers.vswin.mappings.FontsAndColorsMappings
import com.intellij.ide.customize.transferSettings.providers.vswin.mappings.PluginsMapping
import com.intellij.ide.customize.transferSettings.providers.vswin.utilities.VSHive
import com.intellij.ide.customize.transferSettings.providers.vswin.utilities.VSHiveDetourFileNotFoundException
import com.intellij.ide.customize.transferSettings.providers.vswin.utilities.VSProfileDetectorUtils
import com.intellij.ide.customize.transferSettings.providers.vswin.utilities.VSProfileSettingsFileNotFound
import com.intellij.openapi.diagnostic.logger
import com.intellij.ide.customize.transferSettings.providers.vswin.utilities.registryUtils.impl.PrivateRegistryRoot
import com.intellij.ide.customize.transferSettings.providers.vswin.utilities.registryUtils.impl.RegistryRoot
import com.sun.jna.platform.win32.WinReg
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.io.path.Path

class VSRegistryParserNew private constructor(val hive: VSHive) {
  companion object {
    private val veryBadLifetime = Lifetime.Eternal // todo: implement disposable in transfer settings somewhere
    private val logger = logger<VSRegistryParserNew>()

    fun create(hive: VSHive): VSRegistryParserNew? {
      return try {
        VSRegistryParserNew(hive)
      }
      catch (t: VSHiveDetourFileNotFoundException) {
        throw t
      }
      catch (t: Throwable) {
        logger.warn("Failed to initialize registry")
        logger.warn(t)
        null
      }
    }
  }

  private val detourFile: File? = if (isRegistryDetourRequired()) detourFileInit() else null

  val envPath by lazy { envPathInit() }
  val settingsFile by lazy { settingsFileInit() }
  val vsLocation by lazy { vsLocationInit() }
  val recentProjects by lazy { recentProjectsInit() ?: mutableListOf() }
  val extensions by lazy { extensionsListInit() }
  val theme by lazy { themeInit() }

  private val registryRootKey = if (isRegistryDetourRequired()) {
    requireNotNull(detourFile)
    PrivateRegistryRoot.getOrCreate(detourFile, veryBadLifetime)
  }
  else {
    RegistryRoot(WinReg.HKEY_CURRENT_USER, veryBadLifetime)
  }.fromKey("SOFTWARE\\Microsoft\\VisualStudio\\${hive.hiveString}")
  private val registryRootKeyConfig = registryRootKey.withSuffix("_Config")

  private fun isRegistryDetourRequired(): Boolean {
    return hive.instanceId != null
  }

  private fun themeInit(): ILookAndFeel? {
    val regValue = try {
      registryRootKey.inChild("General").getStringValue("CurrentTheme")?.uppercase()
    }
    catch (t: Throwable) {
      return null
    }

    if (regValue == null) {
      return FontsAndColorsMappings.VsTheme.Dark.toRiderTheme()
    }

    return FontsAndColorsMappings.VsTheme.fromString(regValue).toRiderTheme()
  }

  private fun extensionsListInit(): MutableList<FeatureInfo>? {
    val packagesKey = registryRootKeyConfig.inChild("Packages")
    val preParsed = try {

      packagesKey.getKeys()?.mapNotNull {
        val a = packagesKey.inChild(it).getValues()

        val compName = a?.get("CompanyName")
        val productName = a?.get("ProductName") ?: return@mapNotNull null
        "$compName|$productName"
      }
    }
    catch (t: Throwable) {
      logger.warn("error in new method")
      logger.warn(t)
      return null
    }

    logger.info("Installed plugins in ${hive.hiveString}: ${preParsed?.joinToString { "$it, " }}")

    return preParsed?.mapNotNull { PluginsMapping.get(it) }?.toMutableList()
  }

  fun colorSchemesInit() {
    // TODO: Finish this prototype
    /*val thePath = registryRootKey / "ApplicationPrivateSettings" / "Microsoft" / "VisualStudio"
    val rawUuid = thePath.getStringValue("ColorThemeNew")
    if (rawUuid == null) return

    val uuidStart = rawUuid.indexOf('{')
    val uuid = rawUuid.subSequence(uuidStart until rawUuid.length)
    println()

    val rak2 = registryRootKey.
    val colorKeys = Advapi32Util.registryGetKeys(rak2.first, "${rak2.second}\\Themes\\$uuid")
    val a = ByteArrayOutputStream()
    colorKeys.map {
        Advapi32Util.registryGetBinaryValue(rak2.first, "${rak2.second}\\Themes\\$uuid\\$it", "Data")
    }.forEach {
        a.write(it)
        a.write(0);a.write(0);a.write(0);a.write(0);a.write(0);a.write(0);a.write(0);a.write(0);a.write(0);a.write(0);
    }
    a.toByteArray()
    Files.write(File("C:\\file.bin").toPath(), a.toByteArray())
     */
  }

  private fun envPathInit(): Pair<Path?, File?> {
    var envDir: Path? = null
    var devEnv: File? = null

    val setupVSkey = registryRootKeyConfig / "Setup" / "VS"

    val fileDirStr = try {
      setupVSkey.getStringValue("EnvironmentDirectory")
    }
    catch (t: Throwable) {
      logger.warn("Failed to obtain path to EnvDir (probably vs is corrupted)")
      logger.debug(t)
      return Pair(null, null)
    }
    if (fileDirStr == null) {
      logger.warn("EnvDir is null")
    }

    val filePathStr = try {
      setupVSkey.getStringValue("EnvironmentPath")
    }
    catch (t: Throwable) {
      logger.warn("Failed to obtain path to EnvPath (probably vs is corrupted)")
      logger.debug(t)
      return Pair(null, null)
    }
    if (filePathStr == null) {
      logger.warn("EnvPath is null")
    }

    // file access

    if (fileDirStr != null) {
      envDir = Path(fileDirStr)
      if (!Files.exists(envDir)) {
        logger.info("envDir was not found in fs")
        envDir = null
      }
    }

    if (filePathStr != null) {
      devEnv = File(filePathStr)
    }

    return Pair(envDir, devEnv)
  }

  private fun settingsFileInit(): File? {

    val unexpandedPath = try {
      registryRootKey.inChild("Profile").getStringValue("AutoSaveFile")
    }
                         catch (t: com.sun.jna.platform.win32.Win32Exception) {
                           throw VSProfileSettingsFileNotFound(
                             "A problem occurred while trying to work with registry. Probably key does not exist.")
                         } ?: throw Exception("Unknown registry error")
    val path = VSProfileDetectorUtils.expandPath(unexpandedPath, hive)

    return path?.let { File(it) }
  }

  private fun vsLocationInit(): String? {
    return registryRootKey.getStringValue("VisualStudioLocation")
  }

  private fun recentProjectsNewVSInit(): MutableList<RecentPathInfo>? {
    val regInfo = try {
      (registryRootKey / "ApplicationPrivateSettings" / "_metadata" / "baselines" / "CodeContainers").getStringValue("Offline")
    }
                  catch (t2: Throwable) {
                    logger.info("Super new method of gettings projects failed")
                    logger.debug(t2)
                    null
                  } ?: return null

    val regInfo2 = if (!regInfo.startsWith('{')) regInfo.drop(1) else regInfo

    val root = try {
      ObjectMapper(JsonFactory().enable(JsonParser.Feature.ALLOW_COMMENTS)).readTree(regInfo2)
    }
    catch (t: Throwable) {
      logger.warn(t)
      return null
    }

    val preItems = root.mapNotNull {
      if (!it["Value"]["IsLocal"].asBoolean()) return@mapNotNull null

      val date = it["Value"]["LastAccessed"].asText()
      val path = it["Value"]["LocalProperties"]["FullPath"].asText()
      val pathExpanded = VSProfileDetectorUtils.expandPath(path, hive)

      if (pathExpanded == null || !Path(pathExpanded).exists()) return@mapNotNull null

      val dateParser = DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(date).run {
        Instant.from(this)
      }
      val dateConverted = Date.from(dateParser)

      Pair(pathExpanded, dateConverted)
    }.sortedByDescending { it.second }

    return preItems.map {
      val pathExp = File(VSProfileDetectorUtils.expandPath(it.first, hive)!!)
      val info = RecentProjectMetaInfo().apply {
        metadata = null
        if (pathExp.isDirectory) {
          metadata = "folder|"
        }
        projectOpenTimestamp = it.second.time // / 1000L
        buildTimestamp = it.second.time
      }

      RecentPathInfo(pathExp.systemIndependentPath, info)
    }.toMutableList()
  }

  private fun recentProjectsInit(): MutableList<RecentPathInfo>? {
    val newMethod = recentProjectsNewVSInit()
    if (newMethod != null/* && newMethod.isNotEmpty()*/) {
      return newMethod
    }
    else {
      logger.info("New proj detection method failed")
    }

    val registry = try {
      try {
        (registryRootKey / "MRUItems" / "{a9c4a31f-f9cb-47a9-abc0-49ce82d0b3ac}" / "Items").getValues()
      }
      catch (t2: Throwable) {
        logger.info("Failed to get recent projects using new method, trying old one")
        logger.debug(t2)
        (registryRootKey / "ProjectMRUList").getValues()
      }
    }
    catch (t: Throwable) {
      logger.warn("Sorry, no recent projects for you")
      logger.debug(t)
      return null
    }

    if (registry == null) {
      logger.info("No recent projects found (no registry keys)")
      return null
    }

    return registry.mapNotNull { (_, item) ->
      if (item !is String) {
        logger.warn("Got not strings for keys for some unknown reason")
        return@mapNotNull null
      }
      val spl = item.split('|', ';')

      val path = spl[0]
      val name = spl[3]

      if (path.isEmpty()) {
        logger.warn("path is empty")
        return@mapNotNull null
      }

      var i = System.currentTimeMillis()

      val pathExp = File(VSProfileDetectorUtils.expandPath(path, hive)!!)
      val info = RecentProjectMetaInfo().apply {
        i -= 1000
        metadata = null
        if (pathExp.isDirectory) {
          metadata = "folder|"
        }
        projectOpenTimestamp = i
        buildTimestamp = i
      }

      RecentPathInfo(pathExp.systemIndependentPath, info)
    }.toMutableList()
  }

  private fun detourFileInit(): File {
    check(isRegistryDetourRequired()) { "Calling getDetourFile for old VS" }

    val pathToFolder = "${WindowsEnvVariables.localApplicationData}\\Microsoft\\VisualStudio\\${hive.hiveString}"

    val file = File("$pathToFolder\\privateregistry.bin")

    if (!file.exists()) {
      logger.warn("detour file is not found. did you delete it or its not vs<=17?")
      throw VSHiveDetourFileNotFoundException()
    }

    return file
  }
}