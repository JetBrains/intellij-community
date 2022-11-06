package com.intellij.ide.customize.transferSettings.providers.vsmac.parsers

import com.intellij.ide.RecentProjectMetaInfo
import com.intellij.ide.customize.transferSettings.models.RecentPathInfo
import com.intellij.ide.customize.transferSettings.models.Settings
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.io.isDirectory
import com.intellij.util.io.systemIndependentPath
import com.jetbrains.rd.util.URI
import org.jdom.Element
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

private val logger = logger<RecentProjectsParser>()
class RecentProjectsParser(private val settings: Settings) {
  companion object {
    private const val MIME_TYPE = "Mime-Type"
    private const val SLN_TYPE = "application/x-sln"
    private const val URI_FIELD = "URI"
  }

  fun process(file: File) = try {
    logger.info("Processing a file: $file")

    val root = JDOMUtil.load(file)

    processRecentProjects(root)
  }
  catch (t: Throwable) {
    logger.warn(t)
  }

  private fun processRecentProjects(root: Element) {
    var i = System.currentTimeMillis()
    root.children.forEach { recentItem ->
      try {
        val mimeType = recentItem.getChild(MIME_TYPE)?.value ?: return@forEach
        if (mimeType != SLN_TYPE) {
          return@forEach
        }

        val uri = recentItem.getChild(URI_FIELD)?.value ?: return@forEach
        val path = Path.of(URI(uri)) ?: return@forEach

        i -= 1000
        val rpmi = RecentProjectMetaInfo().apply {
          displayName = path.fileName.toString()
          projectOpenTimestamp = i
          buildTimestamp = i
          if (path.isDirectory()) {
            metadata = "folder|"
          }
        }

        settings.recentProjects.add(RecentPathInfo(path.systemIndependentPath, rpmi))
      }
      catch (t: Throwable) {
        logger.warn(t)
      }
    }
  }
}