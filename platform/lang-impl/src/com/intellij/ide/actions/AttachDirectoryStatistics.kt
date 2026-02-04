// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.util.ExecUtil
import com.intellij.ide.actions.AttachDirectoryUsagesCollector.SpecialDirectory
import com.intellij.ide.actions.AttachDirectoryUsagesCollector.logAttachedDirectoryFilesCount
import com.intellij.ide.actions.AttachDirectoryUsagesCollector.logAttachedSpecialDirectory
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.util.SystemProperties
import com.intellij.util.system.OS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.math.min

internal object AttachDirectoryUsagesCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("attach.directory.statistics", 1)

  enum class SpecialDirectory {
    HOME {
      override fun matches(root: VirtualFile): Boolean {
        val userHome = SystemProperties.getUserHome()
        if (userHome.isEmpty()) return false
        return try {
          val home = Paths.get(userHome).toRealPath()
          val rootPath = root.toNioPath().toRealPath()
          home == rootPath
        }
        catch (_: Exception) {
          false
        }
      }
    },
    DOWNLOADS {
      private val downloadsPath: Path? by lazy {
        try {
          resolveDownloadsDir()?.toRealPath()
        }
        catch (_: Exception) {
          null
        }
      }

      override fun matches(root: VirtualFile): Boolean {
        val downloads = downloadsPath ?: return false
        return try {
          val rootPath = root.toNioPath().toRealPath()
          downloads == rootPath
        }
        catch (_: Exception) {
          false
        }
      }

      private fun resolveDownloadsDir(): Path? {
        if (OS.isGenericUnix() && PathEnvironmentVariableUtil.isOnPath("xdg-user-dir")) {
          val line = ExecUtil.execAndReadLine(GeneralCommandLine("xdg-user-dir", "DOWNLOAD"))
          if (!line.isNullOrBlank()) {
            return try {
              Paths.get(line)
            }
            catch (_: InvalidPathException) {
              null
            }
          }
        }

        val home = SystemProperties.getUserHome()
        return if (home.isEmpty()) null else Paths.get(home, "Downloads")
      }
    },
    ROOT {
      override fun matches(root: VirtualFile): Boolean {
        return try {
          val p = Paths.get(root.path)
          p.isAbsolute && p.nameCount == 0
        }
        catch (_: Exception) {
          false
        }
      }
    };

    abstract fun matches(root: VirtualFile): Boolean
  }

  private val ATTACHED_DIRECTORY_EVENT = GROUP.registerEvent(
    "attached.directory",
    EventFields.LogarithmicInt("files_count"), EventFields.Int("files_count_limit"), EventFields.Boolean("limit_reached")
  )

  private val ATTACHED_SPECIAL_DIRECTORY_EVENT = GROUP.registerEvent(
    "attached.special.directory",
    EventFields.Enum<SpecialDirectory>("type")
  )

  override fun getGroup() = GROUP

  @JvmStatic
  fun logAttachedDirectoryFilesCount(filesCount: Int) {
    ATTACHED_DIRECTORY_EVENT.log(filesCount, FILES_COUNT_LIMIT, filesCount == FILES_COUNT_LIMIT)
  }

  @JvmStatic
  fun logAttachedSpecialDirectory(type: SpecialDirectory) {
    ATTACHED_SPECIAL_DIRECTORY_EVENT.log(type)
  }
}

@Service(Service.Level.PROJECT)
private class FileCountLogger(private val cs: CoroutineScope) {
  fun logFilesOnDiskCount(root: VirtualFile) {
    when (val specialKind = SpecialDirectory.entries.firstOrNull { it.matches(root) }) {
      null -> {
        cs.launch(Dispatchers.IO) {
          val filesCount = countFilesOnDisk(root)
          logAttachedDirectoryFilesCount(filesCount)
        }
      }
      else -> {
        logAttachedSpecialDirectory(specialKind)
      }
    }
  }

  private fun countFilesOnDisk(root: VirtualFile): Int {
    var count = 0
    VfsUtilCore.iterateChildrenRecursively((root as NewVirtualFile).asCacheAvoiding(), null) {
      ++count < FILES_COUNT_LIMIT
    }
    return min(count, FILES_COUNT_LIMIT)
  }
}

private const val FILES_COUNT_LIMIT: Int = 1_000_000

internal fun logFilesOnDiskCount(project: Project, root: VirtualFile) {
  project.service<FileCountLogger>().logFilesOnDiskCount(root)
}
