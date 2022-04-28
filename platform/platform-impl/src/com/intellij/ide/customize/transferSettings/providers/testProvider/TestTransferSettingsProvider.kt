// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.providers.testProvider

import com.intellij.icons.AllIcons
import com.intellij.ide.customize.transferSettings.models.*
import com.intellij.ide.customize.transferSettings.providers.DefaultImportPerformer
import com.intellij.ide.customize.transferSettings.providers.TransferSettingsProvider
import java.util.*

class TestTransferSettingsProvider : TransferSettingsProvider {
  override val name = "Test"

  override fun isAvailable() = true

  override fun getIdeVersions(skipIds: List<String>): List<BaseIdeVersion> {
    if (skipIds.isNotEmpty()) return emptyList()
    return listOf(IdeVersion("test23", AllIcons.CodeWithMe.CwmJoin, "Test Instance", "yes", Settings(

      laf = BundledLookAndFeel.fromManager("Darcula"),
      //keymap = BundledKeymap("My cool keymap", "Sublime Text", emptyList(/* fill this with shortcuts samples or action ids */)),
      plugins = mutableListOf(
        PluginFeature("com.intellij.ideolog", "Ideolog")
      )
    ), Date(), this))
  }

  override fun getImportPerformer(ideVersion: IdeVersion) = DefaultImportPerformer()
}