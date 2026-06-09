// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.components

import com.intellij.icons.AllIcons
import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.platform.icons.deferredIcon
import com.intellij.platform.icons.design.dp
import com.intellij.platform.icons.icon
import com.intellij.platform.icons.imageIcon
import com.intellij.platform.icons.scale.factor
import com.intellij.platform.icons.scale.fitArea
import com.intellij.platform.icons.swing.swingIcon
import com.intellij.platform.icons.swing.toNewIcon
import com.intellij.platform.icons.swing.toSwingIcon
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.delay
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.time.Duration.Companion.seconds

internal class IconsPanel : UISandboxPanel {

  override val title: String = "Icons"

  override fun createContent(disposable: Disposable): JComponent {
    val icon = imageIcon("expui/fileTypes/actionScript.svg", AllIcons::class.java.classLoader).toSwingIcon()
    return panel {
      group("Basic Icons") {
        row {
          cell(JLabel(icon))
          for (scale in listOf(
            fitArea(20.dp, 20.dp),
            fitArea(40.dp, 40.dp),
            factor(5.0),
          )) {
            cell(JLabel(icon.scaled(scale)))
          }
        }
      }
      group("Animated Icon") {
        row {
          cell(JLabel(icon {
            animation {
              frame(1000) {
                swingIcon(AllIcons.General.Add)
              }
              frame(1000) {
                swingIcon(AllIcons.General.Remove)
              }
              frame(1000) {
                swingIcon(AllIcons.General.ChevronRight)
              }
              frame(1000) {
                swingIcon(AllIcons.General.ChevronLeft)
              }
            }
          }.toSwingIcon()))
        }
      }
      group("Deferred Icon") {
        row {
          cell(JLabel(deferredIcon(AllIcons.General.GearPlain.toNewIcon()) {
            delay(5.seconds)
            AllIcons.FileTypes.Image.toNewIcon()
          }.toSwingIcon()))
        }
      }
    }
  }
}