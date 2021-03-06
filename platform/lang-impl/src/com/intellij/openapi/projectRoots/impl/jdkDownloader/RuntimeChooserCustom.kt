// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.projectRoots.SimpleJavaSdkType
import com.intellij.openapi.projectRoots.impl.jdkDownloader.RuntimeChooserJreValidator.isSupportedSdkItem
import com.intellij.openapi.roots.ui.configuration.SdkPopup
import com.intellij.openapi.roots.ui.configuration.SdkPopupFactory
import com.intellij.openapi.ui.Messages
import java.nio.file.Path
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
      .withNoDownlaodActions()
      .onSdkSelected { sdk -> importNewItem(parent, sdk, model) }
      .buildPopup()
  }

  private fun importNewItem(parent: JComponent, sdk: Sdk?, model: RuntimeChooserModel) {
    if (sdk == null) return

    object : Task.Modal(null, LangBundle.message("progress.title.choose.ide.runtime.scanning.jdk"), false) {
      override fun run(indicator: ProgressIndicator) {
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
    }.queue()
  }
}
