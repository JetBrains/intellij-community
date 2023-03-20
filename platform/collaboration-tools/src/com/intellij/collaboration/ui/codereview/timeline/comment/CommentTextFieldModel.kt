// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.timeline.comment

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project

interface CommentTextFieldModel {
  val project: Project?

  /**
   * Don't change document's properties use [content] instead
   */
  val document: Document

  val content: CommentTextFieldModelContent
}

interface CommentTextFieldModelContent {
  var text: String

  var isReadOnly: Boolean

  var isAcceptSlashR: Boolean

  fun clear()
}