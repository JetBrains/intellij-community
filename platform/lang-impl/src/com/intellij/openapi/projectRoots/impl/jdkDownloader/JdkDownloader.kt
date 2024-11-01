// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.execution.wsl.WslPath
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
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
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.getEelApiBlocking
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import java.util.function.Consumer
import java.util.function.Predicate
import javax.swing.JComponent

private val LOG = logger<JdkDownloader>()

@Internal
val JDK_DOWNLOADER_EXT: DataKey<JdkDownloaderDialogHostExtension> = DataKey.create("jdk-downloader-extension")

@Internal
interface JdkDownloaderDialogHostExtension {
  fun allowWsl() : Boolean = true

  fun getEel(): EelApi? = null

  fun createMainPredicate() : JdkPredicate? = null

  fun createWslPredicate() : JdkPredicate? = null

  fun createEelPredicate(eel: EelApi) : JdkPredicate? = null

  fun shouldIncludeItem(sdkType: SdkTypeId, item: JdkItem) : Boolean = true
}

@Internal
data class JdkInstallRequestInfo(override val item: JdkItem,
                                 override val installDir: Path): JdkInstallRequest {
  override val javaHome: Path
    get() = item.resolveJavaHome(installDir)
}

@Internal
class JdkDownloader : SdkDownload, JdkDownloaderBase {
  override fun supportsDownload(sdkTypeId: SdkTypeId): Boolean {
    if (!Registry.`is`("jdk.downloader")) return false
    if (ApplicationManager.getApplication().isUnitTestMode) return false
    return notSimpleJavaSdkTypeIfAlternativeExistsAndNotDependentSdkType().value(sdkTypeId)
  }

  override fun showDownloadUI(
    sdkTypeId: SdkTypeId,
    sdkModel: SdkModel,
    parentComponent: JComponent?,
    project: Project?,
    selectedSdk: Sdk?,
    sdkFilter: Predicate<Any>?,
    sdkCreatedCallback: Consumer<in SdkDownloadTask>,
  ) {
    if (project?.isDisposed == true) return
    val (jdkItem, jdkHome) = pickJdkItem(sdkTypeId, parentComponent, null, project, sdkFilter) ?: return
    prepareDownloadTask(project, jdkItem, jdkHome, sdkCreatedCallback)
  }

  override fun pickSdk(
    sdkTypeId: SdkTypeId,
    sdkModel: SdkModel,
    parentComponent: JComponent,
    selectedSdk: Sdk?,
  ): SdkDownloadTask? {
    val dataContext = DataManager.getInstance().getDataContext(parentComponent)
    val project = CommonDataKeys.PROJECT.getData(dataContext)
    if (project?.isDisposed == true) return null
    val (jdkItem, jdkHome) = pickJdkItem(sdkTypeId, parentComponent, dataContext, project, null, ProjectBundle.message("dialog.button.select.jdk")) ?: return null
    return JdkDownloadTask(jdkItem, JdkInstallRequestInfo(jdkItem, jdkHome), project)
  }

  override fun showDownloadUI(
    sdkTypeId: SdkTypeId,
    sdkModel: SdkModel,
    parentComponent: JComponent,
    selectedSdk: Sdk?,
    sdkCreatedCallback: Consumer<in SdkDownloadTask>,
  ) {
    val dataContext = DataManager.getInstance().getDataContext(parentComponent)
    val project = CommonDataKeys.PROJECT.getData(dataContext)
    if (project?.isDisposed == true) return
    val (jdkItem, jdkHome) = pickJdkItem(sdkTypeId, parentComponent, dataContext, project, null) ?: return
    prepareDownloadTask(project, jdkItem, jdkHome, sdkCreatedCallback)
  }

  private fun pickJdkItem(sdkTypeId: SdkTypeId,
                          parentComponent: JComponent?,
                          dataContext: DataContext?,
                          project: Project?,
                          sdkFilter: Predicate<Any>?,
                          okActionText: @NlsContexts.Button String = ProjectBundle.message("dialog.button.download.jdk")): Pair<JdkItem, Path>? {
    val extension = dataContext?.getData(JDK_DOWNLOADER_EXT)
    return selectJdkAndPath(project, sdkTypeId, parentComponent, extension, sdkFilter, okActionText)
  }

