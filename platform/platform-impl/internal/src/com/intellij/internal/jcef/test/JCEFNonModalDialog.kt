// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.jcef.JBCefBrowser
import java.lang.AutoCloseable
import javax.swing.JComponent

internal class JCEFNonModalDialog(content: JComponent, dlgTitle: String, project: Project, closer: Runnable) : DialogWrapper(project, false, IdeModalityType.MODELESS) {
  private val myContent: JComponent = content
  private val myCloser: Runnable = closer

  init {
    title = dlgTitle
    init()
  }

  constructor(testBrowser: JBCefBrowser, title: String) : this(testBrowser.component, title, ProjectManager.getInstance().getDefaultProject(), {
    testBrowser.dispose()
  })

  constructor(content: JComponent, title: String) : this(content, title, ProjectManager.getInstance().getDefaultProject(), {
    if (content is AutoCloseable)
      content.close()
  })

  override fun createCenterPanel(): JComponent {
    return myContent
  }

  override fun dispose() {
    try {
      myCloser.run()
    } catch (e: Throwable) {
      e.printStackTrace()
    } finally {
      super.dispose()
    }
  }
}
