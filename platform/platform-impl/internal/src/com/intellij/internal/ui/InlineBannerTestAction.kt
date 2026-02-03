// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.InlineBanner
import com.intellij.ui.components.panels.VerticalLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * @author Alexander Lobas
 */
class InlineBannerTestAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val dialog = object : DialogWrapper(e.project) {
      init {
        setSize(640, 900)
        title = "Inline Banners"
        isAutoAdjustable = false
        init()
      }

      override fun createCenterPanel(): JComponent {
        val panel = JPanel(VerticalLayout(10))

        for (status in EditorNotificationPanel.Status.values()) {
          val banner = InlineBanner("Interactive lesson available", status).addAction("Open lesson") {
            System.out.println("Action!")
          }
          if (status === EditorNotificationPanel.Status.Warning) {
            banner.showCloseButton(false)
          }
          else {
            banner.setCloseAction(Runnable {
              System.out.println("Close!")
            })
          }
          panel.add(banner)
        }

        panel.add(InlineBanner("Interactive lesson available").setIcon(null).addAction("Open lesson") {})
        panel.add(InlineBanner("Interactive lesson available"))
        panel.add(InlineBanner("Interactive lesson available").setGearAction("Tooltip", {
          Messages.showInfoMessage("AAA", "BBB")
        }).addAction("Open lesson") {})
        panel.add(InlineBanner("Interactive lesson available").showCloseButton(false).setGearAction("Tooltip", {
          System.out.println("Gear!")
        }).addAction("Open lesson", {}).addAction("Open lesson Open lesson", {}).addAction("Open lesson Open lesson Open lesson", {}))

        val banner1 = InlineBanner("Interactive lesson available")
        for (i in 1..5) {
          banner1.addAction("Action $i") {
            System.out.println("Action $i")
          }
        }
        panel.add(banner1)

        val banner2 = InlineBanner()
        banner2.setMessage("Share your successful solutions with other students and contribute to the learning community. Click the More <icon src=\"AllIcons.Actions.More\"/> icon in the toolbar. Share your successful solutions with other students and contribute to the learning community. Share your successful solutions with other students and contribute to the learning community.")
        banner2.addAction("Enable sharing"){}
        banner2.addAction("Add to bookmarks"){}
        banner2.addAction("AAAAAAAAAA"){}
        banner2.addAction("BBBBBB"){}
        banner2.addAction("CCCCCCCCC"){}
        panel.add(banner2)

        return panel
      }
    }
    dialog.show()
  }
}