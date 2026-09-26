// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui.layout.impl

import com.intellij.execution.ui.layout.impl.GridImpl.Placeholder
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.SkipInHeadlessEnvironment
import com.intellij.ui.content.TabDescriptor
import com.intellij.ui.content.TabGroupId
import com.intellij.ui.content.impl.TabbedContentImpl
import org.junit.Assert
import org.junit.Test
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingUtilities

@SkipInHeadlessEnvironment
class GridPlaceholderTest {
  @Test
  fun preferredFocusComponentWhenParentInvisible() {
    SwingUtilities.invokeAndWait {
      /*
       * frame (visible==true)
       * + panel (visible==false)
       *   + text (visible==true but isShowing() == false)
       */
      val frame = JFrame()
      val panel = JPanel()
      val text = JTextField()

      frame.add(panel)
      panel.add(text)

      frame.isVisible = true
      panel.isVisible = false

      val content = TabbedContentImpl(TabGroupId("", {""}), TabDescriptor(panel, ""), true)
      content.setPreferredFocusedComponent { text }

      val placeholder = Placeholder()
      placeholder.setContentProvider{ arrayOf(content) }

      Assert.assertNull(placeholder.focusTraversalPolicy.getDefaultComponent(panel))

      Disposer.dispose(content)
      frame.dispose()
    }
  }
}
