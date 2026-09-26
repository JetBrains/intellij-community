// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.util.setKotlinProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ExitActionType
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.plus
import com.intellij.util.ui.JBFont
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import javax.swing.Action
import javax.swing.JComponent
import kotlin.io.path.pathString

/**
 * Asks the user to trust a single file opened in the safe mode (see [com.intellij.ide.trustedProjects.TrustedFiles]).
 *
 * The dialog offers to trust the file, or the whole parent folder of the file, or to stay in the safe mode.
 * There is no cancel button: an escape or a close counts as "stay in the safe mode".
 */
internal class TrustedFileDialog(
  project: Project,
  private val filePath: Path,
) : TrustAlertDialog(project) {
  private val trustFolder = AtomicBooleanProperty(false)
  private var trusted = false
  private val myTitle: @NlsContexts.DialogTitle String = IdeBundle.message("untrusted.file.open.dialog.title", filePath.fileName)
  private val trustButtonText: @NlsContexts.Button String = IdeBundle.message("untrusted.file.dialog.trust.button")
  private val distrustButtonText: @NlsContexts.Button String = IdeBundle.message("untrusted.project.dialog.distrust.button")
  private var trustAction: Action? = null

  init {
    title = myTitle
    installAlertChrome()
  }

  override fun createCenterPanel(): JComponent {
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
            text(IdeBundle.message("untrusted.file.dialog.text", ApplicationInfoEx.getInstanceEx().fullApplicationName, filePath.toString())).apply {
              component.isFocusable = true // Workaround for IJPL-184339
            }
          }
          filePath.parent?.takeIf { TrustedProjects.isProjectLocationOfferedForTrust(filePath) }?.let { parentPath ->
            row {
              val parentDirName = NioFiles.getFileName(parentPath)
              val trimmedParentDirName = StringUtil.shortenTextWithEllipsis(parentDirName, 40, 0, true)
              val truncatedParentDirName = StringUtil.shortenTextWithEllipsis(parentDirName, 18, 0, true)
              checkBox(IdeBundle.message("untrusted.file.warning.trust.location.checkbox", trimmedParentDirName))
                .bindSelected(trustFolder)
                .apply {
                  component.toolTipText = parentPath.pathString
                }
                .onChanged {
                  val trustButton = trustAction?.let { action -> getButton(action) }
                  trustButton?.text = when {
                    it.isSelected -> IdeBundle.message("untrusted.project.dialog.trust.folder.button", truncatedParentDirName)
                    else -> trustButtonText
                  }
                }
            }
          }
        }.align(AlignX.FILL + AlignY.FILL)
      }
    }.withMinimumWidth(600).withPreferredWidth(600)
  }

  override fun createActions(): Array<out Action?> {
    val trustAction = alertAction(trustButtonText, isDefault = true) {
      trusted = true
      close(0, true, ExitActionType.YES)
    }
    this.trustAction = trustAction
    val distrustAction = alertAction(distrustButtonText, isFocused = true) {
      close(1, false, ExitActionType.NO)
    }
    return arrayOf(trustAction, distrustAction, helpAction)
  }

  class DialogChoice(
    val isTrusted: Boolean,
    val isTrustFolder: Boolean,
  )

  companion object {
    private var ourDialogChoice: DialogChoice? = null

    @TestOnly
    fun setDialogChoiceInTests(choice: DialogChoice, disposable: Disposable) {
      setKotlinProperty(::ourDialogChoice, choice, disposable)
    }

    fun showAndGet(project: Project, filePath: Path): DialogChoice {
      return ourDialogChoice ?: invokeAndWaitIfNeeded {
        val dialog = TrustedFileDialog(project, filePath)
        dialog.show()
        DialogChoice(dialog.trusted, dialog.trustFolder.get())
      }
    }
  }
}
