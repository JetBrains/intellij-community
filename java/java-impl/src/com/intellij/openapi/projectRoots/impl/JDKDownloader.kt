// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.*
import com.intellij.util.Consumer
import com.intellij.util.io.HttpRequests
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent

class JDKDownloader {
  companion object {
    @JvmStatic
    fun getInstance(): JDKDownloader = ApplicationManager.getApplication().getService(JDKDownloader::class.java)

    private val LOG = logger<JDKDownloader>()
  }

  val isEnabled
    get() = Registry.`is`("jdk.downloader.ui")

  lateinit var mock: JDKDownloadItem

  fun showCustomCreateUI(sdkModel: SdkModel,
                         parentComponent: JComponent,
                         selectedSdk: Sdk?,
                         sdkCreatedCallback: Consumer<Sdk>) {
    val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(parentComponent)) ?: return

    ProgressManager.getInstance().run(object : Task.Modal(project, "Downloading JDK list...", true) {
      override fun run(indicator: ProgressIndicator) {
        val model = downloadModel(progress = indicator)
        mock = model.items.first()

        invokeLater {
          dialog(title = "Download JDK",
                 parent = parentComponent,
                 resizable = false,
                 project = project,
                 modality = DialogWrapper.IdeModalityType.PROJECT,
                 panel = panel {
                   row("Vendor:") {
                     comboBox(
                       model = DefaultComboBoxModel(model.items.toTypedArray()),
                       prop = ::mock,
                       renderer = listCellRenderer { value, _, _ -> text = "${value.vendor}: ${value.version}" }
                     )
                   }
                   //row("Version") { comboBox() }
                   row {
                     right {
                       button("Download", actionListener = {})
                     }
                   }
                   row { }
                   row("Path to JDK:") {
                     textFieldWithBrowseButton("Select installation path for the JDK", project = project)
                   }

                 }).show()
        }
      }
    })
  }

  private val om = ObjectMapper()

  private val feedUrl: String
    get() {
      val registry = runCatching { Registry.get("jdk.downloader.url").asString() }.getOrNull()
      if (!registry.isNullOrBlank()) return registry

      //let's use CDN URL in once it'd be established
      return "https://buildserver.labs.intellij.net/guestAuth/repository/download/ijplatform_master_Service_GenerateJDKsJson/lasest.lastSuccessful/feed.zip!/jdks.json"
    }

  fun downloadModel(progress: ProgressIndicator?): JDKDownloadModel {
    //use HTTP caches here, in-memory only
    //note we use 3 copies of data here: String, JSON and Model (first two should GC)
    val rawData = HttpRequests.request(feedUrl).forceHttps(true).readString(progress)
    val tree = om.readTree(rawData) as? ObjectNode ?: error("Unexpected JSON data")
    val items = tree["jdks"] as? ArrayNode ?: error("`jdks` element is missing")

    val expectedOS = when {
      SystemInfo.isWindows -> "windows"
      SystemInfo.isMac -> "mac"
      SystemInfo.isLinux -> "linux"
      else -> error("Unsupported OS")
    }

    val result = mutableListOf<JDKDownloadItem>()
    for (item in items.filterIsInstance<ObjectNode>()) {
      val vendor = item["vendor"]?.asText() ?: continue
      val version = item["jdk_version"]?.asText() ?: continue
      val packages = item["packages"] as? ArrayNode ?: continue
      val pkg = packages.filterIsInstance<ObjectNode>().singleOrNull { it["os"]?.asText() == expectedOS } ?: continue
      val arch = pkg["arch"]?.asText() ?: continue
      val fileType = pkg["package"]?.asText() ?: continue
      val url = pkg["url"]?.asText() ?: continue
      val size = pkg["size"]?.asLong() ?: continue
      val sha256 = pkg["sha256"]?.asText() ?: continue

      result += JDKDownloadItem(vendor = vendor,
                                version = version,
                                arch = arch,
                                fileType = fileType,
                                url = url,
                                size = size,
                                sha256 = sha256)
    }

    return JDKDownloadModel(result)
  }

}

data class JDKDownloadModel(
  val items: List<JDKDownloadItem>
)

data class JDKDownloadItem(
  val vendor: String,
  val version: String,
  val arch: String,
  val fileType: String,
  val url: String,
  val size: Long,
  val sha256: String
)
