// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ui

import com.intellij.dvcs.DvcsRememberedInputs
import com.intellij.dvcs.repo.ClonePathProvider
import com.intellij.dvcs.ui.CloneDvcsValidationUtils.sanitizeCloneUrl
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.ui.VcsCloneComponent
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.TextFieldWithHistory
import com.intellij.ui.layout.*
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

abstract class DvcsCloneDialogComponent(var project: Project,
                                        private var vcsDirectoryName: String,
                                        protected val rememberedInputs: DvcsRememberedInputs) : VcsCloneComponent {
  protected val mainPanel: JPanel
  private val urlEditor = TextFieldWithHistory()
  private val directoryField = SelectChildTextFieldWithBrowseButton(
    ClonePathProvider.defaultParentDirectoryPath(project, rememberedInputs))

  protected lateinit var errorComponent: BorderLayoutPanel

  init {
    val fcd = FileChooserDescriptorFactory.createSingleFolderDescriptor()
    fcd.isShowFileSystemRoots = true
    fcd.isHideIgnored = false
    directoryField.addBrowseFolderListener(DvcsBundle.getString("clone.destination.directory.browser.title"),
                                           DvcsBundle.getString("clone.destination.directory.browser.description"),
                                           project,
                                           fcd)
    mainPanel = panel {
      row(VcsBundle.getString("vcs.common.labels.url")) { urlEditor(growX) }
      row(VcsBundle.getString("vcs.common.labels.directory")) { directoryField(growX) }
        .largeGapAfter()
      row {
        errorComponent = BorderLayoutPanel(UIUtil.DEFAULT_HGAP, 0)
        errorComponent()
      }
    }

    val insets = UIUtil.PANEL_REGULAR_INSETS
    mainPanel.border = JBEmptyBorder(insets.top / 2, insets.left, insets.bottom, insets.right)

    urlEditor.history = rememberedInputs.visitedUrls
    urlEditor.addDocumentListener(object : DocumentAdapter() {
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
    return list
  }

  abstract override fun doClone(project: Project, listener: CheckoutProvider.Listener)

  fun getDirectory(): String = directoryField.text.trim()

  fun getUrl(): String = sanitizeCloneUrl(urlEditor.text)

  override fun dispose() {}

}