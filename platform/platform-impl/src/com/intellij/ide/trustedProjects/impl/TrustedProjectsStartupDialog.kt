// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects.impl

import com.intellij.diagnostic.WindowsDefenderChecker
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.OpenUntrustedProjectChoice
import com.intellij.ide.impl.TRUSTED_PROJECTS_HELP_TOPIC
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.trustedProjects.impl.TrustedProjectUtil.findAllIndexesOfSymbol
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.setKotlinProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ExitActionType
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.MouseDragHelper
import com.intellij.ui.PopupBorder
import com.intellij.ui.WindowRoundedCornersManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.plus
import com.intellij.ui.util.width
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRootPane
import javax.swing.SwingUtilities
import javax.swing.border.Border
import javax.swing.text.View
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.math.ceil

@ApiStatus.Internal
class TrustedProjectStartupDialog private constructor(
  project: Project?,
  private val projectPath: Path,
  private val pathsToExclude: List<Path>,
  private val myTitle: @NlsContexts.DialogTitle String,
  private val message: @NlsContexts.DialogMessage String,
  private val trustButtonText: @NlsContexts.Button String,
  private val distrustButtonText: @NlsContexts.Button String,
  private val cancelButtonText: @NlsContexts.Button String,
) : DialogWrapper(project) {
  private val myDefaultOptionIndex = 0
  private val myFocusedOptionIndex = 1
  private val propGraph = PropertyGraph("Trust project dialog")
  private val windowsDefender = propGraph.property(pathsToExclude.isNotEmpty())
  private val trustAll = propGraph.property(false)
  private var windowsDefenderCheckBox: Cell<JBCheckBox>? = null
  private var userChoice: OpenUntrustedProjectChoice = OpenUntrustedProjectChoice.CANCEL
  private val myIsTitleComponent = SystemInfoRt.isMac || !Registry.`is`("ide.message.dialogs.as.swing.alert.show.title.bar", false)
  private var trustAction: Action? = null

  init {
    if (SystemInfoRt.isMac) {
      setInitialLocationCallback {
        val rootPane: JRootPane? = SwingUtilities.getRootPane(window.parent) ?: SwingUtilities.getRootPane(window.owner)
        if (rootPane == null || !rootPane.isShowing) {
          return@setInitialLocationCallback null
        }
        val location = rootPane.locationOnScreen
        Point(location.x + (rootPane.width - window.width) / 2, (location.y + rootPane.height * 0.25).toInt())
      }
    }

    init()

    if (myIsTitleComponent) {
      setUndecorated(true)
      rootPane.windowDecorationStyle = JRootPane.NONE
      rootPane.border = PopupBorder.Factory.create(true, true)

      object : MouseDragHelper<JComponent>(myDisposable, contentPane as JComponent) {
        var myLocation: Point? = null

        override fun canStartDragging(dragComponent: JComponent, dragComponentPoint: Point): Boolean =
          dragComponent.findComponentAt(dragComponentPoint).let { target -> target == null || target == dragComponent || target is JPanel }

        override fun processDrag(event: MouseEvent, dragToScreenPoint: Point, startScreenPoint: Point) {
          if (myLocation == null) {
            myLocation = window.location
          }
          window.location = Point(
            myLocation!!.x + dragToScreenPoint.x - startScreenPoint.x,
            myLocation!!.y + dragToScreenPoint.y - startScreenPoint.y
          )
        }

        override fun processDragCancel() {
          myLocation = null
        }

        override fun processDragFinish(event: MouseEvent, willDragOutStart: Boolean) {
          myLocation = null
        }

        override fun processDragOutFinish(event: MouseEvent) {
          myLocation = null
        }

        override fun processDragOutCancel() {
          myLocation = null
        }

        override fun processDragOut(event: MouseEvent, dragToScreenPoint: Point, startScreenPoint: Point, justStarted: Boolean) {
          super.processDragOut(event, dragToScreenPoint, startScreenPoint, justStarted)
          myLocation = null
        }
      }.start()
    }

    WindowRoundedCornersManager.configure(this)
  }

  override fun createContentPaneBorder(): Border {
    val insets = JButton().insets
    return JBUI.Borders.empty(if (myIsTitleComponent) 20 else 14, 20, 20 - insets.bottom, 20 - insets.right)
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
          if (getParentFolder() != null) {
            row {
              val trimmedFolderName = StringUtil.shortenTextWithEllipsis(getParentFolder()!!.name, 40, 0, true)
              checkBox(IdeBundle.message("untrusted.project.warning.trust.location.checkbox", trimmedFolderName))
                .bindSelected(trustAll)
                .apply {
                  component.toolTipText = null
                  component.addMouseMotionListener(TooltipMouseAdapter { listOf(getParentFolder()!!.pathString) })
                }
                .onChanged {
                  if (it.isSelected) {
                    windowsDefender.set(false)
                  }
                  if (trustAction != null) {
                    val trustButton = getButton(trustAction!!)
                    val text = if (it.isSelected) {
                      val truncatedParentFolderName = StringUtil.shortenTextWithEllipsis(getTrustFolder(it.isSelected).name, 18, 0, true)
                      IdeBundle.message("untrusted.project.dialog.trust.folder.button", truncatedParentFolderName)
                    }
                    else trustButtonText
                    trustButton?.text = text
                  }
                  val trimmedFolderName = StringUtil.shortenTextWithEllipsis(getTrustFolder(it.isSelected).name, 18, 0, true)
                  windowsDefenderCheckBox?.component?.text = IdeBundle.message("untrusted.project.windows.defender.trust.location.checkbox", trimmedFolderName)
                }
            }
          }
          if (pathsToExclude.isNotEmpty()) {
            row {
              val trimmedFolderName = StringUtil.shortenTextWithEllipsis(projectPath.name.ifEmpty { projectPath.toString() }, 18, 0, true)
              val idePaths = pathsToExclude.asSequence().filter { it != projectPath }.joinToString(separator = "<br>")
              windowsDefenderCheckBox = checkBox(IdeBundle.message("untrusted.project.windows.defender.trust.location.checkbox", trimmedFolderName))
                .bindSelected(windowsDefender)
                .apply {
                  component.toolTipText = null
                  component.addMouseMotionListener(TooltipMouseAdapter { listOf(idePaths, getTrustFolder(isTrustAll()).pathString) })
                  comment(IdeBundle.message("untrusted.project.location.comment"))
                }
            }
          }
        }.align(AlignX.FILL + AlignY.FILL)
      }
    }.withMinimumWidth(600).withPreferredWidth(600)
  }

  private inner class TooltipMouseAdapter(val orderedPaths: () -> List<String>) : MouseAdapter() {
    override fun mouseMoved(e: MouseEvent) {
      val checkBox = e.source as? JBCheckBox ?: return
      val position = e.point
      val textWithMarkedElements = checkBox.text.removePrefix("<html>").replace("'", "").replace("<b>", "'").replace("</b>", "'")
      val htmlDocument = (checkBox.getClientProperty("html") as? View)?.document

      val text = htmlDocument?.getText(0, htmlDocument.length)?.replace("\n", "") ?: textWithMarkedElements
      val fontMetrics = checkBox.getFontMetrics(checkBox.font)
      val bounds = fontMetrics.getStringBounds(text, checkBox.graphics)
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
          @Suppress("HardCodedStringLiteral")
          checkBox.toolTipText = paths.getOrNull(pathInd)
          return
        }
      }
      checkBox.toolTipText = null
    }
  }

  private fun getTrustFolder(isTrustAll: Boolean): Path = if (isTrustAll) getParentFolder() ?: projectPath else projectPath

  private fun getParentFolder(): Path? = projectPath.parent

  override fun createActions(): Array<out Action?> {
    val actions: MutableList<Action> = mutableListOf()
    val options: List<String> = listOf(trustButtonText, distrustButtonText, cancelButtonText)

    for (i in options.indices) {
      val option = options[i]
      val action: Action = object : AbstractAction(UIUtil.replaceMnemonicAmpersand(option)) {
        override fun actionPerformed(e: ActionEvent) {
          val exitActionType: ExitActionType
          userChoice = when (option) {
            trustButtonText -> {
              exitActionType = ExitActionType.YES
              OpenUntrustedProjectChoice.TRUST_AND_OPEN
            }
            distrustButtonText -> {
              exitActionType = ExitActionType.NO
              OpenUntrustedProjectChoice.OPEN_IN_SAFE_MODE
            }
            cancelButtonText -> {
              exitActionType = ExitActionType.CANCEL
              OpenUntrustedProjectChoice.CANCEL
            }
            else -> {
              logger<TrustedProjects>().error("Illegal choice $option")
              close(i, false)
              return
            }
          }
          close(i, userChoice == OpenUntrustedProjectChoice.TRUST_AND_OPEN, exitActionType)
        }
      }
      if (option == trustButtonText) trustAction = action
      if (i == myDefaultOptionIndex) {
        action.putValue(DEFAULT_ACTION, true)
      }
      if (i == myFocusedOptionIndex) {
        action.putValue(FOCUSED_ACTION, true)
      }
      UIUtil.assignMnemonic(option, action)
      actions.add(action)
    }
    if (helpId != null) {
      actions.add(helpAction)
    }
    return actions.toTypedArray()
  }

  override fun sortActionsOnMac(actions: MutableList<Action>) {
    actions.reverse()
  }

  override fun getHelpId(): String? = TRUSTED_PROJECTS_HELP_TOPIC

  private fun getOpenChoice(): OpenUntrustedProjectChoice = userChoice

  private fun isTrustAll(): Boolean = trustAll.get()

  private fun getDefenderTrustFolder(): Path? = if (windowsDefender.get()) getTrustFolder(isTrustAll()) else null

  private fun getDefenderExcludePaths(): List<Path> = pathsToExclude

  class DialogChoice(
    val openChoice: OpenUntrustedProjectChoice,
    val isTrustAll: Boolean,
    val defenderTrustFolder: Path?,
    val defenderExcludePaths: List<Path> ,
  )

  companion object {

    private var ourDialogChoice: DialogChoice? = null

    @TestOnly
    fun setDialogChoiceInTests(openChoice: OpenUntrustedProjectChoice, disposable: Disposable) {
      val choice = DialogChoice(openChoice, false, null, emptyList())
      setKotlinProperty(::ourDialogChoice, choice, disposable)
    }

    suspend fun showAndGet(
      project: Project?,
      projectPath: Path,
      title: @NlsContexts.DialogTitle String,
      message: @NlsContexts.DialogMessage String,
      trustButtonText: @NlsContexts.Button String,
      distrustButtonText: @NlsContexts.Button String,
      cancelButtonText: @NlsContexts.Button String,
    ): DialogChoice {
      val pathsToExclude = getDefenderExcludePaths(project, projectPath)
      return ourDialogChoice ?: withContext(Dispatchers.EDT) {
        val dialog = TrustedProjectStartupDialog(project, projectPath, pathsToExclude, title, message, trustButtonText, distrustButtonText, cancelButtonText)
        dialog.show()
        DialogChoice(dialog.getOpenChoice(), dialog.isTrustAll(), dialog.getDefenderTrustFolder(), dialog.getDefenderExcludePaths())
      }
    }

    private suspend fun getDefenderExcludePaths(project: Project?, projectPath: Path): List<Path> {
      if (SystemInfo.isWindows) {
        val checker = serviceAsync<WindowsDefenderChecker>()
        if (
          !checker.isUntrustworthyLocation(projectPath) &&
          !checker.isStatusCheckIgnored(project) &&
          checker.isRealTimeProtectionEnabled == true
        ) {
          val paths = checker.filterDevDrivePaths(checker.getPathsToExclude(project, projectPath)).toMutableList()
          if (projectPath in paths) {
            return paths
          }
        }
      }
      return emptyList()
    }
  }
}