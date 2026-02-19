// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.util.ExecUtil
import com.intellij.ide.actions.AttachDirectoryUsagesCollector.ATTACH_DIRECTORY_ACTIVITY
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.ide.actions.AttachDirectoryUsagesCollector.SpecialDirectory
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
  private val GROUP = EventLogGroup("attach.directory.statistics", 2)

  private val FILES_COUNT_FIELD = EventFields.LogarithmicInt("files_count_log_scale")
  private val FILES_COUNT_LIMIT_FIELD = EventFields.Int("files_count_limit")
  private val LIMIT_REACHED_FIELD = EventFields.Boolean("limit_reached")

  internal val ATTACH_DIRECTORY_ACTIVITY = GROUP.registerIdeActivity(
    "attach.directory.count.files.activity",
    startEventAdditionalFields = emptyArray(),
    finishEventAdditionalFields = arrayOf(FILES_COUNT_FIELD, FILES_COUNT_LIMIT_FIELD, LIMIT_REACHED_FIELD)
  )

  internal fun finishFilesTraversal(activity: StructuredIdeActivity, filesCount: Int) {
    activity.finished {
      listOf(
        FILES_COUNT_FIELD.with(filesCount),
        FILES_COUNT_LIMIT_FIELD.with(FILES_COUNT_LIMIT),
        LIMIT_REACHED_FIELD.with(filesCount == FILES_COUNT_LIMIT)
      )
    }
  }

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

  private val ATTACHED_SPECIAL_DIRECTORY_EVENT = GROUP.registerEvent(
    "attached.special.directory",
    EventFields.Enum<SpecialDirectory>("type")
  )

  @JvmStatic
  fun logAttachedSpecialDirectory(type: SpecialDirectory) {
    ATTACHED_SPECIAL_DIRECTORY_EVENT.log(type)
  }

  override fun getGroup() = GROUP
}

@Service(Service.Level.PROJECT)
private class FileCountLogger(private val p: Project, private val cs: CoroutineScope) {
  fun logFilesOnDiskCount(root: VirtualFile) {
    when (val specialKind = SpecialDirectory.entries.firstOrNull { it.matches(root) }) {
      null -> {
        cs.launch(Dispatchers.IO) {
          val activity = ATTACH_DIRECTORY_ACTIVITY.started(p)
          try {
            val filesCount = countFilesOnDisk(root)
            AttachDirectoryUsagesCollector.finishFilesTraversal(activity, filesCount)
          }
          finally {
            if (!activity.isFinished()) activity.finished()
          }
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
