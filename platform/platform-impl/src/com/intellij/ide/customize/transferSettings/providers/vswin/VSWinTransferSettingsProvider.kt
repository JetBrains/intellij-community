package com.intellij.ide.customize.transferSettings.providers.vswin

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.customize.transferSettings.models.BaseIdeVersion
import com.intellij.ide.customize.transferSettings.models.FailedIdeVersion
import com.intellij.ide.customize.transferSettings.models.IdeVersion
import com.intellij.ide.customize.transferSettings.providers.DefaultImportPerformer
import com.intellij.ide.customize.transferSettings.providers.ImportPerformer
import com.intellij.ide.customize.transferSettings.providers.TransferSettingsProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.ide.customize.transferSettings.providers.vswin.parsers.VSParser
import com.intellij.ide.customize.transferSettings.providers.vswin.utilities.VSHiveDetourFileNotFoundException
import com.intellij.ide.customize.transferSettings.providers.vswin.utilities.VSPossibleVersionsEnumerator
import com.intellij.ide.customize.transferSettings.providers.vswin.utilities.VSProfileDetectorUtils
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfoRt
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import javax.swing.*

private val logger = logger<VSWinTransferSettingsProvider>()
class VSWinTransferSettingsProvider : TransferSettingsProvider {
  override val name: String = "Visual Studio"

  private val defaultAdvice: @Nls String = IdeBundle.message("transfersettings.vs.quit.advise")
  val failureReason: @Nls String = IdeBundle.message("transfersettings.vs.failureReason", defaultAdvice)
  private val noSettings: @Nls String = IdeBundle.message("transfersettings.vs.noSettings")

  override fun getIdeVersions(skipIds: List<String>): List<BaseIdeVersion> {
    var speedResult = ""

    val badVersions = mutableListOf<FailedIdeVersion>()
    val accessibleVSInstallations = VSPossibleVersionsEnumerator().get().mapNotNull { hive ->
      val start = timeFn()
      logger.info("Started processing ${hive.hiveString}")

      val instanceIdForIdeVersion = "VisualStudio${hive.hiveString}"

      /*customizer.reloadIds?.let {
        if (!it.contains(instanceIdForIdeVersion)) {
          return@mapNotNull null
        }
      }*/

      val customRt = System.getProperty("trl.oneHs")
      if (customRt != null && hive.hiveString != customRt) {
        return@mapNotNull null
      }

      speedResult += "START $instanceIdForIdeVersion ---------------------------\n" // NON-NLS

      val name = if (System.getProperty("trl.transfer.debug")?.toBoolean() == true) {
        "${hive.presentationString.replace("Visual Studio", "VS")} ${hive.hiveString}"
      }
      else {
        hive.presentationString
      }

      val failedIde = FailedIdeVersion(
        id = instanceIdForIdeVersion,
        name = name,
        subName = hive.hiveString,
        icon = AllIcons.Idea_logo_welcome,

        stepsToFix = defaultAdvice,
        canBeRetried = true,
        potentialReason = failureReason
      )

      val registryTime = timeFn()
      val registry = try {
        hive.registry
      }
      catch (t: VSHiveDetourFileNotFoundException) {
        logger.info("File not found. Probably vs was uninstalled")

        return@mapNotNull null
      }

      val res2 = convertTimeFn(timeFn() - registryTime)
      speedResult += "registryTime $res2\n" // NON-NLS

      if (registry == null) {
        logger.warn("Critical. Failed to init registry")
        badVersions.add(failedIde)

        return@mapNotNull null
      }

      if (!hive.isInstalled) {
        logger.info("This instance of Visual Studio was uninstalled")

        return@mapNotNull null
      }

      val subNameTime = timeFn()
      val subName = StringBuilder().apply {
        try {
          append(hive.edition ?: "")
          append(if (hive.isolation?.isPreview == true) " Preview" else "")
        }
        catch (_: Throwable) {
        }
        VSProfileDetectorUtils.rootSuffixStabilizer(hive).let {
          if (it != null) append(" ($it)")
        }
      }.let { if (it.isEmpty()) null else it.toString().trimStart() }

      val res1 = convertTimeFn(timeFn() - subNameTime)
      speedResult += "subname $res1\n" // NON-NLS

      val readSettingsTime = timeFn()
      try {
        requireNotNull(registry.settingsFile)
      }
      catch (t: Throwable) {
        logger.warn("Critical. Failed to read file")
        logger.warn(t)
        badVersions.add(failedIde.apply {
          this.stepsToFix = noSettings
        })

        return@mapNotNull null
      }

      val res3 = convertTimeFn(timeFn() - readSettingsTime)
      speedResult += "readSettingsFile $res3\n" // NON-NLS

      val settings by lazy { VSParser(hive).settings }

      // Finally, IdeVersion
      val l = IdeVersion(
        id = instanceIdForIdeVersion,
        name = name,
        subName = subName,
        icon = AllIcons.Idea_logo_welcome,

        lastUsed = hive.lastUsage,
        settings = settings,

        provider = this
      )

      val res4 = convertTimeFn(timeFn() - start)
      speedResult += "${hive.hiveString} $res4\n\n"
      l
    }

    if (System.getProperty("trl.transfer.ReSharperGranular").toBoolean()) {
      SwingUtilities.invokeLater {
        Messages.showInfoMessage(speedResult, "Speedrun") // NON-NLS
      }
    }

    return accessibleVSInstallations + badVersions
  }

  override fun isAvailable(): Boolean = SystemInfoRt.isWindows

  override fun getImportPerformer(ideVersion: IdeVersion): DefaultImportPerformer = DefaultImportPerformer()

  private fun timeFn() = System.nanoTime()
  private fun convertTimeFn(time: Long): Long = time / 1_000_000

}