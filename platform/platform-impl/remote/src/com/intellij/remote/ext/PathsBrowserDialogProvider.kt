// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote.ext

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.remote.RemoteSdkAdditionalData
import org.jetbrains.annotations.ApiStatus
import java.util.function.Supplier
import javax.swing.JTextField

/**
 * Could be implemented by [com.intellij.remote.CredentialsType] to allow
 * browsing for paths fields in create remote SDK dialog.
 */
interface PathsBrowserDialogProvider {
  fun showPathsBrowserDialog(project: Project?,
                             textField: JTextField,
                             dialogTitle: @NlsContexts.DialogTitle String,
                             supplier: Supplier<out RemoteSdkAdditionalData>)

  /**
   * Same as [showPathsBrowserDialog], but narrowed to directories when [foldersOnly] is `true`.
   *
   * A caller usually knows what the field it is browsing for accepts -- an executable is a file, a search path is a
   * directory -- and an implementation that can express that should, so the same field does not accept different
   * things depending on which transport happens to browse it. The default keeps the unrestricted dialog, so an
   * implementation whose chooser cannot be narrowed needs no change.
   */
  @ApiStatus.Internal
  fun showPathsBrowserDialog(project: Project?,
                             textField: JTextField,
                             dialogTitle: @NlsContexts.DialogTitle String,
                             supplier: Supplier<out RemoteSdkAdditionalData>,
                             foldersOnly: Boolean) {
    showPathsBrowserDialog(project, textField, dialogTitle, supplier)
  }
}
