// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions

import com.intellij.codeInsight.actions.ReaderModeProvider.ReaderMode
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Use it to override file <--> reader mode matching
 */
interface ReaderModeMatcher {
  /**
   * It's triggered on Reader Mode to check if file matches mode specified.
   *
   * @return null if unable to decide
   */
  fun matches(project: Project, file: VirtualFile, editor: Editor?, mode: ReaderMode): Boolean?
}