// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DocumentationUtil")

package com.intellij.lang.documentation.ide

import com.intellij.lang.documentation.ide.impl.DocumentationBrowser
import com.intellij.lang.documentation.ide.ui.DocumentationComponent
import com.intellij.lang.documentation.ide.ui.DocumentationUI
import com.intellij.model.Pointer
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.impl.DocumentationRequest
import com.intellij.platform.backend.documentation.impl.EmptyDocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.util.ui.EDT
import org.jetbrains.annotations.ApiStatus.Experimental
import javax.swing.JComponent

@Experimental
fun documentationComponent(
  project: Project,
  targetPointer: Pointer<out DocumentationTarget>,
  targetPresentation: TargetPresentation,
  parentDisposable: Disposable,
): DocumentationComponent {
  EDT.assertIsEdt()
  return createDocumentationComponent(project, DocumentationRequest(targetPointer, targetPresentation), parentDisposable)
}

internal fun documentationComponent(
  project: Project,
  request: DocumentationRequest,
  parentDisposable: Disposable,
): JComponent {
  return createDocumentationComponent(project, request, parentDisposable).getComponent()
}

private fun createDocumentationComponent(project: Project,
                                         request: DocumentationRequest,
                                         parentDisposable: Disposable): DocumentationComponent {
  val browser = DocumentationBrowser.createBrowser(project, request)
  val ui = DocumentationUI(project, browser)
  Disposer.register(parentDisposable, ui)
  return DocumentationComponentImpl(browser, ui)
}

private class DocumentationComponentImpl(private val browser: DocumentationBrowser,
                                         private val ui: DocumentationUI) : DocumentationComponent {
  override fun getComponent(): JComponent = ui.scrollPane

  override fun resetBrowser() {
    browser.resetBrowser(EmptyDocumentationTarget.request)
  }

  override fun resetBrowser(targetPointer: Pointer<out DocumentationTarget>,
                            targetPresentation: TargetPresentation) {
    browser.resetBrowser(DocumentationRequest(targetPointer, targetPresentation))
  }
}