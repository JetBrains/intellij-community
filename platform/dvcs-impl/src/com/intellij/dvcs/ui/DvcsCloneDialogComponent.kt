// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ui

import com.intellij.dvcs.DvcsRememberedInputs
import com.intellij.dvcs.repo.ClonePathProvider
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.ui.VcsCloneComponent
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.*
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

abstract class DvcsCloneDialogComponent(var project: Project,
                                        private var vcsDirectoryName: String,
                                        rememberedInputs: DvcsRememberedInputs) : VcsCloneComponent {
  private val mainPanel: JPanel
  private val urlEditor = JBTextField()
  private val directoryField = SelectChildTextFieldWithBrowseButton(ClonePathProvider.defaultParentDirectoryPath(project, rememberedInputs))

  init {
    val fcd = FileChooserDescriptorFactory.createSingleFolderDescriptor()
    fcd.isShowFileSystemRoots = true
    fcd.isHideIgnored = false
    directoryField.addBrowseFolderListener(DvcsBundle.getString("clone.destination.directory.browser.title"),
                                           DvcsBundle.getString("clone.destination.directory.browser.description"),
                                           project,
                                           fcd)
    mainPanel = panel {
      row("URL:") { urlEditor(growX) }
      row("Directory:") { directoryField(growX) }
    }
    val insets = UIUtil.PANEL_REGULAR_INSETS
    mainPanel.border = JBEmptyBorder(insets.top / 2, insets.left, insets.bottom, insets.right)

    urlEditor.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        directoryField.trySetChildPath(defaultDirectoryPath(urlEditor.text.trim()))
      }
    })
  }

  override fun getPreferredFocusedComponent(): JComponent = urlEditor

  private fun defaultDirectoryPath(url: String): String {
    return StringUtil.trimEnd(ClonePathProvider.relativeDirectoryPathForVcsUrl(project, url), vcsDirectoryName)
  }

  override fun getView() = mainPanel

  override fun isOkEnabled(): Boolean {
    return false
  }

  override fun doValidateAll(): List<ValidationInfo> {
    val list = ArrayList<ValidationInfo>()
    ContainerUtil.addIfNotNull(list, CloneDvcsValidationUtils.checkDirectory(directoryField.text, directoryField.textField))
    ContainerUtil.addIfNotNull(list, CloneDvcsValidationUtils.checkRepositoryURL(urlEditor, urlEditor.text.trim()))
    ContainerUtil.addIfNotNull(list, CloneDvcsValidationUtils.createDestination(directoryField.text))
    return list
  }

  abstract override fun doClone(project: Project, listener: CheckoutProvider.Listener)

  fun getDirectory() = directoryField.text
  fun getUrl() = urlEditor.text

  override fun dispose() {}
}