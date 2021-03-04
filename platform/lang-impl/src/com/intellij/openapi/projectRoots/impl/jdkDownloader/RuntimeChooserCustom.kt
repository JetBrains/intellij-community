// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.lang.LangBundle
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.SimpleJavaSdkType
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.ui.Messages
import java.nio.file.Path
import javax.swing.JComponent

data class RuntimeChooserCustomItem(
  val version: String,
  override val homeDir: String,
) : RuntimeChooserItem(), RuntimeChooserItemWithFixedLocation

object RuntimeChooserAddCustomItem : RuntimeChooserItem()

object RuntimeChooserCustom {
  val sdkType: SdkType?
    get() = SdkType
      .getAllTypes()
      .singleOrNull(SimpleJavaSdkType.notSimpleJavaSdkTypeIfAlternativeExistsAndNotDependentSdkType()::value)

  val isActionAvailable
    get() = sdkType != null

  fun showAddCustomJdkPopup(parent: JComponent, model: RuntimeChooserModel) {
    val type = sdkType
    if (type == null) return

    SdkConfigurationUtil.selectSdkHome(type, parent) { home ->
      if (home != null) {
        importNewItem(parent, home, model)
      }
    }
  }

  private fun importNewItem(parent: JComponent, homePath: String, model: RuntimeChooserModel) {
    object : Task.Modal(null, LangBundle.message("progress.title.choose.ide.runtime.scanning.jdk"), false) {
      override fun run(indicator: ProgressIndicator) {
        RuntimeChooserJreValidator.testNewJdkUnderProgress(
          computeHomePath = { homePath },
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
