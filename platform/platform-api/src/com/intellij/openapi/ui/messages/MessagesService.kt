// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.messages

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper.DoNotAskOption
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.DetailedDescription
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.util.Function
import com.intellij.util.PairFunction
import java.awt.Component
import javax.swing.Icon
import javax.swing.JCheckBox
import javax.swing.JTextField

/**
 * Allows to replace the implementation of showing messages. If you, as a plugin developer, need to show
 * messages, please use the [com.intellij.openapi.ui.Messages] class.
 */
interface MessagesService {
  companion object {
    @JvmStatic
    fun getInstance(): MessagesService {
      ApplicationManager.getApplication()?.let {
        return it.getService(MessagesService::class.java)
      }
      return MessagesService::class.java.classLoader.loadClass("com.intellij.ui.messages.MessagesServiceImpl")
        .newInstance() as MessagesService
    }
  }

  fun showMessageDialog(project: Project?,
                        parentComponent: Component? = null,
                        message: @DialogMessage String?,
                        title: @NlsContexts.DialogTitle String?,
                        options: Array<String>,
                        defaultOptionIndex: Int = 0,
                        focusedOptionIndex: Int = -1,
                        icon: Icon?,
                        doNotAskOption: DoNotAskOption?,
                        alwaysUseIdeaUI: Boolean = false): Int

  fun showMoreInfoMessageDialog(project: Project?,
                                message: @DialogMessage String?,
                                title: @NlsContexts.DialogTitle String?,
                                moreInfo: @DetailedDescription String?,
                                options: Array<String?>?,
                                defaultOptionIndex: Int,
                                focusedOptionIndex: Int,
                                icon: Icon?): Int

  fun showTwoStepConfirmationDialog(message: @DialogMessage String?,
                                    title: @NlsContexts.DialogTitle String?,
                                    options: Array<String?>?,
                                    checkboxText: @NlsContexts.Checkbox String?,
                                    checked: Boolean,
                                    defaultOptionIndex: Int,
                                    focusedOptionIndex: Int,
                                    icon: Icon?,
                                    exitFunc: PairFunction<in Int?, in JCheckBox?, Int?>?): Int

  fun showPasswordDialog(project: Project?,
                         message: @DialogMessage String?,
                         title: @NlsContexts.DialogTitle String?,
                         icon: Icon?,
                         validator: InputValidator?): String?

  fun showPasswordDialog(parentComponent: Component,
                         message: @DialogMessage String?,
                         title: @NlsContexts.DialogTitle String?,
                         icon: Icon?,
                         validator: InputValidator?): CharArray?

  fun showInputDialog(project: Project?,
                      parentComponent: Component?,
                      message: @DialogMessage String?,
                      title: @NlsContexts.DialogTitle String?,
                      icon: Icon?,
                      initialValue: String?,
                      validator: InputValidator?,
                      selection: TextRange?,
                      comment: @DetailedDescription String?): String?

  fun showMultilineInputDialog(project: Project?,
                               message: @DialogMessage String?,
                               title: @NlsContexts.DialogTitle String?,
                               initialValue: String?,
                               icon: Icon?,
                               validator: InputValidator?): String?

  fun showInputDialogWithCheckBox(message: @DialogMessage String?,
                                  title: @NlsContexts.DialogTitle String?,
                                  checkboxText: @NlsContexts.Checkbox String?,
                                  checked: Boolean,
                                  checkboxEnabled: Boolean,
                                  icon: Icon?,
                                  initialValue: String?,
                                  validator: InputValidator?): Pair<String?, Boolean?>

  fun showEditableChooseDialog(message: @DialogMessage String?,
                               title: @NlsContexts.DialogTitle String?,
                               icon: Icon?,
                               values: Array<String?>?,
                               initialValue: String?,
                               validator: InputValidator?): String?

  fun showChooseDialog(project: Project?,
                       parentComponent: Component?,
                       message: @DialogMessage String?,
                       title: @NlsContexts.DialogTitle String?,
                       values: Array<String?>?,
                       initialValue: String?,
                       icon: Icon?): Int

  fun showTextAreaDialog(textField: JTextField?,
                         title: @NlsContexts.DialogTitle String?,
                         dimensionServiceKey: String?,
                         parser: Function<in String?, out MutableList<String?>?>?,
                         lineJoiner: Function<in MutableList<String?>?, String?>?)

}