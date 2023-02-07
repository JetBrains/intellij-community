// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff.viewer

import com.intellij.collaboration.ui.codereview.diff.EditorComponentInlaysManager
import com.intellij.collaboration.ui.codereview.diff.EditorLineInlaysController
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import javax.swing.JComponent

/**
 * Subscribe to [vmsFlow] and show components created via [componentFactory] as inlays on proper lines
 *
 * @param VM - inlay viemodel
 */
fun <VM : Any> EditorEx.controlInlaysIn(
  cs: CoroutineScope,
  vmsFlow: Flow<Map<Int, List<VM>>>,
  vmKeyExtractor: (VM) -> Any,
  componentFactory: (CoroutineScope, VM) -> JComponent
) {
  val inlaysManager1 = EditorComponentInlaysManager(this as EditorImpl)
  EditorLineInlaysController(cs, vmsFlow, vmKeyExtractor, componentFactory, inlaysManager1)
}