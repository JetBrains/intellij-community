// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("DocumentationUtil")

package com.intellij.lang.documentation.ide

import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.lang.documentation.ide.impl.DocumentationBrowser
import com.intellij.lang.documentation.ide.ui.DocumentationUI
import com.intellij.lang.documentation.impl.DocumentationRequest
import com.intellij.model.Pointer
import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.EDT
import org.jetbrains.annotations.ApiStatus.Experimental
import javax.swing.JComponent

@Experimental
fun documentationComponent(
  project: Project,
  targetPointer: Pointer<out DocumentationTarget>,
  targetPresentation: TargetPresentation,
  parentDisposable: Disposable,
): JComponent {
  EDT.assertIsEdt()
  val request = DocumentationRequest(targetPointer, targetPresentation)
  return documentationComponent(project, request, parentDisposable)
}

internal fun documentationComponent(
  project: Project,
  request: DocumentationRequest,
  parentDisposable: Disposable,
): JComponent {
  val browser = DocumentationBrowser.createBrowser(project, request)
  val ui = DocumentationUI(project, browser)
  Disposer.register(parentDisposable, ui)
  return ui.scrollPane
}
