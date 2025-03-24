package com.intellij.diagnostic.specialPaths

import com.intellij.idea.LoggerFactory
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import kotlin.io.path.pathString

internal class ApplicationSpecialPathsProvider : SpecialPathsProvider {
  override fun collectPaths(project: Project?): List<SpecialPathEntry> {
    return listOf(
      SpecialPathEntry("System directory", PathManager.getSystemPath(), SpecialPathEntry.Kind.Folder),
      SpecialPathEntry("Config directory", PathManager.getConfigPath(), SpecialPathEntry.Kind.Folder),
      SpecialPathEntry("Bin directory", PathManager.getBinPath(), SpecialPathEntry.Kind.Folder),
      SpecialPathEntry("Lib directory", PathManager.getLibPath(), SpecialPathEntry.Kind.Folder),
      SpecialPathEntry("Options directory", PathManager.getOptionsPath(), SpecialPathEntry.Kind.Folder),
      SpecialPathEntry("PLUGINS Main directory", PathManager.getPluginsPath(), SpecialPathEntry.Kind.Folder),
      SpecialPathEntry("PLUGINS PreInstalled directory", PathManager.getPreInstalledPluginsPath(), SpecialPathEntry.Kind.Folder),
      SpecialPathEntry("MISC Scratch directory", PathManager.getScratchPath(), SpecialPathEntry.Kind.Folder),
      SpecialPathEntry("MISC Temp directory", PathManager.getTempPath(), SpecialPathEntry.Kind.Folder),
      SpecialPathEntry("LOGS folder", PathManager.getLogPath(), SpecialPathEntry.Kind.Folder),
      SpecialPathEntry("LOGS frontend log", LoggerFactory.getLogFilePath().pathString, SpecialPathEntry.Kind.File),
      SpecialPathEntry("IDE Installation home", PathManager.getHomePath(), SpecialPathEntry.Kind.Folder),
      SpecialPathEntry("IDE Runtime", System.getProperty("java.home"), SpecialPathEntry.Kind.Folder),
    )
  }
}