  private fun selectJdkAndPath(
    project: Project?,
    sdkTypeId: SdkTypeId,
    parentComponent: JComponent?,
    extension: JdkDownloaderDialogHostExtension?,
    sdkFilter: Predicate<Any>?,
    okActionText: @NlsContexts.Button String,
  ): Pair<JdkItem, Path>? {
    val items = try {
      val extension = extension ?: object : JdkDownloaderDialogHostExtension {
        override fun getEel(): EelApi? {
          if (!Registry.`is`("java.home.finder.use.eel")) {
            return null
          }
          else {
            return project.getEelApiBlocking()
          }
        }
      }
      computeInBackground(project, ProjectBundle.message("progress.title.downloading.jdk.list")) {

        val buildModel = { predicate: JdkPredicate ->
          JdkListDownloader.getInstance()
            .downloadForUI(predicate = predicate, progress = it)
            .filter { extension.shouldIncludeItem(sdkTypeId, it) }
            .takeIf { it.isNotEmpty() }
            ?.let { buildJdkDownloaderModel(it, { sdkFilter?.test(it) != false }) }
        }

        val allowWsl = extension.allowWsl()
        val wslDistributions = if (allowWsl) WslDistributionManager.getInstance().installedDistributions else listOf()
        val projectWslDistribution = if (allowWsl) project?.basePath?.let { WslPath.getDistributionByWindowsUncPath(it) } else null

        val mainModel = buildModel(extension.createMainPredicate() ?: JdkPredicate.default()) ?: return@computeInBackground null
        val wslModel = if (allowWsl && wslDistributions.isNotEmpty()) buildModel(extension.createWslPredicate() ?: JdkPredicate.forWSL()) else null

        val eelApi = extension.getEel()
        val eelPair: Pair<EelApi, JdkDownloaderModel>? =
          if (eelApi != null) {
            buildModel(extension.createEelPredicate(eelApi) ?: JdkPredicate.forEel(eelApi))?.let { eelApi to it }
          }
          else null

        JdkDownloaderMergedModel(
          mainModel = mainModel,
          wslModel = wslModel,
          eelModel = eelPair?.second,
          wslDistributions = wslDistributions,
          eel = eelPair?.first,
          projectWSLDistribution = projectWslDistribution,
        )
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

    return JdkDownloadDialog(project, parentComponent, sdkTypeId, items, okActionText).selectJdkAndPath()
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

internal fun selectJdkAndPath(
  project: Project?,
  parentComponent: JComponent?,
  items: List<JdkItem>,
  sdkTypeId: SdkTypeId,
  extension: JdkDownloaderDialogHostExtension?,
  text: @Nls String?,
  okActionText: @NlsContexts.Button String,
): Pair<JdkItem, Path>? {
  val extension = extension ?: object : JdkDownloaderDialogHostExtension {}

  val allowWsl = extension.allowWsl()
  val wslDistributions = if (allowWsl) WslDistributionManager.getInstance().installedDistributions else listOf()
  val projectWslDistribution = if (allowWsl) project?.basePath?.let { WslPath.getDistributionByWindowsUncPath(it) } else null

  val mainModel = buildJdkDownloaderModel(items) { extension.shouldIncludeItem(sdkTypeId, it) }

  val eelModel = null // TODO What should be here?

  val mergedModel = JdkDownloaderMergedModel(
    mainModel = mainModel,
    wslModel = null,
    eelModel = eelModel,
    eel = null,
    wslDistributions = wslDistributions,
    projectWSLDistribution = projectWslDistribution,
  )

  if (project?.isDisposed == true) return null

  return JdkDownloadDialog(project, parentComponent, sdkTypeId, mergedModel, okActionText, text).selectJdkAndPath()
}

@Internal
interface JdkDownloaderBase {
  companion object {
    fun newDownloadTask(item: JdkItem, request: JdkInstallRequest, project: Project?): SdkDownloadTask {
      return JdkDownloadTask(item, request, project)
    }
  }
}

@Internal
class JdkDownloadTask(
  @JvmField val jdkItem: JdkItem,
  @JvmField val request: JdkInstallRequest,
  @JvmField val project: Project?,
): SdkDownloadTask {
  override fun getSuggestedSdkName() = request.item.suggestedSdkName
  override fun getPlannedHomeDir() = request.javaHome.toString()
  override fun getPlannedVersion() = request.item.versionString
  override fun getProductName(): String = request.item.fullPresentationWithVendorText
  override fun doDownload(indicator: ProgressIndicator) {
    JdkInstaller.getInstance().installJdk(request, indicator, project)
  }

  override fun toString() = "DownloadTask{${request.item.fullPresentationText}, dir=${request.installDir}}"
}