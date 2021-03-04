// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.projectRoots.SimpleJavaSdkType
import com.intellij.openapi.projectRoots.impl.jdkDownloader.RuntimeChooserJreValidator.isSupportedSdkItem
import com.intellij.openapi.roots.ui.configuration.SdkPopup
import com.intellij.openapi.roots.ui.configuration.SdkPopupFactory
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTracker
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.util.Consumer
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import javax.swing.JComponent

data class RuntimeChooserCustomItem(
  val version: String,
  override val homeDir: String,
) : RuntimeChooserItem(), RuntimeChooserItemWithFixedLocation

object RuntimeChooserAddCustomItem : RuntimeChooserItem()

object RuntimeChooserCustom {
  val sdkType
    get() = SdkType
      .getAllTypes()
      .singleOrNull(SimpleJavaSdkType.notSimpleJavaSdkTypeIfAlternativeExistsAndNotDependentSdkType()::value)

  val isActionAvailable
    get() = sdkType != null

  val jdkDownloaderExtensionProvider = DataProvider { dataId ->
    when {
      JDK_DOWNLOADER_EXT.`is`(dataId) -> jdkDownloaderExtension
      else -> null
    }
  }

  private val jdkDownloaderExtension = object : JdkDownloaderDialogHostExtension {
    override fun allowWsl(): Boolean = false

    override fun shouldIncludeItem(sdkType: SdkTypeId, item: JdkItem): Boolean {
      return sdkType == this@RuntimeChooserCustom.sdkType && isSupportedSdkItem(item)
    }
  }

  fun createSdkChooserPopup(parent: JComponent, model: RuntimeChooserModel): SdkPopup? {
    return SdkPopupFactory
      .newBuilder()
      .withSdkType(sdkType ?: return null)
      .withSdkFilter { it != null && isSupportedSdkItem(it) }
      .withSuggestedSdkFilter { it != null && isSupportedSdkItem(it) }
      .onSdkSelected { sdk -> importNewItem(parent, sdk, model) }
      .buildPopup()
  }

  private fun importNewItem(parent: JComponent, sdk: Sdk?, model: RuntimeChooserModel) {
    if (sdk == null) return

    val task = object : Task.Modal(null, LangBundle.message("progress.title.choose.ide.runtime.scanning.jdk"), false) {
      override fun run(indicator: ProgressIndicator) {
        //a downloading JDK may be returned, we need to wait for it's download
        waitForDownloadingSdk(parent, indicator, sdk)

        indicator.checkCanceled()

        RuntimeChooserJreValidator.testNewJdkUnderProgress(
          computeHomePath = { sdk.homePath },
          callback = object : RuntimeChooserJreValidatorCallback<Unit> {
            override fun onSdkResolved(versionString: String, sdkHome: Path) {
              val newItem = RuntimeChooserCustomItem(versionString, sdkHome.toString())
              invokeLater {
                model.addExistingSdkItem(newItem)
              }
            }

            override fun onError(message: String) {
              invokeLater {
                Messages.showErrorDialog(parent, message, LangBundle.message("dialog.title.choose.ide.runtime"))
              }
            }
          })
      }
    }

    //we make sure we execute the task with the current parent and modality
    invokeLater(ModalityState.stateForComponent(parent)) {
      task.queue()
    }
  }

  private fun waitForDownloadingSdk(parent: JComponent, indicator: ProgressIndicator, sdk: Sdk) {
    if (!SdkDownloadTracker.getInstance().isDownloading(sdk)) return

    val lifetime = Disposer.newDisposable("Choose Runtime")
    try {
      var downloadSucceeed = false
      val downloadCompletion = CountDownLatch(1)
      val downloadIsRunning = invokeAndWaitIfNeeded {
        SdkDownloadTracker
          .getInstance()
          .tryRegisterDownloadingListener(
            sdk,
            lifetime,
            indicator,
            Consumer {
              downloadSucceeed = it
              downloadCompletion.countDown()
            })
      }

      if (downloadIsRunning) {
        ProgressIndicatorUtils.awaitWithCheckCanceled(downloadCompletion)
        if (!downloadSucceeed) {
          invokeLater {
            Messages.showErrorDialog(
              parent,
              LangBundle.message("dialog.message.choose.ide.runtime.sdk.download.error", sdk.name),
              LangBundle.message("dialog.title.choose.ide.runtime"))
          }
        }
      }
    }
    finally {
      Disposer.dispose(lifetime)
    }
  }
}
