// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("MessageUtil")
package com.intellij.openapi.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.*
import javax.swing.Icon

fun showYesNoDialog(@DialogTitle title: String,
                    @DialogMessage message: String,
                    project: Project?,
                    @Button yesText: String = Messages.getYesButton(),
                    @Button noText: String = Messages.getNoButton(),
                    icon: Icon? = null): Boolean {
  return Messages.showYesNoDialog(project, message, title, yesText, noText, icon) == Messages.YES
}

fun showOkNoDialog(@DialogTitle title: String,
                   @DialogMessage message: String,
                   project: Project?,
                   @Button okText: String = Messages.getOkButton(),
                   @Button noText: String = Messages.getNoButton(),
                   icon: Icon? = null): Boolean {
  return Messages.showYesNoDialog(project, message, title, okText, noText, icon) == Messages.YES
}

@Messages.OkCancelResult
fun showOkCancelDialog(@DialogTitle title: String,
                       @DialogMessage message: String,
                       @Button okText: String,
                       @Button cancelText: String = Messages.getCancelButton(),
                       icon: Icon? = null,
                       doNotAskOption: DoNotAskOption? = null,
                       project: Project? = null): Int {
  return Messages.showOkCancelDialog(project, message, title, okText, cancelText, icon, doNotAskOption)
}
