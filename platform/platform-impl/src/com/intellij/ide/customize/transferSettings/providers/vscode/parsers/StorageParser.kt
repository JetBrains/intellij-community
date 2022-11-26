package com.intellij.ide.customize.transferSettings.providers.vscode.parsers

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.ide.RecentProjectMetaInfo
import com.intellij.ide.customize.transferSettings.db.KnownColorSchemes
import com.intellij.ide.customize.transferSettings.db.KnownLafs
import com.intellij.ide.customize.transferSettings.models.RecentPathInfo
import com.intellij.ide.customize.transferSettings.models.Settings
import com.intellij.ide.customize.transferSettings.providers.vscode.mappings.ThemesMappings
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import com.jetbrains.rd.util.URI
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class StorageParser(private val settings: Settings) {
  private val logger = logger<StorageParser>()

  companion object {
    private const val OPENED_PATHS = "openedPathsList"
    private const val WORKSPACES = "workspaces"
    private const val WORKSPACES_2 = "${WORKSPACES}2"
    private const val WORKSPACES_3 = "${WORKSPACES}3"
    private const val THEME = "theme"

    internal fun parsePath(uri: String): RecentPathInfo? {
      val path = Path.of(URI(uri)) ?: return null
      val modifiedTime = path.toFile().listFiles()?.maxByOrNull { it.lastModified() }?.lastModified()

      val info = RecentProjectMetaInfo().apply {
        projectOpenTimestamp = modifiedTime ?: 0
        buildTimestamp = projectOpenTimestamp
        displayName = path.fileName.toString()
      }

      return RecentPathInfo(workaroundWindowsIssue(path.absolutePathString()), info)
    }

    /**
     * Workaround until IDEA-270493 is fixed
     */
    private fun workaroundWindowsIssue(input: String): String {
      if (!SystemInfo.isWindows) return input
      if (input.length < 3) return input
      if (input[1] != ':') return input

      return "${input[0].uppercase()}${input.subSequence(1, input.length)}"
    }
  }

  fun process(file: File) = try {
    logger.info("Processing a storage file: $file")

    val root = ObjectMapper(JsonFactory().enable(JsonParser.Feature.ALLOW_COMMENTS)).readTree(file) as? ObjectNode
               ?: error("Unexpected JSON data; expected: ${JsonNodeType.OBJECT}")

    processRecentProjects(root)
    processThemeAndScheme(root)
  }
  catch (t: Throwable) {
    logger.warn(t)
  }

  private fun processRecentProjects(root: ObjectNode) {
    try {
      val openedPaths = root[OPENED_PATHS] as? ObjectNode ?: return
      val flatList = openedPaths.toList().flatMap { (it as ArrayNode).toList() }
      val workspacesNew = try {
        flatList.mapNotNull { it["folderUri"] }.mapNotNull { it.textValue() }
      }
      catch (t: Throwable) {
        null
      }
      val workspacesOld = try {
        flatList.mapNotNull { it.textValue() ?: return@mapNotNull null }
      }
      catch (t: Throwable) {
        null
      }

      val workspaces = if (!workspacesNew.isNullOrEmpty()) workspacesNew else workspacesOld ?: return

      workspaces.forEach { uri ->
        try {
          val res = parsePath(uri)
          if (res != null) {
            settings.recentProjects.add(res)
          }
        }
        catch (t: Throwable) {
          logger.warn(t)
        }
      }
    }
    catch (t: Throwable) {
      logger.warn(t)
    }
  }


  private fun processThemeAndScheme(root: ObjectNode) {
    try {
      val theme = root[THEME]?.textValue() ?: return
      val laf = ThemesMappings.themeMap(theme)

      settings.laf = laf

      settings.syntaxScheme = when (laf) {
        KnownLafs.Light -> KnownColorSchemes.Light
        else -> KnownColorSchemes.Darcula
      }
    }
    catch (t: Throwable) {
      logger.warn(t)
    }
  }
}