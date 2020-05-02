// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("MessageUtil")
package com.intellij.openapi.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.Button
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.openapi.util.NlsContexts.DialogTitle
import javax.swing.Icon

fun showYesNoDialog(title: @DialogTitle String,
                    message: @DialogMessage String,
                    project: Project?,
                    yesText: @Button String = Messages.getYesButton(),
                    noText: @Button String = Messages.getNoButton(),
                    icon: Icon? = null): Boolean {
  return Messages.showYesNoDialog(project, message, title, yesText, noText, icon) == Messages.YES
}

fun showOkNoDialog(title: @DialogTitle String,
                   message: @DialogMessage String,
                   project: Project?,
                   okText: @Button String = Messages.getOkButton(),
                   noText: @Button String = Messages.getNoButton(),
                   icon: Icon? = null): Boolean {
  return Messages.showYesNoDialog(project, message, title, okText, noText, icon) == Messages.YES
}

@Messages.OkCancelResult
fun showOkCancelDialog(title: @DialogTitle String,
                       message: @DialogMessage String,
                       okText: @Button String,
                       cancelText: @Button String = Messages.getCancelButton(),
                       icon: Icon? = null,
                       doNotAskOption: DialogWrapper.DoNotAskOption? = null,
                       project: Project? = null): Int {
  return Messages.showOkCancelDialog(project, message, title, okText, cancelText, icon, doNotAskOption)
}