// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.java.JavaBundle
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
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
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import org.jetbrains.jps.model.java.JdkVersionDetector
import java.io.File
import java.util.*

private val LOG = logger<SdkmanrcWatcher>()

private class JdkTarget(val target: String,
                        val version: String,
                        val flavour: String? = null,
                        val vendor: String? = null)

@Service(Service.Level.PROJECT)
class SdkmanrcWatcherService: Disposable {
  fun registerListener(project: Project) {
    val connection = project.messageBus.connect(this)

    connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: MutableList<out VFileEvent>) {
        for (event in events) {
          if (event is VFileContentChangeEvent && event.path.endsWith(".sdkmanrc")) {
            parseSdkmanrc(project)
          }
        }
      }
    })
  }

  fun parseSdkmanrc(project: Project) {
    // Parse .sdkmanrc
    val file = File(project.basePath, ".sdkmanrc")
    if (!file.exists()) return

    val properties = Properties().apply {
      load(file.reader())
    }

    val java = properties.getProperty("java") ?: return
    val regex = Regex("(\\d+(?:\\.\\d+)*)(?:\\.([^-]+))?-?(.*)?")

    val matchResult = regex.find(java) ?: return

    val target = JdkTarget(
      java,
      matchResult.groups[1]?.value ?: return,
      matchResult.groups[2]?.value,
      matchResult.groups[3]?.value
    )

    val testedPaths = mutableListOf<String>()

    // Match against the project SDK
    val projectSdk = ProjectRootManager.getInstance(project).projectSdk
    if (target.match(projectSdk)) {
      return
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
        configure(jdk, target, project)
        return
      }
    }

    // Match against JdkFinder
    JdkFinder.getInstance().suggestHomePaths().forEach { path ->
      if (path !in testedPaths && target.match(path)) {
        testedPaths.add(path)
        configure(path, target, project)
        return
      }
    }
  }

  private fun configure(path: String, target: JdkTarget, project: Project) {
    LOG.info("${target.target} - Candidate found: ${SdkVersionUtil.getJdkVersionInfo(path)?.displayVersionString()}")

    val jdk = SdkConfigurationUtil.createAndAddSDK(path, JavaSdk.getInstance())

    if (jdk != null) {
      LOG.info("${target.target} - Registered ${jdk.name}")
      configure(jdk, target, project)
    }
  }

  private fun configure(jdk: Sdk, target: JdkTarget, project: Project) {
    val rootManager = ProjectRootManager.getInstance(project)
    rootManager.projectSdk = jdk

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

  private fun JdkTarget.match(sdk: Sdk?): Boolean {
    if (sdk == null) return false
    val versionString = sdk.versionString ?: return false
    return matchVersionString(versionString)
  }

  private fun JdkTarget.match(path: String): Boolean {
    val info = SdkVersionUtil.getJdkVersionInfo(path) ?: return false
    return matchVersionString(info.displayVersionString())
  }

  private fun JdkTarget.matchVersionString(versionString: @NlsSafe String): Boolean {
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

  override fun dispose() {}
}

class SdkmanrcWatcher : ProjectActivity {
  override suspend fun execute(project: Project) {
    project.service<SdkmanrcWatcherService>().registerListener(project)
  }
}