// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jdkDownloader

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModel
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownload
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask
import com.intellij.openapi.ui.Messages
import java.util.function.Consumer
import javax.swing.JComponent

internal class JdkDownloader: SdkDownload {
  private val LOG = logger<JdkDownloader>()

  override fun supportsDownload(sdkTypeId: SdkTypeId) = sdkTypeId is JavaSdkImpl

  override fun showDownloadUI(sdkTypeId: SdkTypeId,
                              sdkModel: SdkModel,
                              parentComponent: JComponent,
                              selectedSdk: Sdk?,
                              sdkCreatedCallback: Consumer<SdkDownloadTask>) {
    val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(parentComponent))
    if (project?.isDisposed == true) return
    val items = downloadModelWithProgress(project, parentComponent) ?: return

    if (project?.isDisposed == true) return
    val (jdkItem, jdkHome) = JdkDownloadDialog(project, parentComponent, sdkTypeId, items).selectJdkAndPath() ?: return

    /// prepare the JDK to be installed (e.g. create home dir, write marker file)
    val request = prepareJdkInstall(project, parentComponent, jdkItem, jdkHome) ?: return

    sdkCreatedCallback.accept(object: SdkDownloadTask {
      override fun getSuggestedSdkName() = request.item.suggestedSdkName
      override fun getPlannedHomeDir() = request.targetDir.absolutePath
      override fun getPlannedVersion() = request.item.jdkVersion
      override fun doDownload(indicator: ProgressIndicator) {
        JdkInstaller.installJdk(request, indicator)
      }
    })
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
            //TODO[jo]: add the message from an exception here
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
            //TODO[jo]: add the message from an exception here
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

