// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.codeVision.CodeVisionHost.LensInvalidateSignal
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JdkFinder
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.eel.provider.getEelMachine
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.file.Path

private val LOG = logger<ExternalJavaConfigurationService>()

/**
 * The JDK needed for a project can be described in a configuration file written by an external tool.
 * This service is used to find a matching JDK candidate for a given release data.
 *
 * @see ExternalJavaConfigurationProvider
 */
@Service(Service.Level.PROJECT)
public class ExternalJavaConfigurationService(public val project: Project, private val scope: CoroutineScope) : Disposable {

  internal sealed class JavaConfigurationStatus {
    object Unknown : JavaConfigurationStatus()
    object AlreadyConfigured : JavaConfigurationStatus()
    object Found : JavaConfigurationStatus()
    data class Missing<T>(val releaseData: T) : JavaConfigurationStatus()
  }

  internal val statuses = HashMap<Path, JavaConfigurationStatus>().withDefault { JavaConfigurationStatus.Unknown }

  public sealed class JdkCandidate<T> {
    public data class Jdk<T>(val releaseData: T, val jdk: Sdk, val project: Boolean) : JdkCandidate<T>()
    public data class Path<T>(val releaseData: T, val path: String) : JdkCandidate<T>()
  }

  /**
   * Searches for a matching JDK candidate if a Java configuration is defined in the config file.
   * If [configureJdk] is true, the project JDK will be updated if a candidate is found.
   */
  public fun <T> updateFromConfig(configProvider: ExternalJavaConfigurationProvider<T>, configureJdk: Boolean = false) {
    scope.launch {
      val releaseData: T = getReleaseData(configProvider) ?: return@launch
      val configPath = configProvider.getConfigurationFilePath(project)

      when (val candidate = findCandidate(releaseData, configProvider)) {
        is JdkCandidate.Jdk -> {
          if (candidate.project) {
            setStatus(configPath, JavaConfigurationStatus.AlreadyConfigured)
          }
          else {
            setStatus(configPath, JavaConfigurationStatus.Found)
            if (configureJdk) configure(candidate.jdk, configPath, releaseData)
          }
        }
        is JdkCandidate.Path -> {
          setStatus(configPath, JavaConfigurationStatus.Found)
          if (configureJdk) {
            service<AddJdkService>().createJdkFromPath(candidate.path) {
              configure(it, configPath, releaseData)
            }
          }
        }
        else -> {
          setStatus(configPath, JavaConfigurationStatus.Missing(releaseData))
        }
      }
    }
  }

  /**
   * @return a JDK candidate based on the configuration file.
   */
  public suspend fun <T> getReleaseData(configProvider: ExternalJavaConfigurationProvider<T>): T? {
    val text = readAction {
      val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(configProvider.getConfigurationFilePath(project).toAbsolutePath())
      virtualFile?.let { FileDocumentManager.getInstance().getDocument(it)?.text }
    } ?: return null

    return configProvider.getReleaseData(text)
  }

  /**
   * @return a matching JDK candidate for the release data among registered and detected JDKs.
   */
  public fun <T> findCandidate(releaseData: T, configProvider: ExternalJavaConfigurationProvider<T>): JdkCandidate<T>? {
    val configFile = configProvider.getConfigurationFilePath(project)
    val eelMachine = project.getEelMachine()

    // Match against the project SDK
    val projectSdk = ProjectRootManager.getInstance(project).projectSdk
    if (projectSdk != null && configProvider.matchAgainstSdk(releaseData, projectSdk)) {
      return JdkCandidate.Jdk(releaseData, projectSdk, true)
    } else {
      LOG.info("[${configFile.fileName}] $releaseData - Project JDK doesn't match (${projectSdk?.versionString})")
    }

    // Match against the project JDK table
    val jdks = ProjectJdkTable.getInstance(project).allJdks
    for (jdk in jdks) {
      if (!ProjectSdksModel.sdkMatchesEel(eelMachine, jdk)) continue
      if (configProvider.matchAgainstSdk(releaseData, jdk)) {
        LOG.info("[$configFile.fileName] $releaseData - Candidate found: ${jdk.versionString}")
        return JdkCandidate.Jdk(releaseData, jdk, false)
      }
    }

    // Match against JdkFinder
    JdkFinder.getInstance().suggestHomePaths(project).forEach { path ->
      if (configProvider.matchAgainstPath(releaseData, path) && ProjectSdksModel.sdkMatchesEel(eelMachine, path)) {
        LOG.info("[$configFile.fileName] $releaseData - Candidate found to register")
        return JdkCandidate.Path(releaseData, path)
      }
    }

    return null
  }

  private fun <T> configure(jdk: Sdk, filePath: Path, candidate: T) {
    scope.launch(Dispatchers.EDT) {
      val rootManager = ProjectRootManager.getInstance(project)
      edtWriteAction { rootManager.projectSdk = jdk }
      LOG.info("[${filePath.fileName}] $candidate - JDK registered: ${jdk.versionString}")

      // Update status and refresh inlays after configuration
      setStatus(filePath, JavaConfigurationStatus.AlreadyConfigured)
    }
  }

  private fun setStatus(filePath: Path, newStatus: JavaConfigurationStatus) {
    if (statuses[filePath] == newStatus) return
    statuses[filePath] = newStatus
    runInEdt {
      project.service<CodeVisionHost>().invalidateProvider(
        LensInvalidateSignal(null, listOf(ExternalJavaConfigurationCodeVision.ID))
      )
    }
  }

  override fun dispose() {}

  public fun addExtensionPointListener() {
    ExternalJavaConfigurationProvider.EP_NAME.addExtensionPointListener(scope, object : ExtensionPointListener<ExternalJavaConfigurationProvider<*>> {
      override fun extensionAdded(extension: ExternalJavaConfigurationProvider<*>, pluginDescriptor: PluginDescriptor) {
        updateFromConfig(extension)
      }
    })

    scope.launch {
      project.workspaceModel.eventLog.collect { event ->
        if (event.getChanges(SdkEntity::class.java).any() || event.getChanges(ContentRootEntity::class.java).any()) {
          ExternalJavaConfigurationProvider.EP_NAME.forEachExtensionSafe { extension ->
            updateFromConfig(extension)
          }
        }
      }
    }

  }

  public fun <T> addTerminationCallback(session: TerminalWidget, configProvider: ExternalJavaConfigurationProvider<T>) {
    session.addTerminationCallback({ updateFromConfig(configProvider) }, scope.asDisposable())
  }
}