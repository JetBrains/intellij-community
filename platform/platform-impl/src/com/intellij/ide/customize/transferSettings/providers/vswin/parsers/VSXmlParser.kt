package com.intellij.ide.customize.transferSettings.providers.vswin.parsers

import com.intellij.ide.customize.transferSettings.db.KnownKeymaps
import com.intellij.ide.customize.transferSettings.models.PatchedKeymap
import com.intellij.ide.customize.transferSettings.models.Settings
import com.intellij.ide.customize.transferSettings.providers.vswin.parsers.data.*
import com.intellij.ide.customize.transferSettings.providers.vswin.utilities.VSHive
import com.intellij.ide.customize.transferSettings.providers.vswin.utilities.Version2
import com.intellij.openapi.diagnostic.logger
import org.jdom.Document
import org.jdom.Element
import org.jdom.input.SAXBuilder
import java.io.File

class VSXmlParserException(message: String) : Exception(message)

class VSXmlParser(settingsFile: File, private val hive: VSHive? = null) {
  companion object {
    const val applicationIdentity = "ApplicationIdentity"
    const val toolsOptions = "ToolsOptions"
    const val category = "Category"
    const val envGroup = "Environment_Group"
    const val nameAttr = "name"
    const val versionAttr = "version"

    private val logger = logger<VSXmlParser>()
  }

  private val document: Document

  val allSettings = Settings(
    keymap = KnownKeymaps.VisualStudio2022
  )
  val ver: Version2

  init {
    require(settingsFile.exists()) { "Settings file was not found" }
    if (hive != null) {
      logger.info("Parsing $hive")
    }
    document = SAXBuilder().build(settingsFile)

    logger.info("Parsing file ${settingsFile.absolutePath}")
    val verStr = document.rootElement.getChild(applicationIdentity)?.getAttribute(versionAttr)?.value
                 ?: throw VSXmlParserException("Can't find version")
    ver = Version2.parse(verStr)
          ?: throw VSXmlParserException("Can't parse version")
    categoryDigger(ver, document.rootElement)
  }

  fun toSettings(): Settings {
    return allSettings
  }

  private fun categoryDigger(version: Version2, rtElement: Element) {
    for (el in rtElement.children) {
      if (el.name == applicationIdentity) continue
      if (el.name == toolsOptions || (el.name == category && el.getAttribute(nameAttr)?.value == envGroup)) {
        categoryDigger(version, el)
        continue
      }

      val disp = parserDispatcher(version, el, hive)?.let { it() }
      if (disp != null) {
        val name = el?.getAttribute(nameAttr)?.value
        if (name == null) {
          logger.info("This should not happen. For some reason there is no name attribute")
          continue
        }

        when (disp) {
          is FontsAndColorsParsedData -> allSettings.laf = disp.theme.toRiderTheme()
          is KeyBindingsParsedData -> allSettings.keymap = if (disp.convertToSettingsFormat().isNotEmpty() && allSettings.keymap != null) PatchedKeymap(allSettings.keymap!!, disp.convertToSettingsFormat(), emptyList()) else allSettings.keymap
        }
      }
    }
  }

  private fun parserDispatcher(version: Version2, el: Element, hive: VSHive?): (() -> VSParsedData?)? {
    //.debug("Processing $value")
    return when (el.getAttribute(nameAttr)?.value) {
      FontsAndColorsParsedData.key -> {
        { VSParsedDataCreator.fontsAndColors(version, el) }
      }
      KeyBindingsParsedData.key -> {
        { VSParsedDataCreator.keyBindings(version, el, hive) }
      }
      //DebuggerParsedData.key -> { { VSParsedDataCreator.debugger(version, el) } }
      //ToolWindowsParsedData.key -> { { VSParsedDataCreator.toolWindows(version, el) } }
      //else -> { logger.debug("Unknown").let { null } }
      else -> null
    }
  }
}