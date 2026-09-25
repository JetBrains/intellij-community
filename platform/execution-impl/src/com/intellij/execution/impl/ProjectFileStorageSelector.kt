// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.execution.ExecutionBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.CompositeShortcutSet
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.BrowseFolderRunnable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.ui.LayeredIcon
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.popup.PopupState
import com.intellij.util.PathUtil
import com.intellij.util.ThreeState
import com.intellij.util.UriUtil
import com.intellij.util.concurrency.NonUrgentExecutor
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ThreeStateCheckBox
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.event.ActionListener
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.SwingConstants
import javax.swing.text.JTextComponent

@ApiStatus.Internal
abstract class ProjectFileStorageSelector @JvmOverloads constructor(
  private val project: Project,
  private val pathLabel: @NlsContexts.Label String,
  supportsMixedState: Boolean = false,
) {
  private val storeAsFileCheckBox: JCheckBox = if (supportsMixedState) {
    ThreeStateCheckBox(ExecutionBundle.message("run.configuration.store.as.project.file"), ThreeStateCheckBox.State.NOT_SELECTED)
  }
  else JBCheckBox(ExecutionBundle.message("run.configuration.store.as.project.file"))
  private val storeAsFileGearButton = createStoreAsFileGearButton()

  init {
    storeAsFileCheckBox.addActionListener {
      if (storeAsFileCheckBox is ThreeStateCheckBox) storeAsFileCheckBox.isThirdStateEnabled = false
      onStorageSelectionChanged(storeAsFileCheckBox.isSelected)
      storeAsFileGearButton.isEnabled = storeAsFileCheckBox.isSelected
      if (storeAsFileCheckBox.isSelected) {
        manageStorageFileLocation(null)
      }
    }
  }

  protected abstract fun onStorageSelectionChanged(selected: Boolean)
  protected abstract fun onStoragePathChanged(path: String)
  protected abstract fun getStoragePath(): String
  protected abstract fun getSuggestedPaths(path: String): Collection<String>
  protected abstract fun getPathError(path: String): @NlsContexts.DialogMessage String?
  protected abstract fun createPathChooserDescriptor(): FileChooserDescriptor

  protected open fun isPathInvalid(): Boolean = getPathError(getStoragePath()) != null
  protected open fun getStoragePathComment(): @NlsContexts.DetailedDescription String? = null

  open fun createComponent(): JPanel = FormBuilder.createFormBuilder().setFormLeftIndent(10).setHorizontalGap(0)
    .addLabeledComponent(storeAsFileCheckBox, storeAsFileGearButton)
    .panel

  fun createToolbarComponent(): JPanel {
    storeAsFileCheckBox.isOpaque = false
    storeAsFileCheckBox.border = JBUI.Borders.emptyRight(3)
    storeAsFileGearButton.isFocusable = true
    return JPanel(HorizontalLayout(0, SwingConstants.CENTER)).apply {
      isOpaque = false
      border = JBUI.Borders.empty(0, 10, 0, 2)
      add(storeAsFileCheckBox)
      add(storeAsFileGearButton)
    }
  }

  val focusOrder: List<JComponent> get() = listOf(storeAsFileCheckBox, storeAsFileGearButton)

  open fun addStoreAsFileCheckBoxListener(listener: ActionListener) {
    storeAsFileCheckBox.addActionListener(listener)
  }

  protected fun resetStorageUi(selected: Boolean?, enabled: Boolean) {
    storeAsFileCheckBox.isEnabled = enabled
    if (storeAsFileCheckBox is ThreeStateCheckBox) {
      storeAsFileCheckBox.isThirdStateEnabled = selected == null
      storeAsFileCheckBox.state = when (selected) {
        true -> ThreeStateCheckBox.State.SELECTED
        false -> ThreeStateCheckBox.State.NOT_SELECTED
        null -> ThreeStateCheckBox.State.DONT_CARE
      }
    }
    else {
      storeAsFileCheckBox.isSelected = selected == true
    }
    storeAsFileGearButton.isVisible = enabled
    storeAsFileGearButton.isEnabled = storeAsFileCheckBox.isSelected
    validatePath()
  }

  private fun createStoreAsFileGearButton(): ActionButton {
    val state = PopupState.forBalloon()
    val showStoragePathAction = object : DumbAwareAction() {
      override fun actionPerformed(e: AnActionEvent) {
        if (!state.isRecentlyHidden) manageStorageFileLocation(state)
      }
    }
    val presentation = Presentation(ExecutionBundle.message("run.configuration.manage.file.location"))
    presentation.icon = GEAR_WITH_DROPDOWN_ICON
    presentation.disabledIcon = GEAR_WITH_DROPDOWN_DISABLED_ICON
    return ActionButton(showStoragePathAction, presentation, ActionPlaces.TOOLBAR, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
  }

  @Suppress("SplitModeApiUsage") // TODO: RIDER-143378
  private fun manageStorageFileLocation(state: PopupState<Balloon>?) {
    val balloonDisposable = Disposer.newDisposable()
    val popup = StoragePathPopup(
      project, pathLabel, getStoragePathComment(), createPathChooserDescriptor(), ::getPathError, balloonDisposable,
    )
    val balloon = JBPopupFactory.getInstance().createBalloonBuilder(popup.mainPanel)
      .setDialogMode(true)
      .setBorderInsets(JBUI.insets(20, 15, 10, 15))
      .setFillColor(UIUtil.getPanelBackground())
      .setHideOnAction(false)
      .setHideOnLinkClick(false)
      .setHideOnKeyOutside(false) // otherwise any keypress in file chooser hides the underlying balloon
      .setBlockClicksThroughBalloon(true)
      .setRequestFocus(true)
      .createBalloon()
    balloon.setAnimationEnabled(false)

    val path = getStoragePath()
    popup.reset(path, getSuggestedPaths(path)) { balloon.hide() }
    balloon.addListener(object : JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        Disposer.dispose(balloonDisposable)
        val newPath = popup.getPath()
        if (newPath != path) {
          onStoragePathChanged(newPath)
        }
      }
    })

    state?.prepareToShow(balloon)
    balloon.show(RelativePoint.getSouthOf(storeAsFileCheckBox), Balloon.Position.below)
  }

  protected fun validatePath() {
    ReadAction.nonBlocking<Icon> {
      if (storeAsFileCheckBox.isSelected && isPathInvalid()) GEAR_WITH_DROPDOWN_ERROR_ICON else GEAR_WITH_DROPDOWN_ICON
    }
      .expireWhen { !storeAsFileGearButton.isShowing }
      .finishOnUiThread(ModalityState.defaultModalityState(), storeAsFileGearButton::setIcon)
      .submit(NonUrgentExecutor.getInstance())
  }

  companion object {
    private val GEAR_WITH_DROPDOWN_ICON = LayeredIcon.layeredIcon { arrayOf(AllIcons.General.GearPlain, AllIcons.General.Dropdown) }
    private val GEAR_WITH_DROPDOWN_DISABLED_ICON = LayeredIcon.layeredIcon {
      arrayOf(IconLoader.getDisabledIcon(AllIcons.General.GearPlain), IconLoader.getDisabledIcon(AllIcons.General.Dropdown))
    }
    private val GEAR_WITH_DROPDOWN_ERROR_ICON = LayeredIcon.layeredIcon { arrayOf(AllIcons.General.Error, AllIcons.General.Dropdown) }

    @JvmStatic
    fun getErrorIfBadFolderPath(
      project: Project,
      path: String?,
      allowedStoragePath: String?,
      dotIdeaError: @NlsContexts.DialogMessage String,
    ): @NlsContexts.DialogMessage String? {
      if (allowedStoragePath != null && allowedStoragePath == path) return null // that's ok
      if (path.isNullOrEmpty()) return ExecutionBundle.message("run.configuration.storage.folder.path.not.specified")
      if (path.endsWith("/.idea") || path.contains("/.idea/")) return dotIdeaError

      var file = StandardFileSystems.local().findFileByPath(path)
      if (file != null && !file.isDirectory) return ExecutionBundle.message("run.configuration.storage.folder.path.expected")

      var folderName = PathUtil.getFileName(path)
      var parentPath = PathUtil.getParentPath(path)
      while (file == null && parentPath.isNotEmpty()) {
        if (!PathUtil.isValidFileName(folderName)) {
          return ExecutionBundle.message("run.configuration.storage.folder.path.expected")
        }
        file = StandardFileSystems.local().findFileByPath(parentPath)
        folderName = PathUtil.getFileName(parentPath)
        parentPath = PathUtil.getParentPath(parentPath)
      }

      if (file == null) return ExecutionBundle.message("run.configuration.storage.folder.not.within.project")
      if (!file.isDirectory) return ExecutionBundle.message("run.configuration.storage.folder.path.expected")

      val isInContent = WorkspaceFileIndex.getInstance(project).isUrlInContent(VfsUtilCore.pathToUrl(path)) != ThreeState.NO
      if (!isInContent) {
        if (WorkspaceFileIndex.getInstance(project).getContentFileSetRoot(file, false) == null) {
          return ExecutionBundle.message("run.configuration.storage.folder.not.within.project")
        }
        else {
          return ExecutionBundle.message("run.configuration.storage.folder.in.excluded.root")
        }
      }
      return null // ok
    }
  }
}

