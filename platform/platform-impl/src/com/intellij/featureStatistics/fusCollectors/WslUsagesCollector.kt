// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics.fusCollectors

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.execution.wsl.WslIjentAvailabilityService
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.*
import kotlin.io.path.invariantSeparatorsPathString

@ApiStatus.Internal
object WslUsagesCollector : CounterUsagesCollector() {
  private val WSL_USAGES_GROUP = EventLogGroup("wsl.usages", 1)

  private val DISTRO_TYPE_FIELD = EventFields.Enum<DistroType>("distribution_type")
  private val WSL_VERSION_FIELD = EventFields.Int("wsl_version")
  private val EEL_API_USED_FIELD = EventFields.Boolean("is_eel_api_used")
  private val VCS_TYPE_FIELD = EventFields.Enum<VcsType>("vcs_type")

  private val PROJECT_CREATED_EVENT = WSL_USAGES_GROUP.registerEvent("project.created.in.wsl",
                                                                     DISTRO_TYPE_FIELD,
                                                                     WSL_VERSION_FIELD,
                                                                     EEL_API_USED_FIELD)

  private val PROJECT_OPENED_EVENT = WSL_USAGES_GROUP.registerEvent("project.opened.in.wsl",
                                                                    DISTRO_TYPE_FIELD,
                                                                    WSL_VERSION_FIELD,
                                                                    EEL_API_USED_FIELD)

  private val PROJECT_CLONED_EVENT = WSL_USAGES_GROUP.registerVarargEvent("project.cloned.in.wsl",
                                                                          DISTRO_TYPE_FIELD,
                                                                          WSL_VERSION_FIELD,
                                                                          EEL_API_USED_FIELD,
                                                                          VCS_TYPE_FIELD)

  private val WSL_PATH_PREFIXES = listOf("//wsl$/", "//wsl.localhost/")
  private val newProjectData = Collections.synchronizedMap(mutableMapOf<String, ProjectData>())

  enum class VcsType { None, Git, Svn, Hg, Other }

  enum class DistroType {
    Ubuntu,
    Centos,
    Debian,
    Fedora,
    Kali,
    Suse,
    Oracle,
    Other;

    companion object {
      fun fromName(distroName: String): DistroType =
        when {
          distroName.contains("Ubuntu", true) -> Ubuntu
          distroName.contains("Debian", true) -> Debian
          distroName.contains("Kali", true) -> Kali
          distroName.contains("SUSE", true) -> Suse
          distroName.contains("Oracle", true) -> Oracle
          distroName.contains("Fedora", true) -> Fedora
          distroName.contains("Centos", true) -> Centos
          else -> Other
        }
    }
  }

  private data class ProjectData(val vcsType: VcsType)

  override fun getGroup(): EventLogGroup? = WSL_USAGES_GROUP

  fun beforeProjectCreated(path: Path, task: CloneableProjectsService.CloneTask? = null) {
    if (!SystemInfo.isWindows) return
    path.invariantSeparatorsPathString.takeIf { it.isWslPath() }?.let {
      newProjectData.put(it, ProjectData(getVcsType(task)))
    }
  }

  private fun getVcsType(task: CloneableProjectsService.CloneTask? = null): VcsType =
    task?.let {
      val className = it.javaClass.name
      when {
        className.contains("SvnCheckout") -> VcsType.Svn
        className.contains("GitCheckout") -> VcsType.Git
        className.contains("HgCheckout") -> VcsType.Hg
        else -> VcsType.Other
      }
    } ?: VcsType.None

  private fun String.isWslPath() = WSL_PATH_PREFIXES.any { this.startsWith(it) }

  suspend fun logProjectOpened(project: Project) {
    if (!SystemInfo.isWindows) return
    val projectPath = project.basePath
    if (projectPath?.isWslPath() == true) {
      findWslDistro(projectPath)?.let { wslDistro ->
        val distroType = DistroType.fromName(wslDistro.presentableName)
        val isEelUsed = WslIjentAvailabilityService.getInstance().runWslCommandsViaIjent()
        if (newProjectData.contains(projectPath)) {
          val projectData = newProjectData[projectPath]
          newProjectData.remove(projectPath)
          val vcsType = projectData?.vcsType ?: VcsType.None
          if (vcsType == VcsType.None) {
            PROJECT_CREATED_EVENT.log(distroType, wslDistro.version, isEelUsed)
          }
          else {
            PROJECT_CLONED_EVENT.log(
              DISTRO_TYPE_FIELD.with(distroType),
              WSL_VERSION_FIELD.with(wslDistro.version),
              EEL_API_USED_FIELD.with(isEelUsed),
              VCS_TYPE_FIELD.with(vcsType))
          }
        }
        else {
          PROJECT_OPENED_EVENT.log(distroType, wslDistro.version, isEelUsed)
        }
      }
    }
  }

  private suspend fun findWslDistro(projectPathStr: String): WSLDistribution? =
    withContext(Dispatchers.IO) {
      WslDistributionManager.getInstance().installedDistributions.firstOrNull {
        val distroPath = Path.of(it.getUNCRootPath().toUri())
        Path.of(projectPathStr).startsWith(distroPath)
      }
    }
}

