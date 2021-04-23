// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.timeline.comment

import com.intellij.collaboration.ui.codereview.SimpleEventListener
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project

interface SubmittableTextFieldModel {
  val project: Project?

  val document: Document

  val isBusy: Boolean

  val error: Throwable?

  fun submit()

  fun addStateListener(listener: SimpleEventListener)
}