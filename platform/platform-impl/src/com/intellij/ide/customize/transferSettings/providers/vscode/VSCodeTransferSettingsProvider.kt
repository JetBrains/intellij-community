package com.intellij.ide.customize.transferSettings.providers.vscode

import com.intellij.icons.AllIcons
import com.intellij.ide.customize.transferSettings.models.IdeVersion
import com.intellij.ide.customize.transferSettings.providers.DefaultImportPerformer
import com.intellij.ide.customize.transferSettings.providers.TransferSettingsProvider
import com.intellij.ide.customize.transferSettings.providers.vscode.VSCodeSettingsProcessor.Companion.vsCodeHome
import com.intellij.util.SmartList
import java.nio.file.Files
import java.nio.file.Paths

class VSCodeTransferSettingsProvider : TransferSettingsProvider {
  private val processor = VSCodeSettingsProcessor()
  override val name: String
    get() = "Visual Studio Code"

  override fun isAvailable() = true

  override fun getIdeVersions(skipIds: List<String>) = when (isVSCodeDetected()) {
    true -> SmartList(getIdeVersion())
    false -> SmartList()
  }

  override fun getImportPerformer(ideVersion: IdeVersion) = DefaultImportPerformer()

  private fun getIdeVersion(): IdeVersion {
    return IdeVersion("VSCode", AllIcons.Idea_logo_welcome, "Visual Studio Code", settings = processor.getProcessedSettings(),
                      provider = this)
  }

  private fun isVSCodeDetected() = Files.isDirectory(Paths.get(vsCodeHome)) && processor.willDetectAtLeastSomething()
}