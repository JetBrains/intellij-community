package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
abstract class WelcomeScreenRightTab(
  val project: Project,
  val contentProvider: WelcomeRightTabContentProvider,
) : Disposable {
  abstract val component: JComponent

  abstract fun getPreferredFocusedComponent(): JComponent
}