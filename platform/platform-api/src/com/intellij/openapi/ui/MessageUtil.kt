// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("MessageUtil")
package com.intellij.openapi.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsUI
import com.intellij.openapi.util.NlsUI.Button
import com.intellij.util.nls.NlsContexts
import com.intellij.util.nls.NlsContexts.DialogMessage
import com.intellij.util.nls.NlsContexts.DialogTitle
import org.jetbrains.annotations.Nls
import javax.swing.Icon

fun showYesNoDialog(title: @Nls @DialogTitle String,
                    message: @Nls @DialogMessage String,
                    project: Project?,
                    yesText: @Nls @Button String = Messages.getYesButton(),
                    noText: @Nls @Button String = Messages.getNoButton(),
                    icon: Icon? = null): Boolean {
  return Messages.showYesNoDialog(project, message, title, yesText, noText, icon) == Messages.YES
}

fun showOkNoDialog(title: @Nls @DialogTitle String,
                   message: @Nls @DialogMessage String,
                   project: Project?,
                   okText: @Nls @Button String = Messages.getOkButton(),
                   noText: @Nls @Button String = Messages.getNoButton(),
                   icon: Icon? = null): Boolean {
  return Messages.showYesNoDialog(project, message, title, okText, noText, icon) == Messages.YES
}

@Messages.OkCancelResult
fun showOkCancelDialog(title: @Nls @DialogTitle String,
                       message: @Nls @DialogMessage String,
                       okText: @Nls @Button String,
                       cancelText: @Nls @Button String = Messages.getCancelButton(),
                       icon: Icon? = null,
                       doNotAskOption: DialogWrapper.DoNotAskOption? = null,
                       project: Project? = null): Int {
  return Messages.showOkCancelDialog(project, message, title, okText, cancelText, icon, doNotAskOption)
}