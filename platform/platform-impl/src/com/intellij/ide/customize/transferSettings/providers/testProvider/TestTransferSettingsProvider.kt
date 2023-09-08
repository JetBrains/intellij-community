// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.providers.testProvider

import com.intellij.icons.AllIcons
import com.intellij.ide.customize.transferSettings.TransferableIdeFeatureId
import com.intellij.ide.customize.transferSettings.TransferableIdeId
import com.intellij.ide.customize.transferSettings.db.KnownLafs
import com.intellij.ide.customize.transferSettings.models.BaseIdeVersion
import com.intellij.ide.customize.transferSettings.models.IdeVersion
import com.intellij.ide.customize.transferSettings.models.PluginFeature
import com.intellij.ide.customize.transferSettings.models.Settings
import com.intellij.ide.customize.transferSettings.providers.TransferSettingsProvider
import java.util.*

class TestTransferSettingsProvider : TransferSettingsProvider {

  override val transferableIdeId = TransferableIdeId.DummyIde
  override val name: String = "Test"

  override fun isAvailable(): Boolean = true

  val saved = listOf(IdeVersion(transferableIdeId, null, "test23", AllIcons.CodeWithMe.CwmJoin, "Test Instance", "yes", {
    Settings(

      laf = KnownLafs.Light,
      //keymap = BundledKeymap("My cool keymap", "Sublime Text", emptyList(/* fill this with shortcuts samples or action ids */)),
      plugins = mutableListOf(
        PluginFeature(TransferableIdeFeatureId.DummyPlugin, "com.intellij.ideolog", "Ideolog")
      )
    )
  }, Date(), this))

  override fun getIdeVersions(skipIds: List<String>): List<BaseIdeVersion> {
    if (skipIds.isNotEmpty()) return emptyList()
    return saved
  }
}