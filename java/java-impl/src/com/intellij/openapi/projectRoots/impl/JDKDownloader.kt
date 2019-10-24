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
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.*
import com.intellij.util.Consumer
import com.intellij.util.io.HttpRequests
import org.tukaani.xz.XZInputStream
import java.io.ByteArrayInputStream
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

class JDKDownloader {
  companion object {
    @JvmStatic
    fun getInstance(): JDKDownloader? = if (!isEnabled) null else ApplicationManager.getApplication().getService(JDKDownloader::class.java)

    private val LOG = logger<JDKDownloader>()

    @JvmStatic
    val isEnabled
      get() = Registry.`is`("jdk.downloader.ui")
  }

  fun showCustomCreateUI(javaSdk: JavaSdkImpl,
                         sdkModel: SdkModel,
                         parentComponent: JComponent,
                         selectedSdk: Sdk?,
                         sdkCreatedCallback: Consumer<Sdk>) {
    val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(parentComponent)) ?: return

    ProgressManager.getInstance().run(object : Task.Modal(project, "Downloading JDK list...", true) {
      override fun run(indicator: ProgressIndicator) {
        val model = downloadModel(progress = indicator)
        if (model.feedError != null) return /*Handle error?*/

        invokeLater {
          if (project.isDisposedOrDisposeInProgress) return@invokeLater
          val uiModel = JDKDownloadIUModel(project, model.items)

          dialog(title = "Download JDK",
                 parent = parentComponent,
                 resizable = false,
                 project = project,
                 modality = DialogWrapper.IdeModalityType.PROJECT,
                 panel = panel {
                   row("Vendor:") {
                     cell {
                       uiModel.apply { vendorCombobox }
                     }
                   }
                   row("Version:") {
                     uiModel.apply { versionCombobox }
                   }
                   row { }
                   row("Install JDK to:") {
                     uiModel.apply { installPathChooser }
                   }
                 }).show()
        }
      }
    })
  }

  class JDKDownloadIUModel(
    val project: Project? = null,
    val items: List<JDKDownloadItem>,
    defaultItem: JDKDownloadItem = items.first()
    ) {

    var selectedVendor: JDKVendor by Delegates.observable(defaultItem.vendor, this::updateVendorState)
    val vendorModel = DefaultComboBoxModel<JDKVendor>()
    val vendorRenderer = listCellRenderer<JDKVendor> { vendor, _, _ -> setText(vendor.vendor) }
    val Cell.vendorCombobox get() =
      comboBox(model = vendorModel, prop = this@JDKDownloadIUModel::selectedVendor, renderer = vendorRenderer)
      .applyIfEnabled()

    var selectedVersion: JDKDownloadItem by Delegates.observable(defaultItem, this::updateVersionState)
    val versionModel = DefaultComboBoxModel<JDKDownloadItem>()
    val versionRenderer = listCellRenderer<JDKDownloadItem> { it, _, _ ->
      setText("${it.version} (${StringUtil.formatFileSize(it.size)})")
    }
    val Cell.versionCombobox get() =
      comboBox(model = versionModel, prop = this@JDKDownloadIUModel::selectedVersion, renderer = versionRenderer)


    var installPath: String by Delegates.observable(generateInstallPath(defaultItem)) {_,_,_ ->}
    val Cell.installPathChooser get() = textFieldWithBrowseButton(project = project,
                                                                  browseDialogTitle = "Select installation path for the JDK",
                                                                  prop = this@JDKDownloadIUModel::installPath,
                                                                  fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                                                                  ).applyIfEnabled()

    private fun generateInstallPath(item: JDKDownloadItem): String {
      return "~/.jdks/${item.installFolderName}"
    }

    init {
      items.map { it.vendor }.toSet().forEach {
        vendorModel.addElement(it)
      }
    }

    private fun updateVendorState(prop: KProperty<*>, oldVendor: JDKVendor, newVendor: JDKVendor) {
      if (oldVendor == newVendor) return

      val oldVersion = selectedVersion
      versionModel.removeAllElements()

      val newVersions = items.filter { it.vendor == newVendor }
      newVersions.forEach {
        versionModel.addElement(it)
      }
      selectedVersion = newVersions.firstOrNull { it.version.startsWith(oldVersion.version) } ?: newVersions.first()
    }

    private fun updateVersionState(prop: KProperty<*>, oldVersion: JDKDownloadItem, newVersion: JDKDownloadItem) {
      if (oldVersion == newVersion) return

      //todo: tell customized path and ask
      installPath = generateInstallPath(newVersion)
    }
  }

  private val om = ObjectMapper()

  private val feedUrl: String
    get() {
      val registry = runCatching { Registry.get("jdk.downloader.url").asString() }.getOrNull()
      if (!registry.isNullOrBlank()) return registry

      //let's use CDN URL in once it'd be established
      return "https://buildserver.labs.intellij.net/guestAuth/repository/download/ijplatform_master_Service_GenerateJDKsJson/lasest.lastSuccessful/feed.zip!/jdks.json.xz"
    }

  fun downloadModel(progress: ProgressIndicator?, feedUrl : String = this.feedUrl): JDKDownloadModel {
    //we download XZ packed version of the data (several KBs packed, several dozen KBs unpacked) and process it in-memory
    val rawData = try {
      HttpRequests
        .request(feedUrl)
        .connectTimeout(5_000)
        .readTimeout(5_000)
        .forceHttps(true)
        .throwStatusCodeException(true)
        .readBytes(progress)
        .unXZ()
    } catch (t: Throwable) {
      LOG.warn("Failed to download and process the JDKs list from $feedUrl. ${t.message}", t)
      return JDKDownloadModel.errorModel("Failed to download and process the JDKs list from $feedUrl")
    }

    try {
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

        result += JDKDownloadItem(vendor = JDKVendor(vendor),
                                  version = version,
                                  arch = arch,
                                  fileType = fileType,
                                  url = url,
                                  size = size,
                                  sha256 = sha256)
      }

      return JDKDownloadModel.validModel(result)
    } catch (t: Throwable) {
      LOG.warn("Failed to parse downloaded JDKs list from $feedUrl. ${t.message}", t)
      return JDKDownloadModel.errorModel("Failed to parse downloaded JDKs list")
    }
  }
}

class JDKDownloadModel private constructor(
  val feedError: String? = null,
  val items: List<JDKDownloadItem> = listOf()
) {
  companion object {
    fun errorModel(error: String) = JDKDownloadModel(feedError = error)
    fun validModel(items: List<JDKDownloadItem>) = JDKDownloadModel(items = items)
  }
}

data class JDKVendor(
  val vendor: String
)

data class JDKDownloadItem(
  val vendor: JDKVendor,
  val version: String,
  val arch: String,
  val fileType: String,
  val url: String,
  val size: Long,
  val sha256: String
) {
  val installFolderName get() = url.split("/").last() //TODO: use feed for it
}

private fun ByteArray.unXZ() = ByteArrayInputStream(this).use { input ->
  XZInputStream(input).use { it.readBytes() }
}
