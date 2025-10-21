// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.ui

import com.intellij.dvcs.DvcsRememberedInputs
import com.intellij.dvcs.repo.ClonePathProvider
import com.intellij.dvcs.ui.CloneDvcsValidationUtils.sanitizeCloneUrl
import com.intellij.dvcs.ui.DvcsBundle.message
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.ui.VcsCloneComponent
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogComponentStateListener
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.TextFieldWithHistory
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

abstract class DvcsCloneDialogComponent @ApiStatus.Internal constructor(
  var project: Project,
  private var vcsDirectoryName: String,
  protected val rememberedInputs: DvcsRememberedInputs,
  private val dialogStateListener: VcsCloneDialogComponentStateListener,
  @ApiStatus.Internal
  protected val mainPanelCustomizer: MainPanelCustomizer?,
) : VcsCloneComponent, VcsCloneComponent.WithSettableUrl {
  protected val mainPanel: DialogPanel
  private val urlEditor = TextFieldWithHistory()
  private val directoryField = TextFieldWithBrowseButton()
  private val cloneDirectoryChildHandle = FilePathDocumentChildPathHandle
    .install(directoryField.textField.document, ClonePathProvider.defaultParentDirectoryPath(project, rememberedInputs))

  protected lateinit var errorComponent: BorderLayoutPanel

  constructor(
    project: Project,
    vcsDirectoryName: String,
    rememberedInputs: DvcsRememberedInputs,
    dialogStateListener: VcsCloneDialogComponentStateListener,
  ): this(project, vcsDirectoryName, rememberedInputs, dialogStateListener, null)

  init {
    directoryField.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor()
      .withTitle(message("clone.destination.directory.browser.title"))
      .withDescription(message("clone.destination.directory.browser.description"))
      .withShowFileSystemRoots(true)
      .withHideIgnored(false))
    mainPanel = panel {
      row(VcsBundle.message("vcs.common.labels.url")) {
        cell(urlEditor).align(AlignX.FILL).validationOnApply {
          CloneDvcsValidationUtils.checkRepositoryURL(it, it.text.trim())
        }
      }
      row(VcsBundle.message("vcs.common.labels.directory")) {
        cell(directoryField).align(AlignX.FILL).validationOnApply {
          checkDirectory(it.text, it.textField, dialogStateListener)
        }
      }.bottomGap(BottomGap.SMALL)
      mainPanelCustomizer?.configure(this)
      row {
        errorComponent = BorderLayoutPanel(UIUtil.DEFAULT_HGAP, 0)
        cell(errorComponent).align(AlignX.FILL)
      }
    }
    mainPanel.registerValidators(this)

    val insets = UIUtil.PANEL_REGULAR_INSETS
    mainPanel.border = JBEmptyBorder(insets.top / 2, insets.left, insets.bottom, insets.right)

    urlEditor.history = rememberedInputs.visitedUrls
    urlEditor.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        cloneDirectoryChildHandle.trySetChildPath(defaultDirectoryPath(urlEditor.text.trim()))
        updateOkActionState(dialogStateListener)
      }
    })
  }

  open fun checkDirectory(directoryPath: String, component: JComponent, dialogStateListener: VcsCloneDialogComponentStateListener): ValidationInfo? {
    return CloneDvcsValidationUtils.checkDirectory(directoryPath, component)
  }

  override fun getPreferredFocusedComponent(): JComponent = urlEditor

  private fun defaultDirectoryPath(url: String): String {
    return StringUtil.trimEnd(ClonePathProvider.relativeDirectoryPathForVcsUrl(project, url), vcsDirectoryName)
  }

  override fun getView(): JPanel = mainPanel

  override fun isOkEnabled(): Boolean {
    return false
  }

  override fun doValidateAll(): List<ValidationInfo> {
    return mainPanel.validateAll()
  }

  abstract override fun doClone(listener: CheckoutProvider.Listener)

  fun getDirectory(): String = directoryField.text.trim()

  override fun setUrl(url: String) {
    urlEditor.text = url
  }

  fun getUrl(): String = sanitizeCloneUrl(urlEditor.text)

  override fun dispose() {}

  @RequiresEdt
  protected open fun isOkActionEnabled(): Boolean = getUrl().isNotBlank()

  @RequiresEdt
  protected fun updateOkActionState(dialogStateListener: VcsCloneDialogComponentStateListener) {
    dialogStateListener.onOkActionEnabled(isOkActionEnabled())
  }

  @ApiStatus.Internal
  abstract class MainPanelCustomizer {
    abstract fun configure(panel: Panel)
  }
}
