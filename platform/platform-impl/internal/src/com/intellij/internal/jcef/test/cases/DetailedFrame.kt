// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test.cases

import com.intellij.internal.jcef.test.JBCefTestAppFrame
import com.intellij.internal.jcef.test.detailed.MainFrame
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.jcef.JBCefApp
import org.cef.CefApp
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JPanel

internal class DetailedFrame : JBCefTestAppFrame.TestCase() {
  override fun getComponent() = myComponent

  override fun getDisplayName()= "Detailed Frame"

  override fun initializeImpl() {
    myComponent.removeAll()

    JBCefApp.getInstance()

    val p = JPanel(VerticalFlowLayout(FlowLayout.LEFT))
    p.add(TestCasePanel("Open detailed frame.", "Opens detailed frame in separate JFrame.", "Open", {
      val f = MainFrame(CefApp.getInstanceIfAny())
      f.setSize(1000, 800)
      f.setVisible(true)
    }))
    myComponent.add(p)
  }

  private val myComponent = JPanel(BorderLayout())
}
