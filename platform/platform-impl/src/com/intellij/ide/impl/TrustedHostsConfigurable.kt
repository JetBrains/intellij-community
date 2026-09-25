// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.ide.IdeBundle
import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.ide.trustedProjects.TrustedProjectsLocator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.math.max

@ApiStatus.Internal
class TrustedHostsConfigurable : BoundConfigurable(IdeBundle.message("configurable.trusted.hosts.display.name"), TRUSTED_PROJECTS_HELP_TOPIC),
                                 SearchableConfigurable {

  private val EP_NAME = ExtensionPointName.create<TrustedHostsConfigurableProvider>("com.intellij.trustedHostsConfigurableProvider")

  override fun createPanel(): DialogPanel {
    val result = panel {
      row {
        label(IdeBundle.message("trusted.folders.settings.label"))
      }
      row {
        trustedLocationConfigurable(getValuesFromSettings = { getMergedTrustedPaths() },
                                    setValuesToSettings = { applyMergedTrustedPaths(it) },
                                    getNewValueFromUser = {
                                      getPathFromUser(it, FileChooserDescriptorFactory.singleFileOrDir()
                                        .withTitle(IdeBundle.message("trusted.hosts.settings.new.trusted.folder.file.chooser.title")))
                                    })
      }.resizableRow()

      for (additionalPanel in EP_NAME.extensionList) {
        additionalPanel.buildContent(this)
      }
    }
    return result
  }

  /**
   * One list over both trust stores: the user-managed trusted locations ([TrustedPathsSettings])
   * followed by the projects and files explicitly trusted from the confirmation dialogs ([TrustedPaths]).
   */
  private fun getMergedTrustedPaths(): List<String> {
    val settingsPaths = TrustedPathsSettings.getInstance().getTrustedPaths()
    val settingsPathSet = settingsPaths.toSet()
    return settingsPaths + TrustedPaths.getInstance().getExplicitlyTrustedPaths().filter { it !in settingsPathSet }
  }

  /**
   * Entries stay in the store they came from; new entries go to the user-managed trusted locations.
   * The trust events are fired for the difference, so trust caches, editor banners, and open editors refresh
   * the same way as when trust is granted from the confirmation dialogs.
   */
  private fun applyMergedTrustedPaths(paths: List<String>) {
    val trustedPathsSettings = TrustedPathsSettings.getInstance()
    val trustedPaths = TrustedPaths.getInstance()
    val settingsBefore = trustedPathsSettings.getTrustedPaths().toSet()
    val explicitBefore = trustedPaths.getExplicitlyTrustedPaths()
    val explicitBeforeSet = explicitBefore.toSet()
    val afterSet = paths.toSet()

    trustedPathsSettings.setTrustedPaths(paths.filter { it in settingsBefore || it !in explicitBeforeSet })
    trustedPaths.setExplicitlyTrustedPaths(explicitBefore.filter { it in afterSet })

    val beforeSet = buildSet {
      addAll(settingsBefore)
      addAll(explicitBeforeSet)
    }
    val publisher = ApplicationManager.getApplication().messageBus.syncPublisher(TrustedProjectsListener.TOPIC)
    for (path in afterSet.filter { it !in beforeSet }) {
      locate(path)?.let { publisher.onProjectTrusted(it) }
    }
    for (path in beforeSet.filter { it !in afterSet }) {
      locate(path)?.let { publisher.onProjectUntrusted(it) }
    }
  }

  private fun locate(path: String): TrustedProjectsLocator.LocatedProject? {
    val nioPath = try {
      Path.of(path)
    }
    catch (_: InvalidPathException) {
      return null
    }
    return TrustedProjectsLocator.locateProject(nioPath, project = null)
  }

  private fun Row.trustedLocationConfigurable(
                                          getValuesFromSettings: () -> List<String>,
                                          setValuesToSettings: (List<String>) -> Unit,
                                          getNewValueFromUser: (Component) -> String?) {
    val model = CollectionListModel(getValuesFromSettings())
    val list = JBList(model)

    val component = ToolbarDecorator.createDecorator(list)
      .setAddAction {
        val path = getNewValueFromUser(list)
        if (path != null) {
          val insertPosition = if (list.selectedIndex >= 0) list.selectedIndex else max(list.itemsCount - 1, 0)
          model.add(insertPosition, path)
        }
      }
      .setRemoveAction {
        model.remove(list.selectedIndex)
      }
      .createPanel()

    cell(component)
      .align(Align.FILL)
      .onApply {
        setValuesToSettings(model.items)
        // the settings may normalize the values (e.g., keep them sorted)
        model.replaceAll(getValuesFromSettings())
      }.onIsModified {
        getValuesFromSettings() != model.items
      }.onReset {
        model.replaceAll(getValuesFromSettings())
      }
  }

  private fun getPathFromUser(parent: Component, chooserDescriptor: FileChooserDescriptor): String? {
    val pathField = TextFieldWithBrowseButton(null, disposable)
    pathField.textField.columns = Messages.InputDialog.INPUT_DIALOG_COLUMNS
    pathField.addBrowseFolderListener(null, chooserDescriptor)
    val ok = DialogBuilder(parent)
      .title(IdeBundle.message("trusted.hosts.settings.new.trusted.folder.dialog.title"))
      .setNorthPanel(pathField)
      .showAndGet()
    return if (ok) OSAgnosticPathUtil.expandUserHome(pathField.text) else null
  }

  override fun getId(): String {
    return "trusted.hosts"
  }
}

/**
 * Provides additional components to the "Trusted Hosts" configurable in the application settings.
 */
@ApiStatus.Internal
interface TrustedHostsConfigurableProvider {
  fun buildContent(panel: Panel)
}
