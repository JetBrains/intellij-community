// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects.impl

import com.intellij.diagnostic.WindowsDefenderChecker
import com.intellij.diagnostic.WindowsDefenderCheckerActivity
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.OpenUntrustedProjectChoice
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.trustedProjects.impl.TrustedProjectUtil.findAllIndexesOfSymbol
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.util.setKotlinProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ExitActionType
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.plus
import com.intellij.ui.util.width
import com.intellij.util.SystemProperties
import com.intellij.util.ui.JBFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.text.View
import kotlin.io.path.pathString
import kotlin.math.ceil

@ApiStatus.Internal
class TrustedProjectStartupDialog private constructor(
  project: Project?,
  private val projectPath: Path,
  private val pathsToExclude: List<Path>,
  private val projectParentPathToExclude: Path?,
  private val myTitle: @NlsContexts.DialogTitle String,
  private val message: @NlsContexts.DialogMessage String,
  private val trustButtonText: @NlsContexts.Button String,
  private val distrustButtonText: @NlsContexts.Button String,
  private val cancelButtonText: @NlsContexts.Button String,
) : TrustAlertDialog(project) {
  private val windowsDefender = AtomicBooleanProperty(pathsToExclude.isNotEmpty())
  private val trustAll = AtomicBooleanProperty(false)
  private var windowsDefenderCheckBox: Cell<JBCheckBox>? = null
  private var userChoice: OpenUntrustedProjectChoice = OpenUntrustedProjectChoice.CANCEL
  private var trustAction: Action? = null

  init {
    title = myTitle
    installAlertChrome()
  }

  override fun createCenterPanel(): JComponent? {
    return panel {
      row {
        icon(AllIcons.General.WarningDialog).align(AlignY.TOP)
        panel {
          row {
            @Suppress("DialogTitleCapitalization")
            text(myTitle).apply {
              component.font = JBFont.h4()
              component.isFocusable = true // Workaround for IJPL-184339
            }
          }
          row {
            text(message).apply {
              component.isFocusable = true // Workaround for IJPL-184339
            }
          }
          projectPath.parent?.takeIf { TrustedProjects.isProjectLocationOfferedForTrust(projectPath) }?.let { projectParentPath ->
            row {
              val parentDirName = NioFiles.getFileName(projectParentPath)
              val trimmedParentDirName = StringUtil.shortenTextWithEllipsis(parentDirName, 40, 0, true)
              val truncatedParentDirName = StringUtil.shortenTextWithEllipsis(parentDirName, 18, 0, true)
              checkBox(IdeBundle.message("untrusted.project.warning.trust.location.checkbox", trimmedParentDirName))
                .bindSelected(trustAll)
                .apply {
                  component.toolTipText = null
                  component.addMouseMotionListener(TooltipMouseAdapter { listOf(projectParentPath.pathString) })
                }
                .onChanged {
                  if (it.isSelected) {
                    windowsDefender.set(false)
                  }
                  if (trustAction != null) {
                    val trustButton = getButton(trustAction!!)
                    val text = when {
                      it.isSelected -> IdeBundle.message("untrusted.project.dialog.trust.folder.button", truncatedParentDirName)
                      else -> trustButtonText
                    }
                    trustButton?.text = text
                  }
                  windowsDefenderCheckBox?.let { cb ->
                    val trimmedFolderName = StringUtil.shortenTextWithEllipsis(NioFiles.getFileName(getDefenderTrustFolder(it.isSelected)), 18, 0, true)
                    cb.component.text = IdeBundle.message("untrusted.project.windows.defender.trust.location.checkbox", trimmedFolderName)
                  }
                }
            }
          }
          if (pathsToExclude.isNotEmpty()) {
            row {
              val trimmedFolderName = StringUtil.shortenTextWithEllipsis(NioFiles.getFileName(projectPath), 18, 0, true)
              val idePaths = pathsToExclude.asSequence().filter { it != projectPath }.joinToString(separator = "<br>")
              windowsDefenderCheckBox = checkBox(IdeBundle.message("untrusted.project.windows.defender.trust.location.checkbox", trimmedFolderName))
                .bindSelected(windowsDefender)
                .apply {
                  component.toolTipText = null
                  component.addMouseMotionListener(TooltipMouseAdapter { listOf(idePaths, getDefenderTrustFolder(isTrustAll()).pathString) })
                  comment(IdeBundle.message("untrusted.project.location.comment"))
                }
            }
          }
        }.align(AlignX.FILL + AlignY.FILL)
      }
    }.withMinimumWidth(600).withPreferredWidth(600)
  }

  private class TooltipMouseAdapter(val orderedPaths: () -> List<String>) : MouseAdapter() {
    override fun mouseMoved(e: MouseEvent) {
      val checkBox = e.source as? JBCheckBox ?: return
      val position = e.point
      val textWithMarkedElements = checkBox.text.removePrefix("<html>").replace("'", "").replace("<b>", "'").replace("</b>", "'")
      val htmlDocument = (checkBox.getClientProperty("html") as? View)?.document

      val text = htmlDocument?.getText(0, htmlDocument.length)?.replace("\n", "") ?: textWithMarkedElements
      val fontMetrics = checkBox.getFontMetrics(checkBox.font)
      val bounds = fontMetrics.getStringBounds(text, @Suppress("RawComponentGraphics") checkBox.graphics)
      val x = checkBox.width - bounds.width - checkBox.insets.width
      bounds.setRect(x + bounds.x, bounds.y, bounds.width, bounds.height)
      val mousePosition = position.x - x
      if (mousePosition < 0) {
        checkBox.toolTipText = null
        return
      }
      val quotePositions = findAllIndexesOfSymbol(textWithMarkedElements, '\'')
      // Estimate the character position based on mouse x-coordinate relative to bounds
      val positionX = ceil(mousePosition / (bounds.width / text.length)).toInt().coerceIn(0, text.length - 1)

      val paths = orderedPaths()
      for (pathInd in paths.indices) {
        val firstQuotesInd = pathInd * 2
        val secondQuotesInd = pathInd * 2 + 1
        if (quotePositions[firstQuotesInd] <= positionX && positionX <= quotePositions[secondQuotesInd]) {
          @Suppress("HardCodedStringLiteral", "UseHtmlChunkToolTip")
          checkBox.toolTipText = paths.getOrNull(pathInd)
          return
        }
      }
      checkBox.toolTipText = null
    }
  }

  /* This is a workaround for ij-light which does not know what to do when user clicks the "cancel" button. (IJPL-245778)
   * The problem is that ij-light can transform from Local IDE to RD client, but cannot do it backwards. Use case is the following:
   * user opens remote project as a remote folder - they can see files, edit them, but there is no language support and the project is not
   * imported. What they see is a plain folder and files accessed via eel. Then user decides to continue in the full-remdev mode. They
   * click "enter smart mode" button. IDE installs backend to remote host, launches it, connects to it and then opens the project.
   * Project open triggers import and external system shows this trusted project dialog. For now, we can only continue in "trusted" or
   * "untrusted" mode. We cannot cancel this operation, because this would require to disconnect from the IDE and revert all the upgrades
   * we've made so far (= uninstall RD-support plugin). This scenario is not supported, therefore we want to disable "cancel" option at all.
   * User must choose how they want to proceed: in trusted or untrusted mode, but there is no way back to "light" mode.
   */
  private fun hasCancelButton(): Boolean = SystemProperties.getBooleanProperty(TRUSTED_PROJECT_DIALOG_HAS_CANCEL_BUTTON_KEY, true)

  override fun createCancelAction(): ActionListener? = if (hasCancelButton()) super.createCancelAction() else null

  override fun shouldCloseOnCross(): Boolean = hasCancelButton()

  override fun createActions(): Array<out Action?> {
    val trustAction = alertAction(trustButtonText, isDefault = true) {
      userChoice = OpenUntrustedProjectChoice.TRUST_AND_OPEN
      close(0, true, ExitActionType.YES)
    }
    this.trustAction = trustAction
    val actions = mutableListOf(
      trustAction,
      alertAction(distrustButtonText, isFocused = true) {
        userChoice = OpenUntrustedProjectChoice.OPEN_IN_SAFE_MODE
        close(1, false, ExitActionType.NO)
      },
    )
    if (hasCancelButton()) {
      actions += alertAction(cancelButtonText) {
        userChoice = OpenUntrustedProjectChoice.CANCEL
        close(2, false, ExitActionType.CANCEL)
      }
    }
    actions += helpAction
    return actions.toTypedArray()
  }

  private fun getOpenChoice(): OpenUntrustedProjectChoice = userChoice

  private fun isTrustAll(): Boolean = trustAll.get()

  private fun getDefenderTrustFolder(trustParent: Boolean): Path = projectParentPathToExclude?.takeIf { trustParent } ?: projectPath

  private fun getDefenderExcludePaths(): List<Path> {
    if (!windowsDefender.get()) {
      return emptyList()
    }
    if (isTrustAll()) {
      val defenderTrustDir = getDefenderTrustFolder(isTrustAll())
      if (defenderTrustDir != projectPath) {
        return pathsToExclude.toMutableList().apply {
          remove(projectPath)
          add(0, defenderTrustDir)
        }
      }
    }
    return pathsToExclude
  }

  @TestOnly
  fun getButtonTextsInTests(): Set<String> = buttonMap.values.mapTo(linkedSetOf()) { it.text }

  @TestOnly
  fun hasImplicitCancelActionInTests(): Boolean = createCancelAction() != null

  class DialogChoice(
    val openChoice: OpenUntrustedProjectChoice,
    val isTrustAll: Boolean,
    val defenderExcludePaths: List<Path>
  )

  companion object {
    @VisibleForTesting
    const val TRUSTED_PROJECT_DIALOG_HAS_CANCEL_BUTTON_KEY: String = "trusted.project.dialog.has.cancel.button"

    private var ourDialogChoice: DialogChoice? = null

    @TestOnly
    fun setDialogChoiceInTests(openChoice: OpenUntrustedProjectChoice, disposable: Disposable) {
      val choice = DialogChoice(openChoice, false, emptyList())
      setKotlinProperty(::ourDialogChoice, choice, disposable)
    }

    @TestOnly
    fun createDialogInTests(
      project: Project?,
      projectPath: Path,
      title: @NlsContexts.DialogTitle String,
      message: @NlsContexts.DialogMessage String,
      trustButtonText: @NlsContexts.Button String,
      distrustButtonText: @NlsContexts.Button String,
      cancelButtonText: @NlsContexts.Button String,
    ): TrustedProjectStartupDialog = TrustedProjectStartupDialog(
      project, projectPath, emptyList(), null, title, message, trustButtonText, distrustButtonText, cancelButtonText
    )

    suspend fun showAndGet(
      project: Project?,
      projectPath: Path,
      title: @NlsContexts.DialogTitle String,
      message: @NlsContexts.DialogMessage String,
      trustButtonText: @NlsContexts.Button String,
      distrustButtonText: @NlsContexts.Button String,
      cancelButtonText: @NlsContexts.Button String,
    ): DialogChoice {
      val (pathsToExclude, projectParentPathToExclude) = getDefenderExcludePaths(project, projectPath)
      return ourDialogChoice ?: withContext(Dispatchers.EDT) {
        val dialog = TrustedProjectStartupDialog(
          project, projectPath, pathsToExclude, projectParentPathToExclude,
          title, message, trustButtonText, distrustButtonText, cancelButtonText
        )
        dialog.show()
        DialogChoice(dialog.getOpenChoice(), dialog.isTrustAll(), dialog.getDefenderExcludePaths())
      }
    }

    private suspend fun getDefenderExcludePaths(project: Project?, projectPath: Path): Pair<List<Path>, Path?> {
      if (WindowsDefenderCheckerActivity.isLocalWindowsPath(projectPath)) {
        val checker = serviceAsync<WindowsDefenderChecker>()
        if (
          !checker.isUntrustworthyLocation(projectPath) &&
          !checker.isStatusCheckIgnored(project) &&
          checker.isRealTimeProtectionEnabled == true
        ) {
          val paths = checker.filterDevDrivePaths(checker.getPathsToExclude(project, projectPath)).toMutableList()
          if (projectPath in paths) {
            val projectParentPath = projectPath.parent?.takeIf { !checker.isUntrustworthyLocation(it) }
            return Pair(paths, projectParentPath)
          }
        }
      }
      return Pair(emptyList(), null)
    }
  }
}
