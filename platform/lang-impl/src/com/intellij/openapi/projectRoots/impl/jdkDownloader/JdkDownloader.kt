// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.execution.wsl.WslPath
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModel
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.projectRoots.SimpleJavaSdkType.notSimpleJavaSdkTypeIfAlternativeExistsAndNotDependentSdkType
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownload
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import java.nio.file.Path
import java.util.function.Consumer
import javax.swing.JComponent

private val LOG = logger<JdkDownloader>()


internal val JDK_DOWNLOADER_EXT: DataKey<JdkDownloaderDialogHostExtension> = DataKey.create("jdk-downloader-extension")

interface JdkDownloaderDialogHostExtension {
  fun allowWsl() : Boolean = true

  fun createMainPredicate() : JdkPredicate? = null

  fun createWslPredicate() : JdkPredicate? = null

  fun shouldIncludeItem(sdkType: SdkTypeId, item: JdkItem) : Boolean = true
}

class JdkDownloader : SdkDownload, JdkDownloaderBase {
  override fun supportsDownload(sdkTypeId: SdkTypeId): Boolean {
    if (!Registry.`is`("jdk.downloader")) return false
    if (ApplicationManager.getApplication().isUnitTestMode) return false
    return notSimpleJavaSdkTypeIfAlternativeExistsAndNotDependentSdkType().value(sdkTypeId)
  }

  override fun showDownloadUI(sdkTypeId: SdkTypeId,
                              sdkModel: SdkModel,
                              parentComponent: JComponent,
                              selectedSdk: Sdk?,
                              sdkCreatedCallback: Consumer<in SdkDownloadTask>) {
    val dataContext = DataManager.getInstance().getDataContext(parentComponent)
    val project = CommonDataKeys.PROJECT.getData(dataContext)
    if (project?.isDisposed == true) return

    val extension = dataContext.getData(JDK_DOWNLOADER_EXT)
    val (jdkItem, jdkHome) = selectJdkAndPath(project, sdkTypeId, parentComponent, extension) ?: return
    prepareDownloadTask(project, jdkItem, jdkHome, sdkCreatedCallback)
  }

  fun selectJdkAndPath(
    project: Project?,
    sdkTypeId: SdkTypeId,
    parentComponent: JComponent,
    extension: JdkDownloaderDialogHostExtension?,
  ): Pair<JdkItem, Path>? {
    val items = try {
      val extension = extension ?: object : JdkDownloaderDialogHostExtension {}
      computeInBackground(project, ProjectBundle.message("progress.title.downloading.jdk.list")) {

        val buildModel = { predicate: JdkPredicate ->
          JdkListDownloader.getInstance()
            .downloadForUI(predicate = predicate, progress = it)
            .filter { extension.shouldIncludeItem(sdkTypeId, it) }
            .takeIf { it.isNotEmpty() }
            ?.let { buildJdkDownloaderModel(it) }
        }

        val allowWsl = extension.allowWsl()
        val wslDistributions = if (allowWsl) WslDistributionManager.getInstance().installedDistributions else listOf()
        val projectWslDistribution = if (allowWsl) project?.basePath?.let { WslPath.getDistributionByWindowsUncPath(it) } else null

        val mainModel = buildModel(extension.createMainPredicate() ?: JdkPredicate.default()) ?: return@computeInBackground null
        val wslModel = if (allowWsl && wslDistributions.isNotEmpty()) buildModel(extension.createWslPredicate() ?: JdkPredicate.forWSL()) else null
        JdkDownloaderMergedModel(mainModel, wslModel, wslDistributions, projectWslDistribution)
      }
    }
    catch (e: Throwable) {
      if (e is ControlFlowException) throw e
      LOG.warn("Failed to download the list of installable JDKs. ${e.message}", e)
      null
    }

    if (project?.isDisposed == true) return null

    if (items == null) {
      Messages.showErrorDialog(project,
                               ProjectBundle.message("error.message.no.jdk.for.download"),
                               ProjectBundle.message("error.message.title.download.jdk")
      )
      return null
    }

    return JdkDownloadDialog(project, parentComponent, sdkTypeId, items).selectJdkAndPath()
  }

  fun prepareDownloadTask(
    project: Project?,
    jdkItem: JdkItem,
    jdkHome: Path,
    sdkCreatedCallback: Consumer<in SdkDownloadTask>
  ) {
    /// prepare the JDK to be installed (e.g. create home dir, write marker file)
    val request = try {
      computeInBackground(project, ProjectBundle.message("progress.title.preparing.jdk")) {
        JdkInstaller.getInstance().prepareJdkInstallation(jdkItem, jdkHome)
      }
    } catch (e: Throwable) {
      if (e is ControlFlowException) throw e
      LOG.warn("Failed to prepare JDK installation to $jdkHome. ${e.message}", e)
      Messages.showErrorDialog(project,
                               ProjectBundle.message("error.message.text.jdk.install.failed", jdkHome),
                               ProjectBundle.message("error.message.title.download.jdk")
      )
      return
    }

    sdkCreatedCallback.accept(JdkDownloaderBase.newDownloadTask(jdkItem, request, project))
  }

  private inline fun <T : Any?> computeInBackground(project: Project?,
                                                   @NlsContexts.DialogTitle title: String,
                                                   crossinline action: (ProgressIndicator) -> T): T =
    ProgressManager.getInstance().run(object : Task.WithResult<T, Exception>(project, title, true) {
      override fun compute(indicator: ProgressIndicator) = action(indicator)
    })
}

interface JdkDownloaderBase {
  companion object {
    fun newDownloadTask(item: JdkItem, request: JdkInstallRequest, project: Project?): SdkDownloadTask {
      return JdkDownloadTask(item, request, project)
    }
  }
}

class JdkDownloadTask(val jdkItem: JdkItem, val request: JdkInstallRequest, val project: Project?): SdkDownloadTask {
  override fun getSuggestedSdkName() = request.item.suggestedSdkName
  override fun getPlannedHomeDir() = request.javaHome.toString()
  override fun getPlannedVersion() = request.item.versionString
  override fun getProductName(): String = request.item.fullPresentationWithVendorText
  override fun doDownload(indicator: ProgressIndicator) {
    JdkInstaller.getInstance().installJdk(request, indicator, project)
  }

  override fun toString() = "DownloadTask{${request.item.fullPresentationText}, dir=${request.installDir}}"
}