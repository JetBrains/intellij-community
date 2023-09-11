// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.java.JavaBundle
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JdkFinder
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.jps.model.java.JdkVersionDetector
import java.io.File
import java.util.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

private val LOG = logger<SdkmanrcWatcher>()

data class SdkmanCandidate(val target: String,
                           val version: String,
                           val flavour: String? = null,
                           val vendor: String? = null) {
  companion object {
    private val regex: Regex = Regex("(\\d+(?:\\.\\d+)*)(?:\\.([^-]+))?-?(.*)?")

    fun parse(text: String): SdkmanCandidate? {
      val matchResult = regex.find(text) ?: return null
      return SdkmanCandidate(
        text,
        matchResult.groups[1]?.value ?: return null,
        matchResult.groups[2]?.value,
        matchResult.groups[3]?.value
      )
    }
  }

  fun matchVersionString(versionString: @NlsSafe String): Boolean {
    LOG.info("Matching '$versionString'")
    if ("version $version" !in versionString && "version \"$version" !in versionString) return false

    val variant = when {
      vendor == "adpt" && flavour == "hs" -> JdkVersionDetector.Variant.AdoptOpenJdk_HS
      vendor == "adpt" && flavour == "j9" -> JdkVersionDetector.Variant.AdoptOpenJdk_J9
      vendor == "amzn" -> JdkVersionDetector.Variant.Corretto
      vendor == "grl" -> JdkVersionDetector.Variant.GraalVM
      vendor == "jbr" -> JdkVersionDetector.Variant.JBR
      vendor == "librca" -> JdkVersionDetector.Variant.Liberica
      vendor == "oracle" -> JdkVersionDetector.Variant.Oracle
      vendor == "open" -> JdkVersionDetector.Variant.Oracle
      vendor == "sapmchn" -> JdkVersionDetector.Variant.SapMachine
      vendor == "sem" -> JdkVersionDetector.Variant.Semeru
      vendor == "tem" -> JdkVersionDetector.Variant.Temurin
      vendor == "zulu" -> JdkVersionDetector.Variant.Zulu
      else -> JdkVersionDetector.Variant.Unknown
    }

    // Check vendor
    val variantName = variant.displayName
    return variantName != null && versionString.contains(variantName)
  }

}

@Service(Service.Level.PROJECT)
class SdkmanrcWatcherService(private val project: Project, private val scope: CoroutineScope): Disposable {
  val file: File = File(project.basePath, ".sdkmanrc")

  fun registerListener(project: Project) {
    val connection = project.messageBus.connect(this)

    connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: MutableList<out VFileEvent>) {
        for (event in events) {
          when {
            !event.path.endsWith(".sdkmanrc") -> {}
            event is VFileContentChangeEvent || event is VFileCreateEvent -> configureSdkFromSdkmanrc()
          }
        }
      }
    })
  }

  sealed class SdkSuggestion {
    data class Jdk(val target: SdkmanCandidate, val jdk: Sdk, val project: Boolean) : SdkSuggestion()
    data class Path(val target: SdkmanCandidate, val path: String) : SdkSuggestion()
  }

  fun configureSdkFromSdkmanrc() {
    scope.launch {
      val result = suggestSdkFromSdkmanrc()
      withContext(Dispatchers.EDT) {
        when {
          result is SdkSuggestion.Jdk && !result.project -> configure(result.jdk, result.target)
          result is SdkSuggestion.Path -> configure(result.path, result.target)
          else -> Unit
        }
      }
    }
  }

  @RequiresBackgroundThread
  suspend fun suggestSdkFromSdkmanrc(): SdkSuggestion? {
    if (!file.exists()) return null

    // Parse .sdkmanrc
    val properties = Properties().apply {
      val vfText = readAction {
        findVirtualFile()?.let { FileDocumentManager.getInstance().getDocument(it)?.text }
      }
      load(vfText?.byteInputStream() ?: file.inputStream())
    }

    val java = properties.getProperty("java") ?: return null
    val target = SdkmanCandidate.parse(java) ?: return null
    LOG.info(".sdkmanrc found: $target")

    val testedPaths = mutableListOf<String>()

    // Match against the project SDK
    val projectSdk = ProjectRootManager.getInstance(project).projectSdk
    if (target.match(projectSdk)) {
      return SdkSuggestion.Jdk(target, projectSdk, true)
    } else {
      projectSdk?.homePath?.let { testedPaths.add(it) }
      LOG.info("$java - Project JDK doesn't match .sdkmanrc (${projectSdk?.versionString})")
    }

    // Match against the project JDK table
    val jdks = ProjectJdkTable.getInstance().allJdks
    for (jdk in jdks) {
      if (jdk.homePath !in testedPaths && target.match(jdk)) {
        jdk.homePath?.let { testedPaths.add(it) }
        LOG.info("$java - Candidate found: ${jdk.versionString}")
        return SdkSuggestion.Jdk(target, jdk, false)
      }
    }

    // Match against JdkFinder
    JdkFinder.getInstance().suggestHomePaths().forEach { path ->
      if (path !in testedPaths && target.match(path)) {
        testedPaths.add(path)
        return SdkSuggestion.Path(target, path)
      }
    }

    return null
  }

  private fun findVirtualFile() = VirtualFileManager.getInstance().findFileByNioPath(file.toPath().toAbsolutePath())

  @RequiresEdt
  private suspend fun configure(path: String, target: SdkmanCandidate) {
    LOG.info("${target.target} - Candidate found: ${SdkVersionUtil.getJdkVersionInfo(path)?.displayVersionString()}")

    val jdk = SdkConfigurationUtil.createAndAddSDK(path, JavaSdk.getInstance())

    if (jdk != null) {
      LOG.info("${target.target} - Registered ${jdk.name}")
      configure(jdk, target)
    }
  }

  @RequiresEdt
  private suspend fun configure(jdk: Sdk, target: SdkmanCandidate) {
    val rootManager = ProjectRootManager.getInstance(project)

    writeAction {
      rootManager.projectSdk = jdk
    }

    LOG.info("${target.target} - Configured ${jdk.name}")

    NotificationGroupManager.getInstance()
      .getNotificationGroup("Setup SDK")
      .createNotification(
        JavaBundle.message("sdk.configured.sdkmanrc.title"),
        JavaBundle.message("sdk.configured.sdkmanrc", jdk.versionString),
        NotificationType.INFORMATION
      )
      .notify(project)
  }

  @OptIn(ExperimentalContracts::class)
  private fun SdkmanCandidate.match(sdk: Sdk?): Boolean {
    contract { returns(true) implies (sdk != null) }
    if (sdk == null) return false
    val versionString = sdk.versionString ?: return false
    return matchVersionString(versionString)
  }

  private fun SdkmanCandidate.match(path: String): Boolean {
    val info = SdkVersionUtil.getJdkVersionInfo(path) ?: return false
    return matchVersionString(info.displayVersionString())
  }

  override fun dispose() {}
}

class SdkmanrcWatcher : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (!AdvancedSettings.getBoolean("java.sdkmanrc.watcher")) return
    project.service<SdkmanrcWatcherService>().apply {
      registerListener(project)
      configureSdkFromSdkmanrc()
    }
  }
}