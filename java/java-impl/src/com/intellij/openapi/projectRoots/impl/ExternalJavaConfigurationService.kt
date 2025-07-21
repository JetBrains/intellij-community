// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.execution.wsl.WslPath
import com.intellij.java.JavaBundle
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.projectRoots.JdkFinder
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val LOG = logger<ExternalJavaConfigurationService>()

/**
 * The JDK needed for a project can be described in a configuration file written by an external tool.
 * This service is used to find a matching JDK candidate for a given release data.
 *
 * @see ExternalJavaConfigurationProvider
 */
@Service(Service.Level.PROJECT)
public class ExternalJavaConfigurationService(public val project: Project, private val scope: CoroutineScope) : Disposable {

  public sealed class JdkCandidate<T> {
    public data class Jdk<T>(val releaseData: T, val jdk: Sdk, val project: Boolean) : JdkCandidate<T>()
    public data class Path<T>(val releaseData: T, val path: String) : JdkCandidate<T>()
  }

  internal fun <T> registerListener(disposable: Disposable, configProvider: ExternalJavaConfigurationProvider<T>) {
    project.messageBus.connect(disposable).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: MutableList<out VFileEvent>) {
        //TODO: getConfigurationFile(project) create a File with a fixed name, only to extract this name back here
        //      much better would be to provide .getConfigurationFileName() method, OR just cache the file created
        //      inside getConfigurationFile()
        val configFileName = configProvider.getConfigurationFile(project).name
        for (event in events) {
          if (!event.path.endsWith(configFileName)) continue
          if (event !is VFileContentChangeEvent && event !is VFileCreateEvent) continue

          updateJdkFromConfig(configProvider)
        }
      }
    })
  }

  /**
   * Updates the project JDK according to the configuration file of [configProvider].
   */
  public fun <T> updateJdkFromConfig(configProvider: ExternalJavaConfigurationProvider<T>) {
    scope.launch {
      val releaseData: T = getReleaseData(configProvider) ?: return@launch
      val file = configProvider.getConfigurationFile(project)
      val suggestion = findCandidate(releaseData, configProvider)

      when (suggestion) {
        is JdkCandidate.Jdk -> if (!suggestion.project) configure(suggestion.jdk, file.name, releaseData)
        is JdkCandidate.Path -> service<AddJdkService>().createJdkFromPath(suggestion.path) {
          configure(it, file.name, releaseData)
        }
        else -> null
      }
    }
  }

  /**
   * @return a JDK candidate based on the configuration file.
   */
  public suspend fun <T> getReleaseData(configProvider: ExternalJavaConfigurationProvider<T>): T? {
    val text = readAction {
      val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(configProvider.getConfigurationFile(project).toPath().toAbsolutePath())
      virtualFile?.let { FileDocumentManager.getInstance().getDocument(it)?.text }
    } ?: return null

    return configProvider.getReleaseData(text)
  }

  /**
   * @return a matching JDK candidate for the release data among registered and detected JDKs.
   */
  public fun <T> findCandidate(releaseData: T, configProvider: ExternalJavaConfigurationProvider<T>): JdkCandidate<T>? {
    val fileName = configProvider.getConfigurationFile(project).name

    val wsl =  SystemInfo.isWindows && project.guessProjectDir()?.let { WslPath.isWslUncPath(it.path) } == true

    // Match against the project SDK
    val projectSdk = ProjectRootManager.getInstance(project).projectSdk
    if (projectSdk != null && configProvider.matchAgainstSdk(releaseData, projectSdk)) {
      return JdkCandidate.Jdk(releaseData, projectSdk, true)
    } else {
      LOG.info("[$fileName] $releaseData - Project JDK doesn't match (${projectSdk?.versionString})")
    }

    // Match against the project JDK table
    val jdks = ProjectJdkTable.getInstance().allJdks
    for (jdk in jdks) {
      val path = jdk.homePath ?: continue
      if (SystemInfo.isWindows && wsl != WslPath.isWslUncPath(path)) continue
      if (configProvider.matchAgainstSdk(releaseData, jdk)) {
        LOG.info("[$fileName] $releaseData - Candidate found: ${jdk.versionString}")
        return JdkCandidate.Jdk(releaseData, jdk, false)
      }
    }

    // Match against JdkFinder
    JdkFinder.getInstance().suggestHomePaths(project).forEach { path ->
      if (configProvider.matchAgainstPath(releaseData, path) && (!SystemInfo.isWindows || wsl == WslPath.isWslUncPath(path))) {
        LOG.info("[$fileName] $releaseData - Candidate found to register")
        return JdkCandidate.Path(releaseData, path)
      }
    }

    return null
  }

  private fun <T> configure(jdk: Sdk, fileName: String, candidate: T) {
    scope.launch(Dispatchers.EDT) {
      val rootManager = ProjectRootManager.getInstance(project)
      edtWriteAction { rootManager.projectSdk = jdk }
      LOG.info("[$fileName] $candidate - JDK registered: ${jdk.versionString}")

      NotificationGroupManager.getInstance()
        .getNotificationGroup("Setup JDK")
        .createNotification(
          JavaBundle.message("sdk.configured.external.config.title", fileName),
          JavaBundle.message("sdk.configured", jdk.versionString),
          NotificationType.INFORMATION
        )
        .notify(project)
    }
  }

  override fun dispose() {}
}