private class StoragePathPopup(
  project: Project,
  pathLabel: @NlsContexts.Label String,
  pathComment: @NlsContexts.DetailedDescription String?,
  descriptor: FileChooserDescriptor,
  pathToErrorMessage: (String) -> @NlsContexts.DialogMessage String?,
  uiDisposable: Disposable,
) {
  private val pathComboBox = ComboBox<String>(JBUI.scale(500)).apply {
    isEditable = true
    val selectPathAction = BrowseFolderRunnable(project, descriptor, this, TextComponentAccessor.STRING_COMBOBOX_WHOLE_TEXT)
    initBrowsableEditor(selectPathAction, uiDisposable)
  }
  private lateinit var closePopupAction: () -> Unit
  val mainPanel: JPanel

  init {
    val validator = ComponentValidator(uiDisposable)
    val comboBoxEditorComponent = pathComboBox.editor.editorComponent as JTextComponent
    validator.withValidator {
      pathToErrorMessage(getPath())?.let { ValidationInfo(it, pathComboBox) }
    }
      .andRegisterOnDocumentListener(comboBoxEditorComponent)
      .installOn(comboBoxEditorComponent)

    val comboBoxPanel = UI.PanelFactory.panel(pathComboBox).withLabel(pathLabel).moveLabelOnTop().apply {
      if (pathComment != null) withComment(StringUtil.escapeXmlEntities(pathComment))
    }.createPanel()
    val doneButton = JButton(ExecutionBundle.message("run.configuration.done.button"))
    doneButton.addActionListener { closePopupAction() }
    val doneButtonPanel = JPanel(BorderLayout())
    doneButtonPanel.add(doneButton, BorderLayout.EAST)

    mainPanel = FormBuilder.createFormBuilder().addComponent(comboBoxPanel).addComponent(doneButtonPanel).panel
    mainPanel.isFocusCycleRoot = true
    mainPanel.focusTraversalPolicy = LayoutFocusTraversalPolicy()

    // need to handle Enter keypress, otherwise Enter closes the main Run Configurations dialog.
    // Escape should also be handled manually because setHideOnKeyOutside(false) is set for this balloon.
    DumbAwareAction.create {
      if (pathComboBox.isPopupVisible) {
        pathComboBox.isPopupVisible = false
      }
      else {
        validator.updateInfo(null)
        closePopupAction()
      }
    }.registerCustomShortcutSet(CompositeShortcutSet(CommonShortcuts.ENTER, CommonShortcuts.ESCAPE), mainPanel, uiDisposable)
  }

  fun reset(path: String, pathsToSuggest: Collection<String>, closePopupAction: () -> Unit) {
    pathComboBox.selectedItem = FileUtil.toSystemDependentName(path)
    for (suggestion in pathsToSuggest) {
      pathComboBox.addItem(FileUtil.toSystemDependentName(suggestion))
    }
    this.closePopupAction = closePopupAction
  }

  fun getPath(): String =
    UriUtil.trimTrailingSlashes(FileUtil.toSystemIndependentName(pathComboBox.editor.item.toString().trim { it <= ' ' }))
}
