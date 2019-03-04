// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("MessageUtil")
package com.intellij.openapi.ui

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import javax.swing.Icon

fun showYesNoDialog(@Nls(capitalization = Nls.Capitalization.Title) title: String, message: String, project: Project?, yesText: String = Messages.YES_BUTTON, noText: String = Messages.NO_BUTTON, icon: Icon? = null): Boolean {
  return Messages.showYesNoDialog(project, message, title, yesText, noText, icon) == Messages.YES
}

fun showOkNoDialog(@Nls(capitalization = Nls.Capitalization.Title) title: String, message: String, project: Project?, yesText: String = Messages.OK_BUTTON, noText: String = Messages.NO_BUTTON, icon: Icon? = null): Boolean {
  return Messages.showYesNoDialog(project, message, title, yesText, noText, icon) == Messages.YES
}

@Messages.OkCancelResult
fun showOkCancelDialog(@Nls(capitalization = Nls.Capitalization.Title) title: String,
                       @Nls(capitalization = Nls.Capitalization.Sentence) message: String,
                       okText: String,
                       cancelText: String = Messages.CANCEL_BUTTON,
                       icon: Icon? = null,
                       doNotAskOption: DialogWrapper.DoNotAskOption? = null,
                       project: Project? = null): Int {
  return Messages.showOkCancelDialog(project, message, title, okText, cancelText, icon, doNotAskOption)
}