// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jdkDownloader

import com.google.common.collect.Sets
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.*
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownload
import com.intellij.openapi.ui.*
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import java.io.File
import java.util.function.Consumer
import javax.swing.JComponent

private class JdkDownloadProgress(
  val request: JdkInstallRequest
) {
  private val myListeners = Sets.newIdentityHashSet<SdkDownload.DownloadProgressListener>()
  private val proxyProgress = ProgressIndicatorBase()

  fun bindProgressIndicator(indicator: ProgressIndicator, action: () -> Unit) {
    indicator as ProgressIndicatorBase
    indicator.addStateDelegate(proxyProgress)
    try {
      return action()
    } finally {
      indicator.removeStateDelegate(proxyProgress)
    }
  }

  fun onDownloadCompleted() {
    ApplicationManager.getApplication().assertIsDispatchThread()
    myListeners.forEach { it.onDownloadCompleted() }
  }

  fun onDownloadFailed(message: String) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    myListeners.forEach { it.onDownloadFailed(message)}
  }

  fun subscribe(disposable: Disposable, handler: SdkDownload.DownloadProgressListener) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    if (!myListeners.add(handler)) return

    val uiIndicator = handler.progressIndicator
    proxyProgress.addStateDelegate(uiIndicator)

    Disposer.register(disposable, Disposable {
      ApplicationManager.getApplication().assertIsDispatchThread()
      proxyProgress.removeStateDelegate(uiIndicator)
      myListeners.remove(handler)
    })
  }
}

object JdkDownloader {
  private val LOG = logger<JdkDownloader>()
  private val JdkDownloadProgressKey = Key.create<JdkDownloadProgress>("jdk-install-progress]")

  fun createSdk(sdkModel: SdkModel, javaSdkType: SdkTypeId, request: JdkInstallRequest): Sdk {
    //TODO[jo]: inherit name from a missing JDK in the UI (e.g. I need JDK 11, I'd like new one be named as 11 too)
    val sdk = sdkModel.createSdk(javaSdkType as SdkType, request.item.suggestedSdkName, request.targetDir.absolutePath)

    sdk.sdkModificator.apply {
      versionString = request.item.jdkVersion
    }.commitChanges()

    startJdkDownloadAsync(sdk, JdkDownloadProgress(request))
    return sdk
  }

  private fun startJdkDownloadAsync(sdk: Sdk, request: JdkDownloadProgress, project: Project? = null) {
    // we assume the Sdk instance is available in the model!
    sdk.putUserData(JdkDownloadProgressKey, request)

    val downloadSdkTask = object : Task.Backgroundable(project, "Installing JDK", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
      override fun run(indicator: ProgressIndicator) = request.bindProgressIndicator(indicator) {
        try {
          JdkInstaller.installJdk(request.request, indicator)
        }
        catch (t: ProcessCanceledException) {
          throw t
        }
        catch (e: Exception) {
          val msg = "Failed to install JDK ${request.request.item} to ${request.request.targetDir}. ${e.message}"
          LOG.warn(msg, e)
          Messages.showMessageDialog(project,
                                     msg,
                                     JdkDownloadDialog.DIALOG_TITLE,
                                     Messages.getErrorIcon())
          invokeLater { request.onDownloadFailed(msg) }
          return@bindProgressIndicator
        }

        require(sdk.homePath?.let(::File) == request.request.targetDir) {
          "Sdk home is ${sdk.homePath} must be the same as in the request: ${request.request.targetDir}"
        }

        WriteAction.runAndWait<Exception> {
          LOG.info("Jdk Download complete, updating $sdk for $request")
          (sdk.sdkType as SdkType).setupSdkPaths(sdk)
          sdk.putUserData(JdkDownloadProgressKey, null)
        }
        invokeLater { request.onDownloadCompleted() }
      }
    }

    ProgressManager.getInstance().run(downloadSdkTask)
  }

  fun subscribeDownload(sdk: Sdk,
                        lifetime: Disposable,
                        listener: SdkDownload.DownloadProgressListener): Boolean {
    val progress = sdk.getUserData(JdkDownloadProgressKey) ?: return false
    progress.subscribe(lifetime, listener)
    return true
  }
}

internal class JdkDownloaderUI: SdkDownload {
  private val LOG = logger<JdkDownloaderUI>()

  override fun supportsDownload(sdkTypeId: SdkTypeId) = sdkTypeId is JavaSdkImpl

  override fun showDownloadUI(sdkTypeId: SdkTypeId,
                              sdkModel: SdkModel,
                              parentComponent: JComponent,
                              selectedSdk: Sdk?,
                              sdkCreatedCallback: Consumer<Sdk>) {

    val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(parentComponent))
    if (project?.isDisposed == true) return
    val items = downloadModelWithProgress(project, parentComponent) ?: return

    if (project?.isDisposed == true) return
    val (jdkItem, jdkHome) = JdkDownloadDialog(project, parentComponent, sdkTypeId, items).selectJdkAndPath() ?: return

    /// prepare the JDK to be installed (e.g. create home dir, write marker file
    val request = prepareJdkInstall(project, parentComponent, jdkItem, jdkHome) ?: return
    sdkTypeId as JavaSdkImpl
    val sdk = JdkDownloader.createSdk(sdkModel, sdkTypeId, request)
    sdkCreatedCallback.accept(sdk)
  }

  override fun addChangesListener(sdk: Sdk, lifetime: Disposable, listener: SdkDownload.DownloadProgressListener): Boolean {
    return supportsDownload(sdk.sdkType) && JdkDownloader.subscribeDownload(sdk, lifetime, listener)
  }

  private fun prepareJdkInstall(project: Project?, parentComponent: JComponent, jdkItem: JdkItem, jdkHome: String) : JdkInstallRequest? {
    val task = object : Task.WithResult<JdkInstallRequest?, Exception>(project, "Preparing JDK target folder...", true) {
      override fun compute(indicator: ProgressIndicator): JdkInstallRequest? {
        try {
          return JdkInstaller.prepareJdkInstallation(jdkItem, jdkHome)
        } catch (e: ProcessCanceledException) {
          throw e
        } catch (e: Exception) {
          //TODO[jo]: handle error to UI
          val msg = "Failed to prepare JDK installation to $jdkHome for ${jdkItem.fullPresentationText}. ${e.message}"
          LOG.warn(msg, e)
          invokeLater {
            //TODO[jo]: add message from an exception here
            Messages.showMessageDialog(parentComponent,
                                       msg,
                                       JdkDownloadDialog.DIALOG_TITLE,
                                       Messages.getErrorIcon()
            )
          }
          return null
        }
      }
    }

    return ProgressManager.getInstance().run(task)
  }

  private fun downloadModelWithProgress(project: Project?, parentComponent: JComponent): List<JdkItem>? {
    val task = object : Task.WithResult<List<JdkItem>?, Exception>(project, "Downloading the list of available JDKs...", true) {
      override fun compute(indicator: ProgressIndicator): List<JdkItem>? {
        try {
          return JdkListDownloader.downloadModel(progress = indicator)
        }
        catch (e: ProcessCanceledException) {
          throw e
        }
        catch (t: Exception) {
          LOG.warn(t.message, t)
          invokeLater {
            //TODO[jo]: add message from an exception here
            Messages.showMessageDialog(parentComponent,
                                       "Failed to download the list of installable JDKs",
                                       JdkDownloadDialog.DIALOG_TITLE,
                                       Messages.getErrorIcon()
            )
          }
          return null
        }
      }
    }

    return ProgressManager.getInstance().run(task)
  }
}

